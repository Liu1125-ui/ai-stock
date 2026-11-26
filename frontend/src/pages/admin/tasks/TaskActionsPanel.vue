<script setup lang="ts">
import {computed,inject,nextTick,onUnmounted,ref,watch} from 'vue'
import {useAuthStore} from '../../../stores/auth'
import {ApiError} from '../../../api/auth'
import {getTaskActionOptions,submitTaskAction,getTaskActionReceipt} from '../../../api/adminTaskActions'
import type {TaskAction,ActionOptions,ActionReceipt,TaskActionCommand} from '../../../api/adminTaskActions'
import type {TaskSummary} from '../../../api/adminTasks'
import {taskIdentity,taskAccessKey,createAdminTaskAccess} from './taskAccess'
import {savePending,loadPending,clearPending,clearTaskActionPendingOwner} from './taskActionPending'
import type {PendingTaskAction} from './taskActionPending'
import {formatTaskTime,taskTypeLabel,taskStateLabel} from './taskPresentation'
const props=defineProps<{taskId:string;task?:TaskSummary}>(),emit=defineEmits<{changed:[]}>(),auth=useAuthStore()
const access=inject(taskAccessKey,()=>createAdminTaskAccess(()=>taskIdentity(auth.profile),()=>auth.refresh()),true)
const canRead=computed(()=>!!auth.profile?.userId&&auth.profile.role==='ADMIN'&&!access.denied.value)
const options=ref<ActionOptions|null>(null),pending=ref<PendingTaskAction|null>(null),receipt=ref<ActionReceipt|null>(null)
const busy=ref(false),loading=ref(false),error=ref(''),blocked=ref(false),dialog=ref(false),recovering=ref(false),action=ref<TaskAction>('RETRY'),reason=ref('')
const modal=ref<HTMLElement|null>(null),reasonSelect=ref<HTMLSelectElement|null>(null)
let epoch=0,capSequence=0,returnFocus:HTMLElement|null=null
const reasons=computed(()=>action.value==='RETRY'?[{value:'RETRY_TRANSIENT_FAILURE',label:'临时故障后重新尝试'},{value:'RETRY_DEPENDENCY_RECOVERED',label:'依赖服务已恢复'}]:[{value:'CANCEL_NOT_NEEDED',label:'当前任务不再需要'},{value:'CANCEL_DUPLICATE_REQUEST',label:'确认不需要本次执行'}])
const canNew=computed(()=>canRead.value&&!busy.value&&!loading.value&&!pending.value&&!blocked.value)
function active(n:number,identity:string){return n===epoch&&canRead.value&&identity===taskIdentity(auth.profile)}
function failAccess(e:unknown,identity:string){if(access.reject(e,identity))return true;if((e as {status?:number})?.status===401){access.denied.value=true;return true}return false}
async function refreshOptions(){if(!canRead.value)return;const n=epoch,seq=++capSequence,identity=taskIdentity(auth.profile);loading.value=true
 try{const value=await getTaskActionOptions(props.taskId);if(active(n,identity)&&seq===capSequence)options.value=value}
 catch(e){if(failAccess(e,identity))return;if(active(n,identity)&&seq===capSequence){options.value=null;error.value=(e as Error).message||'操作条件暂不可用'}}
 finally{if(n===epoch&&seq===capSequence)loading.value=false}}
function open(which:TaskAction,recover=false){
 if(!canRead.value||busy.value||blocked.value)return
 if(recover){if(!pending.value||!options.value?.actionsEnabled)return;action.value=pending.value.action;reason.value=pending.value.reasonCode}
 else{if(!canNew.value||!options.value||(which==='RETRY'?!options.value.retry.allowed:!options.value.cancel.allowed))return;action.value=which;reason.value=''}
 returnFocus=document.activeElement instanceof HTMLElement?document.activeElement:null;recovering.value=recover;dialog.value=true;error.value='';void nextTick(()=>{if(reasonSelect.value&&!reasonSelect.value.disabled)reasonSelect.value.focus();else modal.value?.querySelector<HTMLElement>('button:not(:disabled)')?.focus()})
}
function close(){if(busy.value)return;dialog.value=false;void nextTick(()=>{if(returnFocus?.isConnected)returnFocus.focus()})}
function trap(event:KeyboardEvent){if(event.key==='Escape'){event.preventDefault();close();return}if(event.key!=='Tab')return
 const controls=Array.from(modal.value?.querySelectorAll<HTMLElement>('button:not(:disabled),select:not(:disabled),[tabindex="0"]')??[])
 if(!controls.length){event.preventDefault();modal.value?.focus();return}const first=controls[0]!,last=controls[controls.length-1]!
 if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}
