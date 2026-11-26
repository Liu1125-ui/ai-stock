<script setup lang="ts">
import {computed,inject,onUnmounted,ref,watch} from 'vue'
import {useAuthStore} from '../../../stores/auth'
import {listTaskOperations} from '../../../api/adminTaskActions'
import type {OperationPage} from '../../../api/adminTaskActions'
import {taskAccessKey,taskIdentity,createAdminTaskAccess} from './taskAccess'
import {formatTaskTime,taskStateLabel} from './taskPresentation'
const props=defineProps<{taskId:string;refreshKey:number}>(),auth=useAuthStore()
const access=inject(taskAccessKey,()=>createAdminTaskAccess(()=>taskIdentity(auth.profile),()=>auth.refresh()),true)
const canRead=computed(()=>!!auth.profile?.userId&&auth.profile.role==='ADMIN'&&!access.denied.value)
const result=ref<OperationPage|null>(null),busy=ref(false),error=ref(''),stale=ref(false),pageIndex=ref(0),cursors=ref<(string|undefined)[]>([undefined])
let epoch=0
async function load(index=0,refresh=false){if(!canRead.value||busy.value)return;const n=++epoch,identity=taskIdentity(auth.profile);busy.value=true;error.value='';if(refresh)index=0
 try{const value=await listTaskOperations(props.taskId,20,refresh?undefined:cursors.value[index]);if(n!==epoch||!canRead.value)return;result.value=value;pageIndex.value=index;cursors.value=refresh?[undefined]:cursors.value.slice(0,index+1);if(value.nextCursor)cursors.value[index+1]=value.nextCursor;stale.value=false}
 catch(e){if(access.reject(e,identity))return;if(n!==epoch)return;const status=(e as {status?:number}).status;if(status===401){access.denied.value=true;return}if(status===404)result.value=null;else stale.value=result.value!==null;error.value=(e as Error).message||'操作历史读取未完成'}
 finally{if(n===epoch)busy.value=false}}
watch([()=>props.taskId,()=>auth.profile?.userId,()=>auth.profile?.role,()=>access.denied.value],()=>{epoch++;result.value=null;busy.value=false;error.value='';stale.value=false;pageIndex.value=0;cursors.value=[undefined];if(canRead.value)void load()},{immediate:true,flush:'sync'})
watch(()=>props.refreshKey,()=>{if(canRead.value){epoch++;busy.value=false;void load(0,true)}})
onUnmounted(()=>{epoch++})
</script>
<template><section v-if="canRead" class="task-operation-history" aria-label="管理员操作历史"><h3>管理员操作记录</h3><button type="button" data-test="operation-refresh" :disabled="busy" @click="load(0,true)">刷新操作记录</button><p v-if="error" role="alert" class="task-error">{{error}}</p><p v-if="stale" role="status" class="task-stale">操作记录尚未刷新，保留上次读取结果。</p><p v-if="result" class="small">查询时间：{{formatTaskTime(result.queriedAt)}}（上海时间）</p><div v-if="result?.items.length" class="task-scroll" role="region" aria-label="管理员操作记录，可横向滚动" tabindex="0"><table><caption class="sr-only">当前任务的管理员操作记录</caption><thead><tr><th scope="col">时间 / 编号</th><th scope="col">管理员</th><th scope="col">操作 / 原因</th><th scope="col">处理结果</th><th scope="col">提交时状态</th></tr></thead><tbody><tr v-for="item in result.items" :key="item.receipt.operationId"><td>{{formatTaskTime(item.receipt.requestedAt)}}<br/><span class="mono">{{item.receipt.operationId}}</span></td><td class="mono">{{item.actorUserId}}</td><td>{{item.receipt.action==='RETRY'?'重新排队':'取消'}}<br/>{{item.reasonCode==='RETRY_TRANSIENT_FAILURE'?'临时故障后重新尝试':item.reasonCode==='RETRY_DEPENDENCY_RECOVERED'?'依赖服务已恢复':item.reasonCode==='CANCEL_NOT_NEEDED'?'当前任务不再需要':'确认不需要本次执行'}}</td><td>{{item.receipt.outcome==='APPLIED'?'已生效':'未生效'}}<br/>{{item.receipt.message}}</td><td>{{taskStateLabel(item.receipt.beforeState)}} → {{taskStateLabel(item.receipt.afterState)}}<br/>版本 {{item.receipt.beforeRevision}} → {{item.receipt.afterRevision}}</td></tr></tbody></table></div><p v-else-if="result&&!busy" role="status">暂无管理员操作记录。</p><div class="task-actions"><button type="button" data-test="operation-prev" :disabled="busy||pageIndex===0" @click="load(pageIndex-1)">上一页操作</button><span>第 {{pageIndex+1}} 页</span><button type="button" data-test="operation-next" :disabled="busy||!result?.hasMore" @click="load(pageIndex+1)">下一页操作</button></div></section></template>
