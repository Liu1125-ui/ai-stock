<script setup lang="ts">
import PageState from '../../components/PageState.vue'
import {onMounted,watch,ref,computed} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {analysisReturn,contextPath,listReturn,stockRoute} from '../../router/listContext'
import {daily,instrument,type DailyResponse,type Instrument} from '../../api/market'
import {useWatchlistStore} from '../../stores/watchlist'
import StockValuationPanel from '../../components/market/StockValuationPanel.vue'
import MarketChart from '../../components/market/MarketChart.vue'
import PilotLiveQuote from '../../components/market/PilotLiveQuote.vue'
import StockAiAnalysisPanel from './StockAiAnalysisPanel.vue'
import {formatDecimal} from '../../components/market/format'
const route=useRoute(),router=useRouter(),stock=ref<Instrument|null>(null),history=ref<DailyResponse|null>(null),error=ref(''),actionError=ref(''),busy=ref(false),actionBusy=ref(false),watchlist=useWatchlistStore()
const id=computed(()=>String(route.params.id||''))
const returnList=computed(()=>listReturn(route.query.from))
const analysisContext=computed(()=>{
 const target=analysisReturn(route.fullPath.split('#')[0],id.value)
 return target?contextPath(target):'/market/'+id.value
})
const period=computed<'3m'|'6m'|'1y'>({
 get(){return route.query.period==='6m'||route.query.period==='1y'?route.query.period:'3m'},
 set(value){
  const query:Record<string,string>={}
  if(value!=='3m')query.period=value
  if(returnList.value)query.from=contextPath(returnList.value)
  void router.replace({path:route.path,query})
 }
})
const marketStatus=computed(()=>{
 const data=history.value
 if(!data)return ''
 if(data.state==='NO_DATA')return '暂无已发布日线'
 if(!data.bars.length)return '所选期间没有日线'
 if(!data.bars.some(bar=>bar.tradeStatus===1))return '所选期间无有效成交'
 if(data.freshness==='FRESH')return '已核验近期覆盖'
 if(data.freshness==='STALE')return '缺少预期交易日'
 return '交易日历不足，更新状态待确认'
})
function indicatorValue(value:string|null,places:number,tradeStatus:number){
 return value===null?(tradeStatus===0?'停牌':'样本不足'):formatDecimal(value,places)
}
let sequence=0
function range(){if(period.value==='3m')return {};const now=new Date();const to=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(now);const start=new Date(`${to}T00:00:00Z`);start.setUTCMonth(start.getUTCMonth()-(period.value==='6m'?6:12));return {from:start.toISOString().slice(0,10),to}}
async function load(){const turn=++sequence;if(stock.value?.instrumentId!==id.value){stock.value=null;history.value=null}busy.value=true;error.value='';try{const [info,prices]=await Promise.all([instrument(id.value),daily(id.value,range())]);if(turn===sequence){stock.value=info;history.value=prices}}catch(e){if(turn===sequence){error.value=(e as Error).message;history.value=null}}finally{if(turn===sequence)busy.value=false}}
async function toggle(){const target=id.value;actionBusy.value=true;actionError.value='';try{if(watchlist.items.some(x=>x.instrumentId===id.value))await watchlist.remove(id.value);else await watchlist.add(id.value)}catch(e){if(id.value===target)actionError.value=(e as Error).message}finally{actionBusy.value=false}}
onMounted(()=>{void load();void watchlist.load(1).catch(()=>{})});watch(id,()=>{actionError.value=''})
watch([id,period],()=>void load())
</script>
<template>
  <main class="analysis-page">
    <header class="analysis-head">
      <div>
        <router-link :to="returnList || '/'">{{ returnList?.path==='/watchlist'?'← 返回自选股':'← 返回行情' }}</router-link>
        <h1>{{ stock?.displayName || id }}</h1>
        <p class="stock-meta">{{ id }} · {{ stock?.displayNameVerified?'名称已核验':'名称待核验' }} · {{ stock?.listingStatus==='UNKNOWN'?'状态待核验':stock?.listingStatus||'状态待核验' }}</p>
      </div>
      <div class="head-actions">
        <router-link v-if="id!=='HK00700'" class="stock-action" :to="{path:'/comparisons',query:{ids:id}}">加入对比</router-link>
        <router-link v-if="id!=='HK00700'" class="stock-action" :to="stockRoute('/alerts',analysisContext,{instrumentId:id})">设置预警</router-link>
        <el-button :loading="actionBusy" @click="toggle">{{ watchlist.items.some(x=>x.instrumentId===id)?'移除自选':'加入自选' }}</el-button>
        <router-link v-if="id!=='HK00700'" class="stock-action primary" :to="stockRoute('/market/'+id+'/predict',analysisContext)">发起预测</router-link>
      </div>
    </header>
    <PageState v-if="actionError" kind="error" :message="'未能确认自选股操作。'+actionError"/>

    <section class="analysis-panel status-panel" aria-labelledby="market-status-heading">
      <h2 id="market-status-heading">行情状态</h2><PilotLiveQuote v-if="id==='HK00700'"/><p v-if="id==='HK00700'">港股试点，暂无日线，暂不支持五日预测。</p>
      <PageState v-if="busy" kind="loading" message="正在加载已发布行情…"/>
      <PageState v-else-if="error" kind="error" :message="'读取股票资料或日线失败。'+error" action-label="重新读取行情" @action="load"/>
      <template v-else-if="history">
        <dl class="market-meta">
          <div><dt>更新核验</dt><dd>{{ marketStatus }}</dd></div>
          <div><dt>行情截止日</dt><dd>{{ history.lastTradeDate || '暂无' }}</dd></div>
          <div><dt>数据来源</dt><dd>{{ history.provider || '暂无' }}</dd></div>
        </dl>
        <el-alert v-if="history.lastUpdateError" :title="'最近更新未完成。'+history.lastUpdateError" type="warning" :closable="false"/>
      </template>
    </section>

    <section class="analysis-panel" aria-labelledby="stock-daily-heading">
      <div class="section-head">
        <h2 id="stock-daily-heading" tabindex="-1">日线与技术指标</h2>
        <el-segmented v-model="period" :options="[{label:'近三个月',value:'3m'},{label:'近半年',value:'6m'},{label:'近一年',value:'1y'}]" aria-label="行情查询范围"/>
      </div>
      <template v-if="!busy&&history">
        <MarketChart v-if="history.bars.length" :response="history"/>
        <PageState v-else kind="empty" message="此范围没有已发布的日线，可切换时间范围。"/>
      </template>
      <dl class="indicator-guide" aria-label="技术指标说明">
        <div>
          <dt>MA · 移动平均线</dt>
          <dd>把最近 5、10 或 20 个有成交的交易日的收盘价取平均，便于观察一段时间内的价格走势。</dd>
        </div>
        <div>
          <dt>MACD · 趋势与动能</dt>
          <dd>比较 12 日和 26 日指数移动平均线，再用 9 期信号线对照，观察短期与较长期趋势的差距及变化。</dd>
        </div>
        <div>
          <dt>RSI · 相对强弱指标</dt>
          <dd>用 0 到 100 表示近期上涨相对于下跌的强弱。RSI14 使用 14 期平滑计算，数值越高，近期上涨相对越强。</dd>
        </div>
      </dl>
    </section>

    <StockAiAnalysisPanel v-if="id!=='HK00700'&&history&&!busy" :instrument-id="id" :range-from="history.requestedRange.from" :range-to="history.requestedRange.to" :market-version-id="history.versionId" :has-bars="history.bars.some(x=>x.tradeStatus===1)"/>

    <section v-if="history&&!busy" class="analysis-panel" aria-labelledby="market-basis-heading">
      <h2 id="market-basis-heading">行情数据依据</h2>
      <p v-if="history.explanation" class="data-note">{{ history.explanation }}</p>
      <details>
        <summary>查看数据版本与日线明细</summary>
        <dl class="data-basis">
          <dt>行情版本</dt><dd>{{ history.versionId || '暂无' }}</dd>
          <dt>价格口径</dt><dd>未复权。公司行为可能造成历史价格跳变。</dd>
          <dt>请求期间</dt><dd>{{ history.requestedRange.from }} 至 {{ history.requestedRange.to }}</dd>
          <dt>实际覆盖</dt><dd>{{ history.actualRange ? history.actualRange.from+' 至 '+history.actualRange.to : '暂无' }}</dd>
          <dt>日历核验截至</dt><dd>{{ history.checkedThrough || '暂无' }}</dd>
          <dt>指标口径版本</dt><dd>{{ history.formulaVersion }}</dd>
          <template v-if="history.missingTradingDates.length">
            <dt>缺少的预期交易日</dt><dd>{{ history.missingTradingDates.join('、') }}</dd>
          </template>
        </dl>
        <p class="data-note">指标按完整行情版本计算，停牌日跳过，切换显示期间会保留同一行情版本内的指标口径。</p>
        <div v-if="history.bars.length" class="table-wrap" tabindex="0" role="region" aria-label="最近10条日线明细">
          <table>
            <caption>最近 10 条日线</caption>
            <thead><tr><th scope="col">日期</th><th scope="col">开</th><th scope="col">高</th><th scope="col">低</th><th scope="col">收</th><th scope="col">MA5</th><th scope="col">MACD</th><th scope="col">RSI14</th></tr></thead>
            <tbody>
              <tr v-for="bar in history.bars.slice(-10).reverse()" :key="bar.date">
                <th scope="row">{{ bar.date }}</th>
                <td>{{ formatDecimal(bar.open,2) }}</td>
                <td>{{ formatDecimal(bar.high,2) }}</td>
                <td>{{ formatDecimal(bar.low,2) }}</td>
                <td>{{ formatDecimal(bar.close,2) }}</td>
                <td>{{ indicatorValue(bar.ma5,4,bar.tradeStatus) }}</td>
                <td>{{ indicatorValue(bar.macd,4,bar.tradeStatus) }}</td>
                <td>{{ indicatorValue(bar.rsi14,2,bar.tradeStatus) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </section>

    <StockValuationPanel v-if="history?.versionId" :key="history.versionId" :instrument-id="id" :version-id="history.versionId"/>
  </main>
</template>
<style scoped>
.analysis-page{max-width:1180px;margin:auto;padding:30px 24px 80px}
.analysis-head,.head-actions,.section-head{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}
.analysis-head h1{font-size:32px;margin:6px 0}
.stock-meta,.data-note,dt{color:var(--muted);font-size:14px}
.stock-meta{margin:0}
.stock-action{display:inline-flex;align-items:center;min-height:44px;padding:4px 12px;border:1px solid var(--border);border-radius:8px;background:var(--surface)}
.stock-action.primary{background:var(--accent);border-color:var(--accent);color:#fff}
.analysis-panel{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:24px;margin-top:20px}
.analysis-panel h2{font-size:20px;margin:0}
.status-panel{padding-block:16px}
.market-meta{display:flex;gap:8px 36px;flex-wrap:wrap;margin:10px 0 0}
.market-meta dd{margin:0;font-weight:600}
.status-panel .el-alert{margin-top:12px}
.indicator-guide{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:12px 0 0}
.indicator-guide>div{border:1px solid var(--border);border-radius:8px;padding:10px 12px}
.indicator-guide dt{color:var(--ink);font-weight:700}
.indicator-guide dd{margin:4px 0 0;color:var(--muted);font-size:14px}
details summary{cursor:pointer;min-height:44px;align-content:center;font-weight:600}
.data-basis{display:grid;grid-template-columns:max-content minmax(0,1fr);gap:6px 12px}
.data-basis dd{margin:0;overflow-wrap:anywhere}
.table-wrap{overflow:auto}
table{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}
caption{text-align:left;font-weight:600;padding:12px 0}
th,td{text-align:right;border-bottom:1px solid var(--border);padding:8px;white-space:nowrap}
th:first-child,td:first-child{text-align:left}
@media(max-width:680px){
  .analysis-page{padding:22px 16px}
  .analysis-panel{padding:16px}
  .head-actions{justify-content:flex-start}
  .indicator-guide{grid-template-columns:1fr}
}
</style>