function complete(value:ActionReceipt,owner:string){receipt.value=value;clearPending(owner,props.taskId);pending.value=null;error.value='';emit('changed');void refreshOptions()}
async function send(){
 if(!dialog.value||busy.value||!canRead.value||!reason.value||!options.value?.actionsEnabled)return
 const owner=auth.profile!.userId,identity=taskIdentity(auth.profile),n=epoch
 let marker:PendingTaskAction
 if(recovering.value){if(!pending.value)return;marker=pending.value}
 else{marker={taskId:props.taskId,operationId:crypto.randomUUID(),action:action.value,expectedRevision:options.value.revision,reasonCode:reason.value,createdAt:new Date().toISOString()};try{savePending(owner,marker)}catch(e){error.value=(e as Error).message;return}pending.value=marker}
 const input:TaskActionCommand={operationId:marker.operationId,expectedRevision:marker.expectedRevision,reasonCode:marker.reasonCode}
 busy.value=true;error.value=''
 try{const value=await submitTaskAction(marker.taskId,marker.action,input);if(active(n,identity))complete(value,owner)}
 catch(e){if(failAccess(e,identity))return;if(!active(n,identity))return
  if(e instanceof ApiError&&e.status===409&&e.data){complete(e.data as ActionReceipt,owner)}
  else error.value=(e as Error).message||'操作结果尚未确认，请查询原操作编号'}
 finally{if(n===epoch){busy.value=false;dialog.value=false;void nextTick(()=>{if(returnFocus?.isConnected)returnFocus.focus()})}}
}
async function queryReceipt(){if(!pending.value||busy.value||!canRead.value)return;const marker=pending.value,n=epoch,owner=auth.profile!.userId,identity=taskIdentity(auth.profile);busy.value=true;error.value=''
 try{const value=await getTaskActionReceipt(marker.taskId,marker.operationId);if(active(n,identity))complete(value,owner)}
 catch(e){if(failAccess(e,identity))return;if(active(n,identity))error.value=(e as {status?:number}).status===404?'尚未找到原操作记录，这并不代表未执行。可再次查询或确认恢复原请求。':(e as Error).message||'尚未确认原操作结果'}
 finally{if(n===epoch)busy.value=false}}
