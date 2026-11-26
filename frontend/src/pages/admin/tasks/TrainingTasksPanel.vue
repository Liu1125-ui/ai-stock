<script setup lang="ts">
import {inject,onMounted,onUnmounted,ref} from 'vue'
import {ApiError} from '../../../api/auth'
import {cancelAdminTraining,listAdminTraining,type AdminTrainingPage,type AdminTrainingTask,type TrainingCancelCommand} from '../../../api/adminTraining'
import {useAuthStore} from '../../../stores/auth'
import {taskAccessKey,taskIdentity} from './taskAccess'
import {formatTaskTime,taskStateLabel} from './taskPresentation'

const access=inject(taskAccessKey)
const auth=useAuthStore()
const result=ref<AdminTrainingPage|null>(null)
const page=ref(1)
const busy=ref(false)
const error=ref('')
const notice=ref('')
const selected=ref<AdminTrainingTask|null>(null)
const reason=ref<TrainingCancelCommand['reasonCode']>('CANCEL_NOT_NEEDED')
const pending=ref<{taskId:string;command:TrainingCancelCommand}|null>(null)
let epoch=0

async function load(target=1){
  if(busy.value)return
  const current=++epoch,identity=taskIdentity(auth.profile)
  busy.value=true
  error.value=''
  try {
    const response=await listAdminTraining(target)
    if(current!==epoch)return
    result.value=response
    page.value=target
    if(pending.value){
      const audit=response.items.find(item=>item.taskId===pending.value?.taskId)?.cancellation
      if(audit?.operationId===pending.value.command.operationId){pending.value=null;selected.value=null;notice.value='训练取消记录已确认。'}
    }
  } catch(failure){
    if(current===epoch&&!access?.reject(failure,identity)){
      if(failure instanceof ApiError&&[401,403].includes(failure.status))result.value=null
      error.value=failure instanceof Error?failure.message:'训练任务列表暂不可查。'
    }
  } finally {if(current===epoch)busy.value=false}
}

function select(task:AdminTrainingTask){
  if(pending.value&&pending.value.taskId!==task.taskId)return
  selected.value=task
  reason.value=pending.value?.command.reasonCode??'CANCEL_NOT_NEEDED'
}

async function submit(){
  if(busy.value||!selected.value||!result.value?.actionsEnabled)return
  const taskId=selected.value.taskId
  const command=pending.value?.command??{operationId:crypto.randomUUID(),expectedRevision:'0',reasonCode:reason.value}
  pending.value={taskId,command}
  const current=++epoch,identity=taskIdentity(auth.profile)
  busy.value=true
  error.value=''
  notice.value=''
  try {
    const response=await cancelAdminTraining(taskId,command)
    if(current!==epoch)return
    if(response.taskId!==taskId||response.operationId!==command.operationId||response.afterState!=='CANCELLED')
      throw new Error('取消回执暂无法确认，请刷新列表或用原操作编号重试。')
    pending.value=null
    selected.value=null
    notice.value='训练已取消，操作记录已保存。'
    busy.value=false
    await load(page.value)
  } catch(failure){
    if(current!==epoch||access?.reject(failure,identity))return
    if(failure instanceof ApiError&&[400,401,403,404,409].includes(failure.status)){
      pending.value=null
      selected.value=null
    }
    if(failure instanceof ApiError&&[401,403].includes(failure.status))result.value=null
    error.value=failure instanceof Error?failure.message:'取消结果暂无法确认。'
  } finally {if(current===epoch)busy.value=false}
}
onMounted(()=>void load())
onUnmounted(()=>{epoch++})
</script>

<template>
  <section aria-labelledby="training-task-title">
    <h2 id="training-task-title">训练任务</h2>
    <p v-if="result&&!result.actionsEnabled">管理员操作尚未启用，当前可查看状态和操作记录。</p>
    <button type="button" :disabled="busy" @click="load(page)">刷新训练任务</button>
    <p v-if="error" role="alert" class="task-error">{{error}}</p>
    <p v-if="notice" role="status">{{notice}}</p>
    <p v-if="result&&!result.items.length&&!busy">本页暂无训练任务。</p>
    <div v-if="result?.items.length" class="task-scroll" tabindex="0" role="region" aria-label="训练任务列表，可横向滚动">
      <table>
        <thead><tr><th scope="col">任务 / 配置</th><th scope="col">状态</th><th scope="col">接收时间</th>
          <th scope="col">开始 / 结束</th><th scope="col">候选模型</th><th scope="col">操作</th></tr></thead>
        <tbody><tr v-for="item in result.items" :key="item.taskId">
          <td>{{item.configurationId}}<br><small>{{item.taskId}}</small></td>
          <td>{{item.state==='QUEUED'?'排队中':taskStateLabel(item.state)}}</td>
          <td>{{formatTaskTime(item.receivedAt)}}</td>
          <td>{{formatTaskTime(item.startedAt)}}<br>{{formatTaskTime(item.finishedAt)}}</td>
          <td>{{item.modelId||'—'}}</td>
          <td>
            <details v-if="item.cancellation">
              <summary>取消记录</summary>
              <div>{{item.cancellation.reasonCode==='CANCEL_DUPLICATE_REQUEST'?'重复请求':'无需继续执行'}}</div>
              <div>操作人 {{item.cancellation.actorUserId}}</div>
              <div>时间 {{formatTaskTime(item.cancellation.cancelledAt)}}</div>
              <div>操作编号 {{item.cancellation.operationId}}</div>
            </details>
            <button v-else-if="result.actionsEnabled&&item.state==='QUEUED'" type="button"
              :disabled="busy||!!pending" @click="select(item)">取消排队</button>
          </td>
        </tr></tbody>
      </table>
    </div>
    <form v-if="selected" class="task-detail" @submit.prevent="submit">
      <h3>确认取消训练</h3>
      <p>{{selected.configurationId}} · {{selected.taskId}}</p>
      <label>取消原因<select v-model="reason" :disabled="busy||!!pending">
        <option value="CANCEL_NOT_NEEDED">无需继续执行</option>
        <option value="CANCEL_DUPLICATE_REQUEST">重复请求</option>
      </select></label>
      <p v-if="pending">原操作编号 {{pending.command.operationId}}</p>
      <div class="task-actions">
        <button type="submit" :disabled="busy">{{pending?'用原编号重试':'确认取消'}}</button>
        <button v-if="!pending" type="button" :disabled="busy" @click="selected=null">返回</button>
      </div>
    </form>
    <div class="task-actions">
      <button type="button" :disabled="busy||!!pending||page===1" @click="load(page-1)">上一页</button>
      <span>第 {{page}} 页</span>
      <button type="button" :disabled="busy||!!pending||!result?.hasMore" @click="load(page+1)">下一页</button>
    </div>
  </section>
</template>
