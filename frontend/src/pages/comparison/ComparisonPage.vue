<script setup lang="ts">
import SavedResultChanges from '../../components/SavedResultChanges.vue'
import {comparisonChanges} from '../../components/savedResultChanges'
import PageState from '../../components/PageState.vue'
import {computed,onMounted,onUnmounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {validStock} from '../../router/listContext'
import {useAuthStore} from '../../stores/auth'
import {searchInstruments} from '../../api/market'
import {listWatchlist} from '../../api/watchlist'
import {listPositions} from '../../api/positions'
import * as api from '../../api/comparisons'
import {validateSelection,savePending,readPending,clearPending,reasonText} from './comparisonState'
import ComparisonResult from './ComparisonResult.vue'
import ComparisonHistory from './ComparisonHistory.vue'
import ComparisonAiPanel from './ComparisonAiPanel.vue'
const auth=useAuthStore(),route=useRoute(),router=useRouter()
const formHeading=ref<HTMLElement|null>(null)
const selected=ref<string[]>(typeof route.query.ids==='string'?route.query.ids.split(',').filter(Boolean):[]),period=ref<api.Period>('3M')
const choices=ref<{instrumentId:string;displayName:string}[]>([]),options=ref<api.ComparisonOptions|null>(null),result=ref<api.ComparisonResult|null>(null)
const pending=ref<api.ComparisonInput|null>(null),operation=ref<api.Operation|null>(null),checked=ref(false)
const busy=ref(false),loadingOptions=ref(false),loadingDetail=ref(false),searching=ref(false),error=ref(''),optionsError=ref(''),historyRevision=ref(0)
let optionSequence=0,searchSequence=0,detailSequence=0,alive=true
const owner=()=>auth.profile?.userId??''
type SelectionSource='watchlist'|'portfolio'
const selectionSource=ref<SelectionSource|null>(null)
const sourceIds=ref<string[]>([])
const sourcePage=ref(1)
const sourceHasMore=ref(false)
const sourceLoading=ref(false)
const sourceError=ref('')
const sourceLabels={watchlist:'自选股',portfolio:'持仓'}
let sourceSequence=0
async function loadSource(source:SelectionSource,pageNumber=1){
 const requestOwner=owner()
 if(!requestOwner)return
 const sequence=++sourceSequence
 const stillCurrent=()=>alive&&sequence===sourceSequence&&owner()===requestOwner
 selectionSource.value=source
 sourcePage.value=pageNumber
 sourceHasMore.value=false
 sourceIds.value=[]
 sourceLoading.value=true
 sourceError.value=''
 try{
  if(source==='portfolio'){
   const book=await listPositions()
   if(stillCurrent())sourceIds.value=[...new Set(book.items.map(row=>row.instrumentId).filter(validStock))]
  }else{
   const page=await listWatchlist(pageNumber)
   if(stillCurrent()){
    sourceIds.value=[...new Set(page.items.map(row=>row.instrumentId).filter(validStock))]
    sourceHasMore.value=page.page*page.size<page.total
   }
  }
 }catch{
  if(stillCurrent())sourceError.value='读取'+sourceLabels[source]+'失败，请重试。'
 }finally{
  if(stillCurrent())sourceLoading.value=false
 }
}
function toggleSourceStock(id:string){
 if(busy.value||pending.value)return
 if(selected.value.includes(id))selected.value=selected.value.filter(stock=>stock!==id)
 else if(selected.value.length<3)selected.value=[...selected.value,id]
}
function openLinkedSource(value:unknown){
 if(value==='watchlist'||value==='portfolio')void loadSource(value)
}
const allChoices=computed(()=>{const map=new Map(choices.value.map(c=>[c.instrumentId,c]));for(const id of selected.value)if(!map.has(id))map.set(id,{instrumentId:id,displayName:id});return [...map.values()]})
const canStart=computed(()=>!busy.value&&!loadingOptions.value&&!pending.value&&validateSelection(selected.value)&&options.value?.available===true)
const canResume=computed(()=>checked.value&&(!operation.value||(operation.value.state==='RUNNING'&&operation.value.expired)))
const staleResult=computed(()=>result.value&&(result.value.stocks.map(s=>s.instrumentId).join(',')!==selected.value.join(',')||result.value.requestedRange.from!==options.value?.from||result.value.requestedRange.to!==options.value?.to))
async function search(q:string){const seq=++searchSequence;searching.value=true;try{const data=await searchInstruments(q,1,20);if(alive&&seq===searchSequence)choices.value=data.items.filter(s=>validStock(s.instrumentId)&&s.listingStatus!=='DELISTED').map(s=>({instrumentId:s.instrumentId,displayName:s.displayName}))}catch(e){if(alive&&seq===searchSequence)optionsError.value=(e as Error).message}finally{if(alive&&seq===searchSequence)searching.value=false}}
async function loadOptions(){const seq=++optionSequence;options.value=null;optionsError.value='';if(!validateSelection(selected.value)){loadingOptions.value=false;return}loadingOptions.value=true;try{const data=await api.comparisonOptions([...selected.value],period.value);if(alive&&seq===optionSequence)options.value=data}catch(e){if(alive&&seq===optionSequence)optionsError.value=(e as Error).message}finally{if(alive&&seq===optionSequence)loadingOptions.value=false}}
async function loadSaved(id:string){const seq=++detailSequence;loadingDetail.value=true;error.value='';try{const data=await api.comparisonResult(id);if(alive&&seq===detailSequence)result.value=data}catch(e){if(alive&&seq===detailSequence)error.value=(e as Error).message}finally{if(alive&&seq===detailSequence)loadingDetail.value=false}}
async function finish(op:api.Operation,seq:number,requestOwner:string){if(!alive||seq!==detailSequence||requestOwner!==owner())return;operation.value=op;checked.value=true;if(op.state==='SUCCEEDED'&&op.comparisonId){const data=await api.comparisonResult(op.comparisonId);if(!alive||seq!==detailSequence||requestOwner!==owner())return;result.value=data;clearPending(requestOwner);pending.value=null;historyRevision.value++;await router.replace({path:'/comparisons',query:{comparisonId:op.comparisonId}})}else if(op.state==='FAILED'){error.value=`上次对比未完成（${op.errorCode||'未知原因'}）。原结果保留，可重新计算。`}}
async function send(input:api.ComparisonInput){if(busy.value)return;const seq=++detailSequence,requestOwner=owner();loadingDetail.value=false;busy.value=true;error.value='';checked.value=false;try{savePending(requestOwner,input);pending.value=input;await finish(await api.createComparison(input),seq,requestOwner)}catch(e){if(alive&&seq===detailSequence&&requestOwner===owner())error.value=(e as Error).message}finally{if(alive&&requestOwner===owner())busy.value=false}}
async function start(){if(!canStart.value||!options.value?.from||!options.value.to)return;const source=options.value;const input:api.ComparisonInput={requestId:crypto.randomUUID(),instrumentIds:[...selected.value],from:source.from!,to:source.to!,evidenceIds:source.stocks.map(s=>s.evidenceId!)};await send(input)}
async function checkStatus(){if(!pending.value||busy.value)return;const seq=++detailSequence,requestOwner=owner();loadingDetail.value=false;busy.value=true;error.value='';try{await finish(await api.comparisonOperation(pending.value.requestId),seq,requestOwner)}catch(e){if(!alive||seq!==detailSequence||requestOwner!==owner())return;if((e as {status?:number}).status===404){checked.value=true;operation.value=null;error.value='尚未找到原请求，可重试原请求。'}else error.value=(e as Error).message}finally{busy.value=false}}
async function resume(){if(pending.value&&canResume.value)await send(pending.value)}
function newAfterFailure(){if(operation.value?.state!=='FAILED')return;clearPending(owner());pending.value=null;operation.value=null;checked.value=false;error.value=''}
function prepareNewComparison(){
 const saved=result.value
 if(!saved||busy.value||pending.value)return
 const stocks=saved.stocks.map(stock=>stock.instrumentId)
 const monthIndex=(date:string)=>Number(date.slice(0,4))*12+Number(date.slice(5,7))
 const months=monthIndex(saved.requestedRange.to)-monthIndex(saved.requestedRange.from)
 const nextPeriod=months===12?'1Y':months===6?'6M':'3M'
 if(period.value===nextPeriod&&selected.value.join(',')===stocks.join(','))void loadOptions()
 else{selected.value=stocks;period.value=nextPeriod}
 formHeading.value?.focus()
}
function openHistory(id:string){if(busy.value)return;void router.push({path:'/comparisons',query:{comparisonId:id}})}
watch([selected,period],()=>void loadOptions(),{deep:true})
watch(()=>route.query.comparisonId,id=>{if(typeof id==='string'&&result.value?.comparisonId!==id)void loadSaved(id)})
watch(()=>route.query.source,openLinkedSource)
watch(()=>auth.profile?.userId,()=>{sourceSequence++;selectionSource.value=null;sourceIds.value=[];sourcePage.value=1;sourceHasMore.value=false;sourceLoading.value=false;sourceError.value='';selected.value=[];optionSequence++;searchSequence++;detailSequence++;busy.value=false;loadingDetail.value=false;loadingOptions.value=false;searching.value=false;result.value=null;options.value=null;pending.value=null;operation.value=null})
onMounted(()=>{openLinkedSource(route.query.source);void search('');const id=route.query.comparisonId;if(typeof id==='string')void loadSaved(id);try{pending.value=readPending(owner());if(pending.value){selected.value=[...pending.value.instrumentIds];void checkStatus()}else if(validateSelection(selected.value))void loadOptions()}catch(e){error.value=(e as Error).message}})
onUnmounted(()=>{alive=false;optionSequence++;searchSequence++;detailSequence++})
</script>
<template><main class="comparison-page"><p class="eyebrow">同口径历史研究</p><h1>股票对比</h1><p class="hint">选择2—3只股票，查看相同区间内的走势与风险差异。点击开始仅计算指标。</p><section class="panel" :aria-busy="loadingOptions||busy"><h2 ref="formHeading" tabindex="-1">准备对比</h2><div class="selection-grid"><div><label for="comparison-stocks">对比股票（2—3只）</label><el-select id="comparison-stocks" v-model="selected" multiple :multiple-limit="3" filterable remote :remote-method="search" :loading="searching" :disabled="busy" placeholder="搜索并选择股票" aria-label="对比股票"><el-option v-for="stock in allChoices" :key="stock.instrumentId" :label="stock.displayName+' · '+stock.instrumentId" :value="stock.instrumentId"/></el-select></div><div><label for="comparison-period">比较周期</label><el-select id="comparison-period" v-model="period" :disabled="busy" aria-label="比较周期"><el-option label="近三个月" value="3M"/><el-option label="近半年" value="6M"/><el-option label="近一年" value="1Y"/></el-select></div><el-button data-test="compare-start" type="primary" :disabled="!canStart" :loading="busy" @click="start">开始对比</el-button></div>
<p class="muted">目前支持有已发布日线的沪深股票。港股盘中报价可在行情分析页查看。</p><div class="source-picker" aria-label="从本人列表选股" role="group">
 <div class="source-actions">
  <button v-for="source in (['watchlist','portfolio'] as const)" :key="source" type="button" :data-source="source" :aria-pressed="selectionSource===source" @click="loadSource(source)">从{{sourceLabels[source]}}选股</button>
 </div>
 <PageState v-if="sourceLoading" kind="loading" message="正在读取股票列表…"/>
 <PageState v-else-if="sourceError&&selectionSource" kind="error" :message="sourceError" action-label="重新读取" @action="loadSource(selectionSource,sourcePage)"/>
 <template v-else-if="selectionSource">
  <PageState v-if="!sourceIds.length" kind="empty" :message="'本页'+sourceLabels[selectionSource]+'暂无支持对比的股票，可搜索沪深股票。'"/>
  <div v-else class="source-actions">
   <button v-for="id in sourceIds" :key="id" type="button" :data-source-stock="id" :aria-pressed="selected.includes(id)" :disabled="busy||!!pending||(!selected.includes(id)&&selected.length>=3)" @click="toggleSourceStock(id)">{{id}}<span v-if="selected.includes(id)"> · 已选</span></button>
  </div>
  <div v-if="selectionSource==='watchlist'&&(sourcePage>1||sourceHasMore)" class="source-actions">
   <button type="button" :disabled="sourcePage===1" @click="loadSource('watchlist',sourcePage-1)">上一页自选</button>
   <button type="button" :disabled="!sourceHasMore" @click="loadSource('watchlist',sourcePage+1)">下一页自选</button>
  </div>
 </template>
</div>
<p v-if="!validateSelection(selected)" class="muted">请选择2—3只不同的股票。</p><PageState v-if="loadingOptions" kind="loading" message="正在核对已发布数据的覆盖范围…"/><PageState v-if="optionsError" kind="error" :message="optionsError" action-label="重新核对" @action="loadOptions"/><div v-if="options" class="source-note"><p v-if="options.from&&options.to">拟比较区间：{{options.from}} — {{options.to}} · 历史数据，实际共同有效区间以结果为准。</p><p v-for="stock in options.stocks" :key="stock.instrumentId">{{stock.instrumentId}}：<template v-if="stock.reason">{{reasonText(stock.reason)}}</template><template v-else>证据截至 {{stock.sourceTo}} · 采集 {{stock.capturedAt}}<span v-if="stock.rawHeadDate&&stock.sourceTo&&stock.rawHeadDate>stock.sourceTo"> · 当前原始行情已到 {{stock.rawHeadDate}}，对比证据尚未同步</span></template></p></div></section><PageState v-if="error" :kind="pending&&!operation?'pending':'error'" :message="error"/><section v-if="pending" class="panel recovery"><h2>原请求恢复</h2><p>{{pending.instrumentIds.join(' / ')}} · {{pending.from}} — {{pending.to}}</p><p>请求 {{pending.requestId}}<span v-if="operation"> · {{operation.state==='RUNNING'?(operation.expired?'处理超时，可恢复':'处理中'):operation.state==='FAILED'?'未完成':'已完成'}}</span></p><el-button :disabled="busy" @click="checkStatus">查询原请求状态</el-button><el-button v-if="canResume" :disabled="busy" @click="resume">{{operation?.expired?'恢复原请求':'重试原请求'}}</el-button><el-button v-if="operation?.state==='FAILED'" :disabled="busy" @click="newAfterFailure">准备重新计算</el-button></section><PageState v-if="loadingDetail" kind="loading" message="正在读取已保存结果…"/><section v-if="result" class="panel"><p v-if="staleResult" class="muted">以下为已保存结果，尚未按当前选择重新计算。</p><el-button data-test="comparison-prepare-new" :disabled="busy||!!pending" @click="prepareNewComparison">用当前数据准备新对比</el-button><ComparisonResult :result="result"/><SavedResultChanges :owner="auth.profile?.userId" :snapshot="comparisonChanges(result)"/><ComparisonAiPanel :key="result.comparisonId" :comparison-id="result.comparisonId"/></section><div class="panel"><ComparisonHistory :key="historyRevision" @open="openHistory"/></div></main></template>
<style scoped>.comparison-page{max-width:1180px;margin:auto;padding:32px 24px 80px}h1{font-size:32px;margin:4px 0}.panel{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:24px;margin-top:24px;min-width:0}.selection-grid{display:grid;grid-template-columns:minmax(0,1fr) 180px auto;gap:20px;align-items:end}label{display:block;margin-bottom:8px;font-size:14px}.el-select{width:100%}.selection-grid .el-button{min-height:44px}.source-note,.muted{color:var(--muted);font-size:14px}.source-note{border-top:1px solid var(--border);margin-top:20px;padding-top:10px;overflow-wrap:anywhere}.error-note{padding:16px;background:#fff1f0;color:#a12622;border-radius:8px}.recovery{overflow-wrap:anywhere}.recovery h2{font-size:20px}.recovery .el-button{margin-top:8px}.source-picker{border-top:1px solid var(--border);margin-top:14px;padding-top:12px}.source-actions{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px}.source-actions button{font:inherit;font-size:14px;color:var(--ink);border:1px solid var(--border);border-radius:8px;padding:6px 10px;min-height:44px;background:var(--surface);cursor:pointer}.source-actions button[aria-pressed=true]{border-color:var(--accent);color:var(--accent)}.source-actions button:disabled{opacity:.55;cursor:default}@media(max-width:680px){.comparison-page{padding:24px 16px}.panel{padding:18px}.selection-grid{grid-template-columns:1fr}.selection-grid .el-button{width:100%}}
</style>
