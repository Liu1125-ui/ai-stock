<script setup lang="ts">
import {computed,onMounted,onUnmounted,ref,watch,defineAsyncComponent} from 'vue'
import {ApiError} from '../../api/auth'
import {submitTraining,trainingHistory,trainingMetrics,trainingOptions,trainingStatus,
  type TrainingMetrics,type TrainingStatus,type TrainingSummary} from '../../api/training'
import {useAuthStore} from '../../stores/auth'

const LossChart=defineAsyncComponent(()=>import('./TrainingLossChart.vue'))
const auth=useAuthStore()
const canSubmit=computed(()=>Boolean(auth.profile&&(auth.isAdmin||auth.profile.experimentAllowed)))
const options=ref<string[]>([])
const selectedOptions=ref<string[]>([])
const records=ref<TrainingSummary[]>([])
const cursor=ref<string|null>(null)
const selected=ref('')
const tasks=ref<TrainingStatus[]>([])
const report=ref<TrainingMetrics|null>(null)
const reportTask=ref('')
type PendingSubmission={requestId:string;configurationIds:string[]}
const pending=ref<PendingSubmission|null>(null)
const loading=ref(false)
const submitting=ref(false)
const statusBusy=ref(false)
const metricsBusy=ref(false)
const error=ref('')
const statusError=ref('')
const metricsError=ref('')
let generation=0
let selection=0
let timer:number|undefined

const labels:Record<TrainingStatus['state'],string>={
  AWAITING_DISPATCH:'已接单，等待调度',DISPATCH_CONFLICT:'交接冲突',QUEUED:'排队中',RUNNING:'训练中',
  SUCCEEDED:'训练完成',FAILED:'训练失败',CANCELLED:'已取消',STATUS_UNAVAILABLE:'状态暂不可查',
}

function message(value:unknown){
  if(value instanceof ApiError&&value.status===404)return '训练入口暂不可用，或该记录已不存在。'
  return value instanceof Error?value.message:'操作暂未完成。'
}
function storageKey(){return 'aistock:training-submission:'+auth.profile?.userId}
function loadPending():PendingSubmission|null{
  try {
    const value=JSON.parse(sessionStorage.getItem(storageKey())||'null')
    if(!value||typeof value.requestId!=='string')return null
    const ids:unknown[]=Array.isArray(value.configurationIds)?value.configurationIds:[value.configurationId]
    if(ids.length>=1&&ids.length<=3&&ids.every((id):id is string=>typeof id==='string'&&/^[a-z0-9][a-z0-9-]{0,63}$/.test(id))
      &&new Set(ids).size===ids.length)return {requestId:value.requestId,configurationIds:ids}
  } catch { /* A fresh submission can still use an in-memory identifier. */ }
  return null
}
function savePending(){
  try {
    if(pending.value)sessionStorage.setItem(storageKey(),JSON.stringify(pending.value))
    else sessionStorage.removeItem(storageKey())
  } catch { /* Keep the identifier in memory when browser storage is unavailable. */ }
}

async function loadHistory(append=false){
  if(loading.value)return
  const current=generation
  loading.value=true
  error.value=''
  try {
    const data=await trainingHistory(append?cursor.value??undefined:undefined)
    if(current!==generation)return
    records.value=append?[...records.value,...data.items]:data.items
    cursor.value=data.nextCursor
  } catch(reason){if(current===generation)error.value=message(reason)}
  finally {if(current===generation)loading.value=false}
}

async function refreshStatus(){
  if(!selected.value||statusBusy.value)return
  const current=generation, version=selection, requestId=selected.value
  statusBusy.value=true
  statusError.value=''
  try {
    const data=await trainingStatus(requestId)
    if(current===generation&&version===selection)tasks.value=data
  } catch(reason){if(current===generation&&version===selection)statusError.value=message(reason)}
  finally {if(current===generation&&version===selection)statusBusy.value=false}
}

function select(id:string){
  selection++
  selected.value=id
  tasks.value=[]
  report.value=null
  reportTask.value=''
  statusBusy.value=false
  metricsBusy.value=false
  metricsError.value=''
  void refreshStatus()
}