watch([()=>props.taskId,()=>auth.profile?.userId,()=>auth.profile?.role,()=>access.denied.value],(_next,previous)=>{
 if(previous?.[1]&&(!auth.profile||previous[1]!==auth.profile.userId||auth.profile.role!=='ADMIN'||access.denied.value))clearTaskActionPendingOwner(String(previous[1]))
 epoch++;capSequence++;options.value=null;pending.value=null;receipt.value=null;busy.value=false;loading.value=false;dialog.value=false;blocked.value=false;error.value=''
 if(canRead.value){try{pending.value=loadPending(auth.profile!.userId,props.taskId)}catch(e){blocked.value=true;error.value=(e as Error).message}void refreshOptions();if(pending.value)void queryReceipt()}
},{immediate:true,flush:'sync'})
onUnmounted(()=>{epoch++;capSequence++})
</script>
<template><section v-if="canRead" class="task-command" aria-label="管理员任务操作"><div :inert="dialog||undefined"><h3>管理员操作</h3><p class="hint">查看不会启动任务。提交前请核对任务和原因；重新排队不代表执行成功。</p>
<p v-if="error" role="alert" class="task-error">{{error}}</p><button type="button" data-test="action-options-refresh" :disabled="busy||loading" @click="refreshOptions">{{loading?'检查条件中…':'刷新操作条件'}}</button>
<div class="task-actions"><div><button type="button" data-test="task-retry" :disabled="!canNew||!options?.retry.allowed" @click="open('RETRY')">重新排队</button><p class="small">{{options?.retry.message||'操作条件暂不可用'}}</p></div><div><button type="button" data-test="task-cancel" :disabled="!canNew||!options?.cancel.allowed" @click="open('CANCEL')">取消当前任务</button><p class="small">{{options?.cancel.message||'操作条件暂不可用'}}</p></div></div>
<div v-if="pending" class="task-stale" role="status"><p>操作结果尚未确认。请先查询原操作结果，不要重新创建操作。</p><p class="mono">原操作编号：{{pending.operationId}}</p><div class="task-actions"><button type="button" data-testid="query-task-operation" :disabled="busy" @click="queryReceipt">{{busy?'确认中…':'查询原操作结果'}}</button><button type="button" data-test="recover-task-operation" :disabled="busy||loading||!options?.actionsEnabled" @click="open(pending.action,true)">恢复原请求</button></div></div>
<div v-if="receipt" role="status"><p>{{receipt.message}}</p><p>操作回执：{{receipt.outcome==='APPLIED'?'已生效':'未生效'}} · {{formatTaskTime(receipt.finishedAt)}}（上海时间）</p><p class="small">这里保留提交时的结果；当前执行状态请查看刷新后的任务详情。</p></div></div>
<div v-if="dialog" class="task-modal-backdrop"><section ref="modal" role="dialog" aria-modal="true" aria-labelledby="task-action-title" aria-describedby="task-action-impact" tabindex="-1" class="task-modal" @keydown="trap"><h3 id="task-action-title">{{recovering?'恢复原请求':action==='RETRY'?'确认重新排队':'确认取消当前任务'}}</h3><p class="mono">任务：{{props.taskId}}</p><p v-if="props.task">{{taskTypeLabel(props.task.taskType)}} · {{props.task.instrumentId}} · {{props.task.targetDate}}<br/>页面读取状态：{{taskStateLabel(props.task.state)}}</p><p id="task-action-impact">{{action==='RETRY'?'只会重新排队，可能继续访问行情服务，不能保证成功。':'只取消当前未执行任务，不撤销已有结果，不关闭后续自动计划。'}}</p><p v-if="recovering" class="task-stale">若原请求已完成，将返回原结果；若尚未登记，可能现在执行。本次沿用原编号、版本和原因。</p><form data-test="action-confirm" @submit.prevent="send"><label>操作原因<select ref="reasonSelect" v-model="reason" data-test="action-reason" :disabled="busy||recovering" required><option value="" disabled>请选择原因</option><option v-for="item in reasons" :key="item.value" :value="item.value">{{item.label}}</option></select></label><p v-if="error" role="alert">{{error}}</p><div class="task-actions"><button type="button" data-test="action-dismiss" :disabled="busy" @click="close">返回</button><button type="submit" data-test="action-submit" :disabled="busy||!reason">{{busy?'提交中…':'确认提交'}}</button></div></form></section></div></section></template>
<style scoped>
.task-command{margin-top:24px;padding-top:20px;border-top:1px solid var(--border)}.task-command p{overflow-wrap:anywhere}.task-modal-backdrop{position:fixed;inset:0;z-index:2000;display:flex;align-items:center;justify-content:center;padding:16px;background:rgb(15 23 42 / 45%)}.task-modal{background:var(--surface);color:var(--ink);border:1px solid var(--border);border-radius:12px;padding:24px;width:min(100%,560px);max-height:calc(100dvh - 32px);overflow-y:auto;box-shadow:0 18px 60px rgb(0 0 0 / 20%)}.task-modal label{display:flex;flex-direction:column;gap:8px}.task-modal select{font:inherit;min-height:44px;padding:8px;max-width:100%}.task-modal button{min-height:44px}.task-modal :focus-visible{outline:3px solid var(--accent);outline-offset:2px}
</style>
