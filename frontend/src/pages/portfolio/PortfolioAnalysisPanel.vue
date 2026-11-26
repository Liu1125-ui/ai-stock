<script setup lang="ts">
import {priceBasisLabel} from '../../components/priceBasisLabel'
import ExportSavedResult from '../../components/ExportSavedResult.vue'
import SavedResultChanges from '../../components/SavedResultChanges.vue'
import {portfolioChanges} from '../../components/savedResultChanges'
import SavedResultNotice from '../../components/SavedResultNotice.vue'
import {computed,onUnmounted,ref,watch} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {ApiError} from '../../api/auth'
import * as api from '../../api/portfolioAnalyses'
import PortfolioQuestionPanel from './PortfolioQuestionPanel.vue'
const props=defineProps<{currentBookRevision:string|null}>()
const auth=useAuthStore(),owner=computed(()=>auth.profile?.userId||'')
const selected=ref<api.PortfolioAnalysis|null>(null),history=ref<api.PortfolioAnalysisSummary[]>([])
const operation=ref<api.PortfolioAnalysisOperation|null>(null),requestId=ref<string|null>(null)
const busy=ref(false),initializing=ref(false),historyLoading=ref(false),resultLoading=ref(false)
const error=ref(''),storageNotice=ref(''),missingOperation=ref(false),upper=ref<string|null>(null),nextBefore=ref<string|null>(null)
let epoch=0,historySequence=0,resultSequence=0,operationSequence=0
const pending=computed(()=>!!requestId.value&&!missingOperation.value&&(!operation.value||operation.value.state==='RUNNING'))
const canGenerate=computed(()=>!!owner.value&&!busy.value&&!initializing.value&&!resultLoading.value&&!pending.value)
const stateLabels={COMPLETE:'完整同日估值',PARTIAL:'部分持仓缺价',MIXED_DATES:'价格日期不一致',EMPTY:'空持仓快照'}
const freshnessLabels={FRESH:'日历核验为最新收盘日',STALE:'包含较旧收盘价',UNKNOWN:'价格新鲜度未核实'}
const amounts=computed(()=>selected.value?.totals??selected.value?.subtotal)
const amountTitle=computed(()=>selected.value?.state==='COMPLETE'?'本次组合估值':selected.value?.state==='EMPTY'?'空持仓合计':'可估值部分合计')
function profitAmount(value:string){
 return (Number(value)>0?'+':'')+value+' 元'
}
const storageKey=(id:string)=>'aistock:portfolio-analysis:'+id
const uuid=/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i
function readRequest(id:string){try{const value=sessionStorage.getItem(storageKey(id));return value&&uuid.test(value)?value:null}catch{return null}}
function remember(id:string,rid:string){try{sessionStorage.setItem(storageKey(id),rid)}catch{storageNotice.value='浏览器未能保存恢复标识，刷新后请从已保存历史核对结果。'}}
function message(e:unknown){return e instanceof Error?e.message:'读取暂未完成，请稍后重试。'}
async function loadResult(id:string,account=epoch){const seq=++resultSequence;resultLoading.value=true;try{const result=await api.portfolioAnalysis(id);if(account!==epoch||seq!==resultSequence)return;selected.value=result}catch(e){if(account===epoch&&seq===resultSequence)error.value=message(e)}finally{if(account===epoch&&seq===resultSequence)resultLoading.value=false}}
async function loadHistory(more=false,account=epoch){const seq=++historySequence;historyLoading.value=true;try{const page=more?await api.listPortfolioAnalyses(nextBefore.value??undefined,upper.value??undefined):await api.listPortfolioAnalyses();if(account!==epoch||seq!==historySequence)return;history.value=more?[...history.value,...page.items.filter(row=>!history.value.some(old=>old.analysisId===row.analysisId))]:page.items;upper.value=page.upper;nextBefore.value=page.nextBefore}catch(e){if(account===epoch&&seq===historySequence)error.value=message(e)}finally{if(account===epoch&&seq===historySequence)historyLoading.value=false}}
async function acceptOperation(op:api.PortfolioAnalysisOperation,rid:string,account:number){
 if(account!==epoch||requestId.value!==rid)return
 if(op.requestId!==rid)throw new Error('原请求回执编号不匹配，请继续核对原请求。')
 operation.value=op;missingOperation.value=false
 if(op.state==='FAILED'){error.value='本次分析未完成（'+(op.errorCode||'PORTFOLIO_SOURCE_UNAVAILABLE')+'），已保存结果保留。';return}
 if(op.state==='SUCCEEDED'&&op.analysisId){error.value='';await loadResult(op.analysisId,account)}
}
async function recover(account=epoch){const rid=requestId.value;if(!rid)return;const seq=++operationSequence;try{const op=await api.portfolioAnalysisOperation(rid);if(account!==epoch||seq!==operationSequence||rid!==requestId.value)return;await acceptOperation(op,rid,account)}catch(e){if(account!==epoch||seq!==operationSequence||rid!==requestId.value)return;if(e instanceof ApiError&&e.status===404){missingOperation.value=true;error.value='未找到原请求记录。核对已保存历史后，可明确生成新的分析。'}else error.value=message(e)+'；请先查询原请求状态，不会自动重新提交。'}}
async function queryOperation(){if(busy.value||!requestId.value)return;const account=epoch;busy.value=true;try{await recover(account)}finally{if(account===epoch)busy.value=false}}
async function generate(){if(!canGenerate.value)return;const account=epoch,rid=crypto.randomUUID();requestId.value=rid;operation.value=null;missingOperation.value=false;operationSequence++;resultSequence++;error.value='';busy.value=true;remember(owner.value,rid)
 try{const op=await api.createPortfolioAnalysis(rid);if(account!==epoch||rid!==requestId.value)return;await acceptOperation(op,rid,account);if(op.state==='SUCCEEDED')await loadHistory(false,account)}catch(e){if(account!==epoch||rid!==requestId.value)return;error.value=message(e)+'；正在核对原请求。';await recover(account)}finally{if(account===epoch)busy.value=false}}
