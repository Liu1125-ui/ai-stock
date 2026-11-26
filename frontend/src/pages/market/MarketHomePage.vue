<script setup lang="ts">
import PageState from '../../components/PageState.vue'
import {computed,nextTick,onMounted,onUnmounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {contextPath,searchQuery,stockRoute} from '../../router/listContext'
import {useListScroll} from '../../router/listScroll'
import {searchInstruments,summaries,type Instrument,type Summary} from '../../api/market'
import {useWatchlistStore} from '../../stores/watchlist'
import {provideHomePredictionCatalog,quoteAvailability} from '../../components/market/predictionCatalog'
import MarketOverview from '../../components/market/MarketOverview.vue'
import WatchlistCards from '../../components/market/WatchlistCards.vue'
import RecentPredictions from '../../components/market/RecentPredictions.vue'
import RecentMessages from '../messages/RecentMessages.vue'
const route=useRoute(),router=useRouter(),restoreListScroll=useListScroll()
const watchlist=useWatchlistStore(),query=ref(searchQuery(route.query.q)),results=ref<Instrument[]>([]),quotes=ref<Record<string,Summary>>({}),searchError=ref(''),marketError=ref(''),listError=ref(''),searching=ref(false),listLoaded=ref(false),busyId=ref('')
const catalog=provideHomePredictionCatalog()
const popupRequested=ref(false),activeIndex=ref(-1),composing=ref(false)
const suggestions=computed(()=>!searching.value&&searchQuery(query.value)?results.value.slice(0,6):[])
const popupOpen=computed(()=>popupRequested.value&&!composing.value&&suggestions.value.length>0)
const optionId=(index:number)=>'stock-suggestion-'+index
let timer:ReturnType<typeof setTimeout>|undefined,sequence=0
let requestedValue:string|null=null
const pendingRoutes=new Set<string>()

async function loadSearch(value:string,force=false){
 if(!force&&value===requestedValue)return
 requestedValue=value
 const turn=++sequence
 searching.value=true
 searchError.value=''
 try{
  const page=await searchInstruments(value)
  if(turn===sequence)results.value=page.items
 }catch(e){
  if(turn===sequence){results.value=[];searchError.value=(e as Error).message||'请稍后重试'}
 }finally{if(turn===sequence)searching.value=false}
}
function invalidateSearch(){
 clearTimeout(timer)
 sequence++
 requestedValue=null
 results.value=[]
 searchError.value=''
 searching.value=true
 activeIndex.value=-1
}
function closeSuggestions(){popupRequested.value=false;activeIndex.value=-1}
function search(force=false){
 clearTimeout(timer)
 if(composing.value)return
 const value=searchQuery(query.value)
 if(value!==searchQuery(route.query.q)&&!pendingRoutes.has(value)){
  pendingRoutes.add(value)
  void router.replace({path:'/',query:value?{q:value}:{}}).finally(()=>pendingRoutes.delete(value))
 }
 void loadSearch(value,force)
}
watch(()=>searchQuery(route.query.q),value=>{
 if(pendingRoutes.has(value))return
 if(value!==query.value){query.value=value;invalidateSearch();closeSuggestions()}
 void loadSearch(value)
},{flush:'sync'})
function clearSearch(){query.value='';invalidateSearch();closeSuggestions();search()}
function queueSearch(){
 if(composing.value)return
 invalidateSearch()
 popupRequested.value=true
 timer=setTimeout(()=>search(),250)
}
function startComposition(){composing.value=true;invalidateSearch();closeSuggestions()}
function endComposition(event:CompositionEvent){
 composing.value=false
 query.value=(event.target as HTMLInputElement).value
 queueSearch()
}
function openSuggestion(item:Instrument){
 if(!popupOpen.value||!suggestions.value.includes(item))return
 closeSuggestions()
 const from=contextPath({path:'/',query:{q:searchQuery(query.value)}})
 void router.push(stockRoute('/market/'+item.instrumentId,from))
}
function searchKey(event:KeyboardEvent){
 if(composing.value||event.isComposing||event.keyCode===229)return
 if(event.key==='Escape'){
  if(popupRequested.value){event.preventDefault();closeSuggestions()}
  return
 }
 if(event.key==='Enter'){
  event.preventDefault()
  const selected=popupOpen.value?suggestions.value[activeIndex.value]:undefined
  if(selected)openSuggestion(selected)
  else{closeSuggestions();search()}
  return
 }
 if((event.key!=='ArrowDown'&&event.key!=='ArrowUp')||!suggestions.value.length)return
 event.preventDefault()
 popupRequested.value=true
 const last=suggestions.value.length-1
 activeIndex.value=event.key==='ArrowDown'
  ?(activeIndex.value>=last?0:activeIndex.value+1)
  :(activeIndex.value<=0?last:activeIndex.value-1)
 void nextTick(()=>document.getElementById(optionId(activeIndex.value))?.scrollIntoView?.({block:'nearest'}))
}
function marketName(id:string){return id.startsWith('SH')?'沪市':id.startsWith('SZ')?'深市':id.startsWith('HK')?'港股':id.slice(0,2)}
async function loadWatchlist(){listError.value='';marketError.value='';try{await watchlist.load(1)}catch(e){listError.value=(e as Error).message;return}listLoaded.value=true;if(!watchlist.items.length){quotes.value={};return}try{const data=await summaries(watchlist.items.map(x=>x.instrumentId));quotes.value=Object.fromEntries(data.map(x=>[x.instrumentId,x]))}catch{marketError.value='行情暂不可用'}}
async function add(id:string){busyId.value=id;listError.value='';try{await watchlist.add(id);await loadWatchlist()}catch(e){listError.value=(e as Error).message}finally{busyId.value=''}}
async function remove(id:string){busyId.value=id;listError.value='';try{await watchlist.remove(id);delete quotes.value[id]}catch(e){listError.value=(e as Error).message}finally{busyId.value=''}}
onMounted(()=>{
 const path=route.fullPath
 void Promise.all([loadSearch(query.value),loadWatchlist()]).then(()=>restoreListScroll(path))
});onUnmounted(()=>{clearTimeout(timer);sequence++})
</script>
<template>
  <main class="market-page">
    <h1>行情首页</h1>
    <div class="market-layout">
      <div class="market-main">
        <MarketOverview/>
        <section class="market-panel" aria-labelledby="home-search-heading"><div class="section-head"><h2 id="home-search-heading">搜索股票</h2><span>A 股日线 · 五个交易日预测</span></div><label class="search-label" for="stock-query">代码或名称</label><div class="search-controls">
          <div class="search-field">
            <input id="stock-query" v-model="query" type="text" placeholder="例如 茅台或 600519"
              role="combobox" aria-autocomplete="list" autocomplete="off" :aria-expanded="popupOpen"
              :aria-controls="popupOpen?'stock-suggestions':undefined"
              :aria-activedescendant="popupOpen&&activeIndex>=0?optionId(activeIndex):undefined"
              @input="queueSearch" @focus="popupRequested=true" @blur="closeSuggestions"
              @compositionstart="startComposition" @compositionend="endComposition" @keydown="searchKey"/>
            <ul v-if="popupOpen" id="stock-suggestions" class="stock-suggestions" role="listbox" aria-label="股票联想">
              <li v-for="(item,index) in suggestions" :key="item.instrumentId" role="presentation">
                <button :id="optionId(index)" type="button" role="option" tabindex="-1"
                  :aria-selected="index===activeIndex" @mousedown.prevent @click="openSuggestion(item)">
                  <strong>{{item.displayName}}</strong><span> · {{item.instrumentId.slice(2)}} · {{marketName(item.instrumentId)}}</span>
                </button>
              </li>
            </ul>
          </div>
          <button v-if="query" type="button" class="search-action" @click="clearSearch">清空</button>
          <button type="button" class="search-action" @click="closeSuggestions();search()">搜索</button>
        </div><PageState v-if="searchError" kind="error" :message="'股票搜索暂不可用。'+searchError" action-label="重试搜索" @action="search(true)"/><PageState v-else-if="searching" kind="loading" message="正在搜索…"/><div v-else-if="results.length" class="result-list"><article v-for="item in results" :key="item.instrumentId" class="result-row"><div><strong>{{item.displayName}}</strong><span>{{item.ticker}} · {{item.displayNameVerified?'名称已核验':'名称待核验'}}</span><small>{{item.instrumentId==='HK00700'?'盘中报价试点 · 可查看连接状态':quoteAvailability(item.summary)}}<template v-if="item.summary.lastTradeDate"> · {{item.summary.lastTradeDate}}</template></small><small>{{item.forecastCapability==="UNAVAILABLE"?item.forecastReason:catalog.reason(item.instrumentId,item.summary)}}</small></div><div class="row-actions"><router-link :to="stockRoute('/market/'+item.instrumentId,route.fullPath)">{{item.instrumentId==='HK00700'?'查看盘中报价':'查看分析'}}</router-link><router-link v-if="item.forecastCapability!=='UNAVAILABLE'&&catalog.canSubmit(item.instrumentId,item.summary)" :to="stockRoute('/market/'+item.instrumentId+'/predict',route.fullPath)">发起预测</router-link><el-button type="primary" plain :loading="busyId===item.instrumentId" :disabled="!!busyId||watchlist.items.some(x=>x.instrumentId===item.instrumentId)" @click="add(item.instrumentId)">{{watchlist.items.some(x=>x.instrumentId===item.instrumentId)?'已在自选':'加入自选'}}</el-button></div></article></div><PageState v-else kind="empty" message="没有找到匹配的股票，可换一个名称或代码。" :action-label="query?'清空搜索':undefined" @action="clearSearch"/></section>
        <section class="market-panel" aria-labelledby="home-watchlist-heading">
          <div class="section-head">
            <h2 id="home-watchlist-heading">我的自选股</h2>
            <router-link to="/watchlist">查看全部</router-link>
          </div>
          <PageState v-if="listError" kind="error" :message="'自选股操作未完成。'+listError" action-label="重新读取自选股" @action="loadWatchlist"/>
          <p v-if="listLoaded&&!listError&&!watchlist.loading&&!watchlist.items.length" class="empty-note">
            自选股还是空的。搜索股票后可加入自选。<a href="#stock-query">去搜索股票</a>
          </p>
          <WatchlistCards v-else :items="watchlist.items.slice(0,5)" :return-to="route.fullPath" :list-loading="watchlist.loading" :list-loaded="listLoaded&&!listError" :summaries="quotes" :market-error="marketError" :busy-id="busyId" @remove="remove"/>
        </section>
      </div>
      <div class="home-activity" role="group" aria-label="近期动态">
        <RecentPredictions/>
        <RecentMessages/>
      </div>
    </div>
    <details class="market-panel guide">
      <summary>行情数据说明</summary>
      <p>图表标注行情来源、截止日和更新状态。价格采用未复权口径，公司行为可能造成历史价格跳变。</p>
      <p>MA、MACD、RSI 按完整历史版本计算。</p>
      <router-link to="/diagnostics">查看工程诊断</router-link>
    </details>
  </main>
</template>
<style scoped>.market-page{max-width:1230px;margin:auto;padding:36px 24px 80px}.market-layout{display:grid;grid-template-columns:minmax(0,2fr) minmax(270px,1fr);gap:24px}.market-page h1{font-size:30px;margin:0 0 20px}.market-panel{background:#fff;border:1px solid var(--border);border-radius:14px;padding:24px;margin-top:24px;box-shadow:0 8px 28px rgba(35,60,110,.04)}.section-head{display:flex;justify-content:space-between;align-items:center;gap:12px}.section-head h2{font-size:20px;margin:0 0 16px}.section-head span{font-size:13px;color:var(--muted)}.search-label{display:block;font-weight:600;margin:4px 0 8px}.result-list{margin-top:18px}.result-row{display:flex;align-items:center;justify-content:space-between;gap:18px;border-top:1px solid var(--border);padding:16px 0}.result-row strong,.result-row span,.result-row small{display:block}.result-row span,.result-row small{color:var(--muted)}.row-actions{display:flex;align-items:center;gap:12px;white-space:nowrap;flex-wrap:wrap}.market-main,.home-activity{min-width:0}.home-activity>:first-child{margin-top:0}.guide{color:var(--muted);padding-block:8px}.guide summary{color:var(--ink);cursor:pointer;min-height:44px;align-content:center;font-weight:600}.empty-note{color:var(--muted);margin-bottom:0}.empty-note a{display:inline-flex;align-items:center;min-height:44px;margin-inline-start:8px}@media(max-width:900px){.market-layout{grid-template-columns:minmax(0,1fr)}}@media(max-width:620px){.market-page{padding:24px 16px}.result-row{align-items:flex-start;flex-direction:column}.row-actions{flex-wrap:wrap}.market-panel{padding:18px}}</style>
<style scoped>
.search-controls{display:flex;gap:8px;align-items:flex-start}
.search-field{position:relative;flex:1;min-width:0}
.search-field input,.search-action{box-sizing:border-box;min-height:44px;border:1px solid var(--border);border-radius:8px;background:#fff;color:var(--ink);font:inherit;padding:8px 12px}
.search-field input{width:100%}
.search-action{cursor:pointer;white-space:nowrap}
.search-field input:focus-visible,.search-action:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.stock-suggestions{position:absolute;inset-inline:0;top:calc(100% + 4px);z-index:5;margin:0;padding:4px;list-style:none;border:1px solid var(--border);border-radius:8px;background:#fff;box-shadow:0 6px 16px rgba(35,60,110,.14);max-height:min(19rem,45vh);overflow:auto}
.stock-suggestions button{display:block;width:100%;min-height:44px;border:0;border-radius:5px;background:transparent;color:var(--ink);font:inherit;text-align:left;padding:8px;overflow-wrap:anywhere;cursor:pointer}
.stock-suggestions button:hover,.stock-suggestions button[aria-selected=true]{background:#edf3fe;color:#2148ae}
.stock-suggestions span{font-size:.875rem}
@media(max-width:480px){.search-field input,.search-action{padding-inline:8px}}
</style>
