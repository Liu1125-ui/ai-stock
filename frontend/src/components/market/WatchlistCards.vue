<script setup lang="ts">
import PageState from '../PageState.vue'
import PilotLiveQuote from './PilotLiveQuote.vue'
import {stockRoute} from '../../router/listContext'
import {quoteAvailability,usePredictionCatalog} from './predictionCatalog'
import type {WatchlistItem} from '../../api/watchlist'
import type {Summary} from '../../api/market'
import {formatDecimal} from './format'
import {onBeforeUnmount,ref,watch} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {latestStockPredictions,type LatestStockPrediction} from '../../api/predictions'
const props=defineProps<{items:WatchlistItem[];summaries:Record<string,Summary>;marketError?:string;busyId?:string;returnTo?:string;listLoading?:boolean;listLoaded?:boolean}>()
defineEmits<{remove:[id:string]}>()
const catalog=usePredictionCatalog()
const auth=useAuthStore()
const recent=ref<Record<string,LatestStockPrediction>>({})
const recentState=ref<'ready'|'loading'|'error'>('ready')
const stateLabel={RUNNING:'处理中',SUCCEEDED:'已生成',FAILED:'未完成'}
const shanghaiDate=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',dateStyle:'medium'})
let generation=0

async function loadRecent() {
 const turn=++generation
 const ids=[...new Set(props.items.map(item=>item.instrumentId).filter(id=>id!=='HK00700'))]
 const owner=auth.profile?.userId
 recent.value={}
 recentState.value='ready'
 if(!owner||!ids.length)return
 recentState.value='loading'
 try {
  const rows=await latestStockPredictions(ids)
  if(turn!==generation||auth.profile?.userId!==owner)return
  recent.value=Object.fromEntries(rows.filter(row=>ids.includes(row.instrumentId)).map(row=>[row.instrumentId,row]))
  recentState.value='ready'
 } catch {
  if(turn===generation&&auth.profile?.userId===owner)recentState.value='error'
 }
}

function changeLabel(value?:string|null) {
 if(value==null)return '暂无涨跌幅'
 const sign=Number(value)
 return sign===0?'持平 0.00%':(sign>0?'上涨 ':'下跌 ')+formatDecimal(value.replace(/^-/,'') ,4)+'%'
}
watch([()=>auth.profile?.userId,()=>props.items.map(item=>item.instrumentId).join(',')],loadRecent,{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{generation++})

</script>
<template><div v-if="items.length" class="watch-cards"><article v-for="item in items" :key="item.instrumentId" class="watch-card"><div><p class="stock-code">{{item.instrumentId}}</p><p class="stock-name"><span v-if="summaries[item.instrumentId]?.displayName">{{summaries[item.instrumentId]?.displayName}} · </span>{{summaries[item.instrumentId]?.displayNameVerified && summaries[item.instrumentId]?.displayName?'名称已核验':'名称待核验'}}</p><p class="stock-date">{{summaries[item.instrumentId]?.state==='AVAILABLE'?`日线截至 ${summaries[item.instrumentId]?.lastTradeDate}`:'暂无已发布行情'}}</p><p class="stock-date">{{quoteAvailability(summaries[item.instrumentId])}}</p></div><PilotLiveQuote v-if="item.instrumentId==='HK00700'"/><div v-else class="watch-price"><strong v-if="summaries[item.instrumentId]?.close">{{formatDecimal(summaries[item.instrumentId]?.close??null,2)}}</strong><span v-else>—</span><small>未复权收盘价</small><small>{{changeLabel(summaries[item.instrumentId]?.dailyChangePercent)}}</small></div><div class="watch-actions"><router-link class="action-link" :to="stockRoute('/market/'+item.instrumentId,returnTo)">查看分析</router-link><span v-if="item.instrumentId!=='HK00700'" class="prediction-entry"><router-link v-if="catalog.canSubmit(item.instrumentId,summaries[item.instrumentId])" class="action-link" :to="stockRoute('/market/'+item.instrumentId+'/predict',returnTo)">发起预测</router-link><el-button v-else text disabled :title="catalog.reason(item.instrumentId,summaries[item.instrumentId])">发起预测</el-button><small role="status">{{catalog.reason(item.instrumentId,summaries[item.instrumentId])}}</small></span><router-link v-if="item.instrumentId!=='HK00700'" class="action-link" :to="stockRoute('/alerts',returnTo,{instrumentId:item.instrumentId})">设置预警</router-link><el-button text type="danger" :loading="busyId===item.instrumentId" :disabled="!!busyId" @click="$emit('remove',item.instrumentId)">移除自选</el-button></div><p v-if="auth.profile && item.instrumentId!=='HK00700'" class="latest-prediction">
 <router-link v-if="recent[item.instrumentId]" :to="{path:'/predictions',query:{predictionId:recent[item.instrumentId]?.predictionId}}">
  最近预测 · {{stateLabel[recent[item.instrumentId]!.state]}} · {{shanghaiDate.format(new Date(recent[item.instrumentId]!.createdAt))}}
 </router-link>
 <span v-else>{{recentState==='loading'?'正在读取最近预测…':recentState==='error'?'最近预测暂不可用':'暂无本人预测'}}</span>
</p></article></div><PageState v-else-if="listLoading" kind="loading" message="正在读取自选股…"/><PageState v-else-if="listLoaded!==false" kind="empty" message="还没有自选股。先搜索股票，再加入自己的列表。"/><p v-if="marketError" class="inline-warning" role="status">{{marketError}}；自选记录仍可查看和移除。</p><p v-if="recentState==='error'" class="inline-warning" role="status"><el-button text @click="loadRecent">重试最近预测</el-button></p><p v-if="catalog.failed.value" class="inline-warning" role="status">暂无法确认预测模型，行情和自选记录仍可使用。<el-button text @click="catalog.reload">重新检查预测入口</el-button></p></template>
<style scoped>.watch-cards{display:grid;gap:12px}.watch-card{display:grid;grid-template-columns:minmax(140px,1fr) minmax(115px,.6fr) auto;align-items:center;gap:18px;background:#fff;border:1px solid var(--border);border-radius:12px;padding:18px 22px}.stock-code{font-weight:700;font-size:18px;margin:0}.stock-name,.stock-date,.watch-price small{display:block;color:var(--muted);font-size:13px;margin:2px 0}.latest-prediction{grid-column:1/-1;margin:0;font-size:13px;color:var(--muted)}.latest-prediction a{display:inline-flex;align-items:center;min-height:44px}.watch-price strong{display:block;font-size:22px}.watch-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.prediction-entry{display:inline-flex;flex-direction:column;align-items:flex-start}.prediction-entry small{max-width:210px;white-space:normal;font-size:12px;color:var(--muted)}.action-link{font-weight:600;padding:9px}.empty-note{color:var(--muted);padding:24px}.inline-warning{color:#a0481b;font-size:14px}@media(max-width:760px){.watch-card{grid-template-columns:1fr 1fr}.watch-actions{grid-column:1/-1}}@media(max-width:420px){.watch-card{grid-template-columns:1fr}.watch-actions{grid-column:auto}}</style>
