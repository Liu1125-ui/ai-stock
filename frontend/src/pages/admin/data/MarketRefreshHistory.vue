<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {ApiError} from '../../../api/auth'
import {getRefreshHistory,type RefreshHistory,type RefreshOperation} from '../../../api/adminMarketRefresh'
import {label,time} from './marketDataPresentation'
const props=defineProps<{instrumentId:string;refreshEpoch:number}>()
const emit=defineEmits<{accessDenied:[error:unknown]}>()
const open=ref(false),busy=ref(false),error=ref(''),data=ref<RefreshHistory|null>(null),items=ref<RefreshOperation[]>([])
let epoch=0,alive=true
async function load(more=false){if(busy.value||!open.value)return;const e=epoch,stock=props.instrumentId,cursor=more?data.value?.nextCursor||undefined:undefined;busy.value=true;error.value='';try{const v=await getRefreshHistory(stock,cursor,20);if(alive&&e===epoch){data.value=v;items.value=more?[...items.value,...v.items]:v.items}}catch(err){if(alive&&e===epoch){if(err instanceof ApiError&&(err.status===401||err.status===403&&err.code==='ACCESS_DENIED')){epoch++;open.value=false;data.value=null;items.value=[];busy.value=false;error.value='';emit('accessDenied',err);return}error.value=(err as Error).message||'查询暂不可用'}}finally{if(e===epoch)busy.value=false}}
function toggle(){open.value=!open.value;if(open.value&&!data.value)void load()}
watch(()=>props.instrumentId,()=>{epoch++;open.value=false;busy.value=false;data.value=null;items.value=[];error.value=''})
watch(()=>props.refreshEpoch,()=>{if(open.value)void load()})
onUnmounted(()=>{alive=false;epoch++})
</script>
<template><section aria-label="行情重取操作历史"><button type="button" data-test="open-refresh-history" :aria-expanded="open" @click="toggle">{{open?'收起':'查看'}}重取操作历史</button><div v-if="open" :aria-busy="busy"><p>历史记录不发起取数；原回执与当前任务状态分别展示。</p><button type="button" data-test="refresh-refresh-history" :disabled="busy" @click="load()">刷新操作历史</button><p v-if="busy" role="status">正在查询操作记录…</p><p v-if="error" role="alert">{{error}}<span v-if="data"> · 保留原查询记录及时间。</span></p><p v-if="data" class="md-muted">查询时间：{{data.queriedAt}}</p><div v-if="items.length" class="md-table" role="region" aria-label="行情重取历史" tabindex="0"><table><thead><tr><th>登记时间及操作编号</th><th>原始回执</th><th>冻结范围</th><th>任务当前状态</th></tr></thead><tbody><tr v-for="item in items" :key="item.receipt.operationId"><td>{{time(item.receipt.requestedAt)}}<br><code>{{item.receipt.operationId}}</code></td><td>{{label(item.receipt.outcome)}}<br>{{item.receipt.resultCode}}</td><td>{{item.receipt.coverageStart||'未登记'}} ～ {{item.receipt.targetDate}}<br>基准版本：{{item.receipt.baseVersionId}}</td><td><template v-if="item.task"><RouterLink :to="{path:'/admin/tasks',query:{taskId:item.task.taskId}}">{{label(item.task.state)}}</RouterLink><p v-if="item.task.state==='SUCCEEDED'">版本：{{item.task.versionId||'待核对'}}<br>{{item.task.versionId===item.receipt.baseVersionId?'内容一致，复用原版本':'实际产物请以任务详情为准'}}</p></template><span v-else>无执行任务</span></td></tr></tbody></table></div><p v-else-if="!busy&&!error">暂无重取操作记录。</p><button v-if="data?.hasMore" type="button" data-test="more-refresh-history" :disabled="busy" @click="load(true)">查看更多操作</button></div></section></template>
