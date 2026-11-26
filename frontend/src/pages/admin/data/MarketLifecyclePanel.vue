<script setup lang="ts">
import {computed,onUnmounted,ref} from 'vue'
import {ApiError} from '../../../api/auth'
import {getLifecycleHistory,getLifecycleStatus,getLifecycleOperation,submitLifecycleAction,type LifecycleHistory,type LifecycleStatus,type LifecycleCommand,type LifecycleOperation} from '../../../api/marketVersionLifecycle'
import {time} from './marketDataPresentation'

const props=defineProps<{instrumentId:string;versionId:string;actorUserId?:string}>()
const emit=defineEmits<{accessDenied:[error:unknown]}>()
const status=ref<LifecycleStatus|null>(null)
const history=ref<LifecycleHistory|null>(null)
const busy=ref(false)
const statusError=ref(false)
const historyError=ref(false)
const revoked=ref(false)
let alive=true
onUnmounted(()=>{alive=false})

const useLabels={ENABLED:'允许新任务使用',DRAINING:'等待在途任务完成',DISABLED:'已停用'}
const operationLabels={WAITING:'等待在途任务完成',COMPLETED:'已完成',REJECTED:'已拒绝',SUPERSEDED:'已被后续操作替代'}

const action=computed(()=>{
  const value=status.value
  if(!value?.actionsReady||statusError.value||!props.actorUserId)return null
  if(value.useState==='ENABLED')return value.currentHead?null:'DISABLE'
  return 'RESTORE'
})
const draft=ref<{action:LifecycleCommand['action'];revision:string}|null>(null)
const reason=ref('')
const actionMessage=ref('')
const receipt=ref<LifecycleOperation|null>(null)
const key='aistock:market-lifecycle:'+props.actorUserId+':'+props.instrumentId+':'+props.versionId
const pending=ref<LifecycleCommand|null>(readPending())
const canResend=ref(false)

function readPending():LifecycleCommand|null {
  if(!props.actorUserId)return null
  try {
    const command=JSON.parse(sessionStorage.getItem(key)||'null') as LifecycleCommand|null
    if(!command||typeof command.operationId!=='string'||typeof command.reason!=='string'||
       typeof command.expectedRevision!=='string'||!['DISABLE','RESTORE'].includes(command.action))return null
    return command
  } catch {return null}
}

function beginAction() {
  if(!status.value||!action.value||pending.value||busy.value)return
  draft.value={action:action.value,revision:status.value.revision}
  reason.value=''
  actionMessage.value=''
}

async function confirmAction() {
  const current=draft.value
  if(!current||pending.value||busy.value||current.action!==action.value||current.revision!==status.value?.revision)return
  const text=reason.value.trim()
  if(!text||[...text].length>300||/[\u0000-\u001f\u007f-\u009f]/.test(reason.value)) {
    actionMessage.value='请填写 1 至 300 个字符的原因，不含换行或控制字符。'
    return
  }
  try {
    const command={operationId:crypto.randomUUID(),action:current.action,expectedRevision:current.revision,reason:text}
    sessionStorage.setItem(key,JSON.stringify(command))
    pending.value=command
  } catch {
    actionMessage.value='无法保存原操作编号，请检查浏览器设置。'
    return
  }
  draft.value=null
  await runAction(true)
}

async function runAction(write:boolean) {
  const command=pending.value
  if(!command||busy.value||revoked.value)return
  busy.value=true
  canResend.value=false
  actionMessage.value=''
  try {
    const result=write
      ?await submitLifecycleAction(props.instrumentId,props.versionId,command)
      :await getLifecycleOperation(props.instrumentId,props.versionId,command.operationId)
    if(!alive)return
    if(result.action!==command.action||result.expectedRevision!==command.expectedRevision||result.reason!==command.reason) {
      actionMessage.value='回执与原请求不一致，请保留原操作编号核对。'
      return
    }
    receipt.value=result
    pending.value=null
    try {sessionStorage.removeItem(key)} catch {/* The stored number remains safe to look up. */}
    busy.value=false
    await refresh()
  } catch(error) {
    if(!alive||deny(error))return
    if(!write&&error instanceof ApiError&&error.status===404)canResend.value=true
    if(write&&error instanceof ApiError&&error.code==='MARKET_LIFECYCLE_NOT_READY') {
      try {sessionStorage.removeItem(key);pending.value=null} catch {/* Keep the original number available. */}
      actionMessage.value='版本操作暂未开放。'
    } else actionMessage.value='暂无法确认结果，请查询原操作编号。'
  } finally {
    if(alive)busy.value=false
  }
}

function deny(error:unknown) {
  if(!(error instanceof ApiError)||(error.status!==401&&error.status!==403))return false
  revoked.value=true
  receipt.value=null
  draft.value=null
  status.value=null
  history.value=null
  emit('accessDenied',error)
  return true
}