async function initialize(account:number){initializing.value=true;try{await loadHistory(false,account);if(account!==epoch)return;if(requestId.value)await recover(account);if(account===epoch&&!selected.value&&!resultLoading.value&&history.value.length)await loadResult(history.value[0].analysisId,account)}finally{if(account===epoch)initializing.value=false}}
watch(owner,id=>{epoch++;historySequence++;resultSequence++;operationSequence++;selected.value=null;history.value=[];operation.value=null;requestId.value=null;error.value='';storageNotice.value='';missingOperation.value=false;upper.value=null;nextBefore.value=null;busy.value=false;historyLoading.value=false;resultLoading.value=false;initializing.value=false;if(id){requestId.value=readRequest(id);void initialize(epoch)}},{immediate:true})
onUnmounted(()=>{epoch++;historySequence++;resultSequence++;operationSequence++})
</script>
<template>
<section class="valuation-panel" aria-labelledby="portfolio-analysis-heading">
 <header class="valuation-heading"><div><h2 id="portfolio-analysis-heading">持仓估值与历史</h2><p class="hint">使用当次持仓和已发布的未复权收盘价。每次生成单独保存。</p></div><button class="primary" data-test="portfolio-generate" :disabled="!canGenerate" @click="generate">{{busy?'正在处理…':selected?'按当前持仓生成新估值':'生成估值分析'}}</button></header>
 <p v-if="error" role="alert" class="analysis-warning">{{error}}</p><p v-if="storageNotice" role="status">{{storageNotice}}</p>
 <div v-if="requestId" class="operation-row"><span v-if="pending" role="status">原请求尚未确认完成，请先查询状态。</span><span v-else>原请求已核对，可查看记录或主动生成新分析。</span><button data-test="portfolio-recover" :disabled="busy||initializing" @click="queryOperation">查询原请求状态</button></div>
 <p v-if="initializing||resultLoading" role="status">正在读取已保存的分析…</p>
