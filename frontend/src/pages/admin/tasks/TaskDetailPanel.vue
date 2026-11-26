<script setup lang="ts">
import {ref,computed,watch,onUnmounted,inject} from 'vue'
import {useAuthStore} from '../../../stores/auth'
import {createAdminTaskAccess,taskAccessKey,taskIdentity} from './taskAccess'
import {getAdminTask} from '../../../api/adminTasks'
import type {Detail} from '../../../api/adminTasks'
import {formatTaskTime,taskStateLabel,taskTypeLabel} from './taskPresentation'
import TaskAttemptsPanel from './TaskAttemptsPanel.vue'
import TaskActionsPanel from './TaskActionsPanel.vue'
import TaskOperationHistoryPanel from './TaskOperationHistoryPanel.vue'
const props=defineProps<{taskId:string}>(),emit=defineEmits<{missing:[];changed:[]}>(),auth=useAuthStore()
const access=inject(taskAccessKey,()=>createAdminTaskAccess(()=>taskIdentity(auth.profile),()=>auth.refresh()),true)
const canRead=computed(()=>!!auth.profile?.userId&&auth.profile.role==='ADMIN'&&!access.denied.value),detail=ref<Detail|null>(null),busy=ref(false),error=ref(''),stale=ref(false)
const refreshKey=ref(0)
function changed(){refreshKey.value++;void load(true);emit('changed')}
let epoch=0
async function load(replacePending=false){if(!canRead.value||(busy.value&&!replacePending))return;const n=++epoch,identity=taskIdentity(auth.profile);busy.value=true;error.value='';try{const value=await getAdminTask(props.taskId);if(n===epoch&&canRead.value){detail.value=value;stale.value=false}}catch(e){if(access.reject(e,identity))return;if(n!==epoch)return;const failure=e as {status?:number;message?:string};if([401,403,404].includes(failure.status??0))detail.value=null;else stale.value=detail.value!==null;error.value=failure.message||'任务详情未能读取';if(failure.status===404)emit('missing')}finally{if(n===epoch)busy.value=false}}
watch([()=>props.taskId,()=>auth.profile?.userId,()=>auth.profile?.role,()=>access.denied.value],()=>{epoch++;detail.value=null;busy.value=false;error.value='';stale.value=false;if(canRead.value)void load()},{immediate:true,flush:'sync'})
onUnmounted(()=>{epoch++})
</script>
<template><section v-if="canRead" class="task-detail" aria-label="所选任务详情"><h2>任务详情</h2><button type="button" data-test="task-detail-refresh" :disabled="busy" @click="load()">{{busy?'读取中…':'刷新详情'}}</button><p v-if="error" role="alert" class="task-error">{{error}}</p><p v-if="stale" class="task-stale">详情尚未刷新，保留上次读取结果。</p><template v-if="detail"><p class="small">查询时间：{{formatTaskTime(detail.queriedAt)}}（上海时间）</p><dl><dt>任务 ID</dt><dd class="mono">{{detail.task.summary.taskId}}</dd><dt>任务类型</dt><dd>{{taskTypeLabel(detail.task.summary.taskType)}}</dd><dt>股票 / 目标日期</dt><dd>{{detail.task.summary.instrumentId}} / {{detail.task.summary.targetDate}}</dd><dt>当前状态</dt><dd>{{taskStateLabel(detail.task.summary.state)}}</dd><dt>累计尝试次数</dt><dd>{{detail.task.summary.totalAttempts}}</dd><dt>本轮尝试次数</dt><dd>{{detail.task.cycleAttempts}}</dd><dt>下次计划执行</dt><dd>{{['QUEUED','RETRY_WAIT'].includes(detail.task.summary.state)?formatTaskTime(detail.task.nextAt):'无待执行计划'}}</dd><dt>创建 / 更新时间</dt><dd>{{formatTaskTime(detail.task.summary.createdAt)}} / {{formatTaskTime(detail.task.summary.updatedAt)}}</dd><dt>结果版本引用</dt><dd class="mono">{{detail.task.resultVersionId||'暂无结果版本'}}</dd><dt>原因说明</dt><dd>{{detail.task.reason.message}}<span v-if="detail.task.reason.code">（{{detail.task.reason.code}}）</span></dd><template v-if="detail.task.predictionId"><dt>预测引用</dt><dd class="mono">{{detail.task.predictionId}}</dd><dt>验证规则</dt><dd>{{detail.task.ruleVersion}}</dd></template></dl><TaskActionsPanel :task-id="props.taskId" :task="detail.task.summary" @changed="changed"/><TaskAttemptsPanel :key="refreshKey" :task-id="props.taskId"/><TaskOperationHistoryPanel :task-id="props.taskId" :refresh-key="refreshKey"/></template></section></template>