async function submit(){
  if(submitting.value||loading.value||!canSubmit.value||(!pending.value&&!selectedOptions.value.length))return
  const current=generation
  if(!pending.value)pending.value={requestId:crypto.randomUUID(),configurationIds:[...selectedOptions.value].sort()}
  const original=pending.value
  savePending()
  submitting.value=true
  error.value=''
  try {
    const receipt=await submitTraining(original.requestId,original.configurationIds)
    if(current!==generation)return
    if(receipt.requestId!==original.requestId)throw new Error('接单回执与原请求不一致。')
    pending.value=null
    savePending()
    select(receipt.requestId)
    await loadHistory()
  } catch(reason){
    if(current!==generation)return
    if(reason instanceof ApiError&&[400,403,409].includes(reason.status)){
      pending.value=null
      savePending()
    }
    error.value=message(reason)
  } finally {if(current===generation)submitting.value=false}
}

async function viewMetrics(taskId:string){
  if(metricsBusy.value)return
  const current=generation, version=selection
  metricsBusy.value=true
  report.value=null
  reportTask.value=''
  metricsError.value=''
  try {
    const data=await trainingMetrics(selected.value,taskId)
    if(current===generation&&version===selection){report.value=data;reportTask.value=taskId}
  } catch(reason){if(current===generation&&version===selection)metricsError.value=message(reason)}
  finally {if(current===generation&&version===selection)metricsBusy.value=false}
}

watch(()=>auth.profile?.userId,async user=>{
  const current=++generation
  records.value=[]
  cursor.value=null
  options.value=[]
  selectedOptions.value=[]
  pending.value=null
  loading.value=false
  submitting.value=false
  error.value=''
  statusError.value=''
  select('')
  if(!user)return
  const recovered=loadPending()
  pending.value=recovered
  void loadHistory()
  try {
    const data=await trainingOptions()
    if(current===generation){options.value=data;selectedOptions.value=recovered?.configurationIds??data.slice(0,1)}
  } catch(reason){if(current===generation&&!error.value)error.value=message(reason)}
},{immediate:true})

onMounted(()=>{timer=window.setInterval(()=>{
  if(document.visibilityState==='visible'&&(!tasks.value.length||tasks.value.some(task=>
    ['AWAITING_DISPATCH','QUEUED','RUNNING','STATUS_UNAVAILABLE'].includes(task.state)
    ||task.state==='SUCCEEDED'&&!task.modelId)))void refreshStatus()
},5000)})
onUnmounted(()=>{generation++;if(timer!==undefined)window.clearInterval(timer)})
const configurationLabel=(id:string)=>{
  if(id==='five-day')return '五日 Transformer · 种子 42'
  if(id==='five-day-seed-43')return '五日 Transformer · 种子 43'
  return id
}
const date=(value:string)=>new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false})
const loss=(value:number)=>value.toPrecision(5)
const percent=(value:number)=>(value*100).toFixed(2)+'%'
</script>