async function refresh() {
  if(busy.value||revoked.value)return
  busy.value=true
  statusError.value=false
  historyError.value=false
  try {
    const [state,records]=await Promise.allSettled([
      getLifecycleStatus(props.instrumentId,props.versionId),
      getLifecycleHistory(props.instrumentId,props.versionId,null),
    ])
    if(!alive)return
    if(state.status==='rejected'&&deny(state.reason)||records.status==='rejected'&&deny(records.reason))return
    if(state.status==='fulfilled')status.value=state.value
    else statusError.value=true
    if(records.status==='fulfilled') {
      history.value=records.value
      const latest=records.value.items.find(item=>item.operationId===receipt.value?.operationId)
      if(latest)receipt.value=latest
    }
    else historyError.value=true
  } finally {
    if(alive)busy.value=false
  }
}

async function nextPage() {
  const cursor=history.value?.nextCursor
  if(busy.value||revoked.value||!cursor)return
  busy.value=true
  historyError.value=false
  try {
    const result=await getLifecycleHistory(props.instrumentId,props.versionId,cursor)
    if(alive)history.value=result
  } catch(error) {
    if(alive&&!deny(error))historyError.value=true
  } finally {
    if(alive)busy.value=false
  }
}
</script>

<template>
  <section class="lifecycle-panel" aria-label="行情版本使用状态" :aria-busy="busy">
    <h3>行情版本使用状态</h3>
    <p>这里显示该版本能否供新任务使用，以及已绑定该版本的在途使用数。</p>
    <button type="button" :disabled="busy||revoked" @click="refresh">查询状态与操作历史</button>
    <p v-if="busy" role="status">正在查询版本使用信息…</p>
    <p v-if="statusError" role="alert">版本状态查询失败。{{status?'仍显示上次查询结果。':''}}</p>
    <dl v-if="status">
      <dt>使用状态</dt><dd>{{useLabels[status.useState]}}</dd>
      <dt>在途使用数</dt><dd>{{status.activeUses}}</dd>
      <dt>当前行情版本</dt><dd>{{status.currentHead?'是':'否'}}</dd>
      <dt>使用状态修订号</dt><dd>{{status.revision}}</dd>
      <dt>版本操作</dt><dd>{{status.actionsReady?'已就绪':'暂未开放'}}</dd>
    </dl>
    <p v-if="status?.currentHead">当前行情版本不能停用。</p>
    <button v-if="action&&!draft&&!pending" type="button" :disabled="busy" @click="beginAction">
      {{action==='DISABLE'?'停用此版本':'恢复使用'}}
    </button>
    <form v-if="draft" class="md-form" @submit.prevent="confirmAction">
      <p>{{draft.action==='DISABLE'?'停用后阻止新任务使用，已绑定任务可继续完成。':'恢复后允许新任务再次使用此版本。'}}</p>
      <label>操作原因<input v-model="reason" required maxlength="600"></label>
      <button type="submit" :disabled="busy">确认{{draft.action==='DISABLE'?'停用':'恢复'}}</button>
      <button type="button" @click="draft=null">取消</button>
    </form>
    <div v-if="pending">
      <p>原操作编号 {{pending.operationId}}</p>
      <button type="button" :disabled="busy||revoked" @click="runAction(false)">查询原操作</button>
      <button v-if="canResend" type="button" :disabled="busy||revoked" @click="runAction(true)">用原编号重新提交</button>
    </div>
    <p v-if="receipt" role="status">操作结果 {{operationLabels[receipt.state]}}<span v-if="receipt.errorCode"> · {{receipt.errorCode}}</span></p>
    <p v-if="actionMessage" role="alert">{{actionMessage}}</p>
    <h4>操作历史</h4>
    <p v-if="historyError" role="alert">历史查询失败。{{history?'仍显示已读记录。':''}}</p>
    <template v-if="history">
      <p v-if="!history.items.length">暂无操作记录。</p>
      <ul v-else>
        <li v-for="item in history.items" :key="item.operationId">
          <strong>{{item.action==='DISABLE'?'停用':'恢复使用'}} · {{operationLabels[item.state]}}</strong>
          <p>{{time(item.createdAt)}} · {{item.reason}}</p>
          <p v-if="item.errorCode">原因编号 {{item.errorCode}}</p>
        </li>
      </ul>
      <button v-if="history.nextCursor" type="button" :disabled="busy||revoked" @click="nextPage">查看更早记录</button>
    </template>
  </section>
</template>

<style scoped>
.lifecycle-panel {border:1px solid var(--border);border-radius:8px;padding:16px;margin:16px 0;overflow-wrap:anywhere}
.lifecycle-panel button:focus-visible {outline:3px solid var(--accent);outline-offset:3px}
.lifecycle-panel li {margin-block:12px}
.lifecycle-panel li p {margin-block:4px}
</style>