<SavedResultChanges :owner="owner" :snapshot="portfolioChanges(selected)"/> <template v-if="selected">
  <SavedResultNotice label="历史估值" :saved-at="selected.createdAt"><span>估值截止日 {{selected.asOfDate}}</span><span>持仓版本 {{selected.bookRevision}}</span></SavedResultNotice><ExportSavedResult :record="selected" private-record/>
  <div class="snapshot-context"><strong>{{stateLabels[selected.state]}}</strong><span>覆盖 {{selected.valuedCount}} / {{selected.positionCount}} 只</span><span>{{freshnessLabels[selected.freshness]}}</span></div>
  <p v-if="currentBookRevision===null" class="analysis-warning">暂无法核对当前持仓版本</p><p v-else-if="currentBookRevision!==selected.bookRevision" class="analysis-warning">持仓已修改。这份估值保留生成时的数量、成本和收盘价。可按当前持仓生成一份新估值。</p><p v-else class="hint small">与当前持仓版本一致。行情日期和报价仍以这份保存记录为准。</p>
  <p class="hint small">实际价格日期见各行。数据读取于 {{selected.capturedAt}}。</p>
  <p v-if="selected.state==='PARTIAL'||selected.state==='MIXED_DATES'" class="analysis-warning">{{selected.mixedDates?'价格日期不一致；':''}}合计仅包含有报价的持仓。组合占比与集中度需全部持仓有同日可用价格。</p>
  <p v-if="selected.state==='EMPTY'" class="hint">本次没有持仓，未读取报价，不计算收益率或占比。</p>
  <h3>{{amountTitle}}</h3>
  <dl v-if="amounts" class="amount-grid"><div><dt>市值（元）</dt><dd>{{amounts.marketValue??'—'}}</dd></div><div><dt>成本（元）</dt><dd>{{amounts.costAmount??'—'}}</dd></div><div><dt>浮动盈亏（元）</dt><dd>{{amounts.floatingProfit??'—'}}</dd></div><div><dt>成本收益率</dt><dd>{{amounts.returnPercent===null?'无法计算':amounts.returnPercent+'%'}}</dd></div></dl>
  <details class="evidence formulas" aria-label="估值计算口径">
   <summary>查看估值计算口径</summary>
   <dl>
    <div><dt>成本</dt><dd>成本＝当次股数 × 平均成本</dd></div>
    <div><dt>市值</dt><dd>市值＝当次股数 × 可用的未复权收盘价</dd></div>
    <div><dt>浮动盈亏</dt><dd>浮动盈亏＝市值－成本。单股的盈亏金额即该股在估值中的盈亏贡献。</dd></div>
    <div><dt>成本收益率</dt><dd>成本收益率＝浮动盈亏 ÷ 成本 × 100%。成本为零时显示无法计算。</dd></div>
    <div><dt>市值占比</dt><dd>市值占比＝单股市值 ÷ 同日组合总市值 × 100%。需全部持仓有同日可用报价且组合总市值大于零。</dd></div>
   </dl>
   <p>每行成本和市值四舍五入到分后再加总。显示占比可能有舍入差。估值范围为未实现的账面盈亏，现金分红及公司行为调整请结合实际记录核对。</p>
  </details>
  <section v-if="selected.lines.length" class="valuation-breakdown" aria-labelledby="valuation-breakdown-heading">
   <h3 id="valuation-breakdown-heading">单股盈亏与占比</h3>
   <article v-for="line in selected.lines" :key="line.positionId" class="valuation-line">
    <div class="line-heading">
     <h4>{{line.instrumentId}}</h4>
     <span v-if="line.quote.status==='AVAILABLE'&&line.quote.priceDate">收盘价日期 {{line.quote.priceDate}}</span>
     <span v-else>没有可估值价格</span>
     <router-link data-test="valuation-latest-market" :to="'/market/'+line.instrumentId"
       :aria-label="'查看 '+line.instrumentId+' 最新行情'">查看最新行情</router-link>
    </div>
    <dl class="line-highlights">
     <div data-profit-contribution>
      <dt>{{selected.mixedDates?'单股浮动盈亏':'浮动盈亏贡献'}}</dt>
      <dd :class="{'profit-positive':Number(line.floatingProfit)>0,'profit-negative':Number(line.floatingProfit)<0}">
       {{line.floatingProfit===null?'无法计算':profitAmount(line.floatingProfit)}}
      </dd>
     </div>
     <div v-if="selected.state==='COMPLETE'&&line.weightPercent!==null" data-market-weight>
      <dt>市值占比</dt><dd>{{line.weightPercent}}%</dd>
      <p v-if="line.concentrationTriggered" class="threshold-note">超过 {{selected.concentrationThresholdPercent}}% 集中度提示阈值</p>
     </div>
    </dl>
    <dl class="line-values">
     <div><dt>当次数量（股）</dt><dd>{{line.quantity}}</dd></div>
     <div><dt>当次平均成本（元）</dt><dd>{{line.averageCost}}</dd></div>
     <div><dt>成本（元）</dt><dd>{{line.costAmount}}</dd></div>
     <div><dt>市值（元）</dt><dd>{{line.marketValue??'无法估值'}}</dd></div>
     <div><dt>成本收益率</dt><dd>{{line.returnPercent===null?'无法计算':line.returnPercent+'%'}}</dd></div>
    </dl>
    <p v-if="line.quote.status==='UNAVAILABLE'" class="analysis-warning">该持仓缺少可用报价，成本保留，市值与盈亏无法计算，本行未计入小计。</p>
    <p v-if="line.quote.retainedPrice" class="analysis-warning">最近保存日为停牌记录，沿用上一次实际成交日的收盘价。</p>
    <details class="evidence">
     <summary>查看本行价格来源与版本</summary>
     <p>{{line.quote.provider}} · {{priceBasisLabel(line.quote.priceBasis)}} · 收盘价 {{line.quote.close??'缺失'}} · {{freshnessLabels[line.quote.freshness]}}</p>
     <p v-if="line.quote.status==='UNAVAILABLE'&&line.quote.reason">报价缺失原因 {{line.quote.reason}}</p>
     <p>行情版本 {{line.quote.versionId??'无'}}；内容指纹 {{line.quote.contentHash??'无'}}</p>
     <p v-if="line.quote.calendarVersionId">日历版本 {{line.quote.calendarVersionId}}；核验至 {{line.quote.checkedThrough}}；指纹 {{line.quote.calendarHash}}</p>
     <p>持仓记录版本 {{line.positionRevision}}</p>
    </details>
   </article>
  </section>
  <PortfolioQuestionPanel :key="selected.analysisId" :analysis="selected" :current-book-revision="currentBookRevision" />
  <details class="evidence"><summary>查看本次保存信息</summary><p>分析编号 {{selected.analysisId}} · 保存于 {{selected.createdAt}}</p><p>公式 {{selected.formulaVersion}} · 依据指纹 {{selected.sourceHash}}</p></details>
 </template>
 <p v-else-if="!initializing&&!resultLoading" class="hint">还没有选中的估值分析。可主动生成，或从下方历史记录中查看。</p>
 <section class="analysis-history" aria-label="本人估值历史"><div class="valuation-heading"><h3>我的估值历史</h3><button :disabled="historyLoading||busy||initializing" @click="loadHistory(false)">刷新历史</button></div><p v-if="historyLoading" role="status">正在读取历史…</p><ul><li v-for="item in history" :key="item.analysisId"><button :disabled="busy||initializing" @click="loadResult(item.analysisId)">{{item.asOfDate}} · {{stateLabels[item.state]}} · 版本 {{item.bookRevision}}</button></li></ul><button v-if="nextBefore" data-test="portfolio-more-history" :disabled="historyLoading||busy||initializing" @click="loadHistory(true)">加载更多历史</button></section>