<template>
  <main class="training-page">
    <h1>训练工作台</h1>
    <section class="training-card" aria-labelledby="training-submit">
      <h2 id="training-submit">提交训练</h2>
      <form v-if="canSubmit&&(options.length||pending)" class="training-form" @submit.prevent="submit">
        <fieldset :disabled="submitting||Boolean(pending)">
          <legend>训练配置，最多选 3 项</legend>
          <label v-for="item in options" :key="item">
            <input v-model="selectedOptions" type="checkbox" :value="item"
              :disabled="selectedOptions.length>=3&&!selectedOptions.includes(item)">
            {{configurationLabel(item)}}
          </label>
        </fieldset>
        <button type="submit" :disabled="submitting||loading||(!pending&&!selectedOptions.length)">
          {{submitting?'正在提交…':pending?'用原编号重试':'提交训练'}}
        </button>
      </form>
      <p v-else>{{canSubmit?'当前没有可提交的训练配置。':'当前账号可查看本人历史，提交训练需要实验权限。'}}</p>
      <p v-if="pending">待确认请求 {{pending.requestId}}，共 {{pending.configurationIds.length}} 项配置。</p>
      <p v-if="error" role="alert">{{error}}</p>
    </section>

    <div class="training-layout">
      <section class="training-card" aria-labelledby="training-history" :aria-busy="loading">
        <h2 id="training-history">我的训练记录</h2>
        <button type="button" :disabled="loading||submitting" @click="loadHistory()">刷新记录</button>
        <p v-if="loading" role="status">正在加载记录…</p>
        <p v-else-if="!records.length&&!error">暂无训练记录。</p>
        <ul class="request-list">
          <li v-for="item in records" :key="item.requestId">
            <button type="button" :aria-pressed="selected===item.requestId" @click="select(item.requestId)">
              {{date(item.createdAt)}} <span>{{item.requestId.slice(0,8)}}</span>
            </button>
          </li>
        </ul>
        <button v-if="cursor" type="button" :disabled="loading||submitting" @click="loadHistory(true)">加载更早记录</button>
      </section>

      <section class="training-card" aria-labelledby="training-status" :aria-busy="statusBusy||metricsBusy">
        <h2 id="training-status">任务状态</h2>
        <p v-if="!selected">选择一条训练记录查看详情。</p>
        <template v-else>
          <button type="button" :disabled="statusBusy" @click="refreshStatus">刷新状态</button>
          <p v-if="statusBusy&&!tasks.length" role="status">正在查询任务…</p>
          <p v-if="statusError" role="alert">{{statusError}}</p>
          <p v-if="metricsError" role="alert">{{metricsError}}</p>
          <article v-for="task in tasks" :key="task.taskId" class="training-task">
            <h3>{{configurationLabel(task.configurationId)}}</h3>
            <p><strong>{{labels[task.state]}}</strong><span v-if="task.modelId"> · 已登记模型版本</span></p>
            <p v-if="task.startedAt">开始于 {{date(task.startedAt)}}</p>
            <p v-if="task.finishedAt">结束于 {{date(task.finishedAt)}}</p>
            <p v-if="task.state==='SUCCEEDED'&&!task.modelId">等待模型登记</p>
            <p v-if="task.modelId">模型编号 {{task.modelId}}</p>
            <button v-if="task.state==='SUCCEEDED'" type="button" :disabled="metricsBusy"
              @click="viewMetrics(task.taskId)">查看训练指标</button>
            <div v-if="report&&reportTask===task.taskId">
              <p>{{report.trainingSamples}} 个训练窗口 · 种子 {{report.seed}} · 选中第 {{report.selectedEpoch}} 轮 · 耗时 {{report.elapsedSeconds.toFixed(1)}} 秒</p>
              <LossChart v-if="report.epochHistory.length>1" :history="report.epochHistory"/>
              <table><caption>逐轮训练指标</caption>
                <thead><tr><th scope="col">轮次</th><th scope="col">训练 MSE</th><th scope="col">验证 MAE</th></tr></thead>
                <tbody><tr v-for="row in report.epochHistory" :key="row.epoch"><td>{{row.epoch}}</td><td>{{loss(row.trainingMse)}}</td><td>{{percent(row.validation.mae)}}</td></tr></tbody>
              </table>
              <table><caption>留出样本相对 MAE</caption>
                <thead><tr><th scope="col">年份</th><th scope="col">模型</th><th scope="col">朴素基线</th></tr></thead>
                <tbody v-if="report.holdout">
                  <tr><td>{{report.holdout.from}} 至 {{report.holdout.through}}</td>
                    <td>{{percent(report.holdout.mae)}}</td><td>{{percent(report.holdout.baselineMae)}}</td></tr>
                </tbody>
                <tbody v-else-if="report.heldout2025&&report.heldout2026">
                  <tr><td>2025</td><td>{{percent(report.heldout2025.mae)}}</td><td>{{percent(report.heldout2025.baselineMae)}}</td></tr>
                  <tr><td>2026</td><td>{{percent(report.heldout2026.mae)}}</td><td>{{percent(report.heldout2026.baselineMae)}}</td></tr>
                </tbody>
              </table>
            </div>
          </article>
        </template>
      </section>
    </div>
  </main>
</template>

<style scoped>
.training-page{max-width:1100px;margin:auto;padding:clamp(16px,4vw,36px)}
.training-layout{display:grid;grid-template-columns:minmax(230px,1fr) minmax(0,2fr);gap:16px}
.training-card{min-width:0;margin-block:12px;padding:16px;border:1px solid var(--border);border-radius:8px;overflow-wrap:anywhere}
.training-form{display:flex;flex-wrap:wrap;gap:12px;align-items:end}
.training-form fieldset{min-width:0;margin:0;padding:8px 12px;border:1px solid var(--border);border-radius:6px}
.training-form label{display:flex;align-items:center;gap:8px;min-height:36px}
button{min-height:44px;padding:6px 12px;font:inherit}
.request-list{list-style:none;padding:0;display:grid;gap:8px}
.request-list button{width:100%;text-align:left}
.request-list button[aria-pressed=true]{font-weight:700}
.training-task+ .training-task{margin-top:16px;border-top:1px solid var(--border)}
table{width:100%;margin-top:16px;border-collapse:collapse;text-align:left}
caption{text-align:left;font-weight:700}
td,th{padding:6px;border-bottom:1px solid var(--border)}
@media(max-width:690px){.training-layout{grid-template-columns:minmax(0,1fr)}}
</style>