</section>
</template>
<style scoped>
.valuation-panel{background:var(--surface,#fff);border:1px solid var(--border);border-radius:14px;padding:24px;margin:24px 0;color:var(--ink);overflow-wrap:anywhere}.valuation-heading,.snapshot-context,.operation-row,.line-heading{display:flex;gap:16px;align-items:center;justify-content:space-between;flex-wrap:wrap}.valuation-heading h2{font-size:22px;margin:0}.valuation-heading p{margin:8px 0}.snapshot-context{justify-content:flex-start;padding:14px 0;border-bottom:1px solid var(--border)}button{font:inherit;min-height:44px;padding:9px 16px;border:1px solid var(--border);border-radius:8px;background:var(--surface,#fff);color:var(--ink);cursor:pointer}button.primary{background:var(--primary,#285ad6);color:#fff;border-color:var(--primary,#285ad6)}button:disabled{opacity:.55;cursor:not-allowed}button:focus-visible,summary:focus-visible{outline:3px solid var(--primary,#285ad6);outline-offset:3px}.operation-row{justify-content:flex-start;padding:12px 0}.analysis-warning{padding:12px 14px;border-left:3px solid #9b6216;background:#fff8e9;color:#69420e;line-height:1.65}.amount-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}.amount-grid>div{padding:16px;background:var(--background,#f5f7fc);border-radius:10px}.amount-grid dt,.line-values dt{font-size:14px;color:var(--muted,#52647c)}dd{margin:8px 0 0;font-variant-numeric:tabular-nums}.amount-grid dd{font-size:23px;font-weight:650}.valuation-line{border-top:1px solid var(--border);padding:18px 0}.line-heading h4,.valuation-breakdown h3{margin:0}.line-highlights{display:flex;flex-wrap:wrap;align-items:flex-start;gap:12px 28px;margin:14px 0}.line-highlights dt,.threshold-note{font-size:14px;color:var(--muted)}.line-highlights dd{margin:4px 0 0;font-weight:700;font-size:22px}.profit-positive{color:#9f2e36}.profit-negative{color:#137050}.threshold-note{margin:4px 0 0}.formulas dl>div{margin-bottom:8px}.formulas dt{font-weight:650}.formulas dd{margin:2px 0 0}.line-values{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px}.evidence{margin:12px 0;font-size:14px;line-height:1.7}.evidence summary{cursor:pointer;min-height:44px;display:list-item;align-content:center}.analysis-history{border-top:1px solid var(--border);margin-top:24px;padding-top:16px}.analysis-history ul{list-style:none;padding:0;display:flex;flex-wrap:wrap;gap:10px}.small{font-size:14px;line-height:1.65}@media(max-width:760px){.valuation-panel{padding:18px}.amount-grid,.line-values{grid-template-columns:repeat(2,minmax(0,1fr))}.amount-grid dd{font-size:21px}}@media(max-width:420px){.amount-grid,.line-values{grid-template-columns:1fr}.valuation-heading .primary{width:100%}}
</style>
