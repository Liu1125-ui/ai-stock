<script setup lang="ts">
import {computed,ref,watch,onBeforeUnmount} from 'vue'
import {useAuthStore} from '../../stores/auth'
import type {PredictionRecord} from '../../api/predictions'
import {verificationHistory,verificationDetail,type VerificationHistory,type VerificationEntry,type VerificationPoint} from '../../api/verifications'
const props=defineProps<{record:PredictionRecord}>()
const auth=useAuthStore()
const history=ref<VerificationHistory|null>(null)
const shown=ref<VerificationEntry|null>(null)
const chosenId=ref('')
const busy=ref(false)
const detailBusy=ref(false)
const listError=ref('')
const detailError=ref('')
const historical=computed(()=>!!shown.value&&shown.value.verificationVersionId!==history.value?.currentVersionId)
let generation=0,listRequest=0,selection=0
let followCurrent=true

function showCurrent(){
 followCurrent=true
 selection++
 shown.value=history.value?.current??null
 chosenId.value=history.value?.currentVersionId||''
 detailBusy.value=false
 detailError.value=''
}
async function load(page=1){
 if(!auth.profile||props.record.state!=='SUCCEEDED')return
 const owner=generation,request=++listRequest
 busy.value=true
 listError.value=''
 try{
  const result=await verificationHistory(props.record.predictionId,page)
  if(owner!==generation||request!==listRequest)return
  history.value=result
  if(followCurrent)showCurrent()
 }catch{
  if(owner===generation&&request===listRequest)listError.value='查询失败，已有验证结果保留，请稍后重试。'
 }finally{
  if(owner===generation&&request===listRequest)busy.value=false
 }
}
async function choose(id:string){
 if(id===history.value?.currentVersionId){showCurrent();return}
 if(!id)return
 const owner=generation,selected=++selection
 followCurrent=false
 chosenId.value=id
 detailError.value=''
 detailBusy.value=true
 try{
  const result=await verificationDetail(props.record.predictionId,id)
  if(owner===generation&&selected===selection)shown.value=result
 }catch{
  if(owner===generation&&selected===selection){
   chosenId.value=shown.value?.verificationVersionId||''
   followCurrent=!chosenId.value||chosenId.value===history.value?.currentVersionId
   detailError.value='版本查询失败，仍显示上次验证。'
  }
 }finally{
  if(owner===generation&&selected===selection)detailBusy.value=false
 }
}
watch([()=>auth.profile?.userId,()=>props.record.predictionId,()=>props.record.state],()=>{
 generation++
 history.value=null
 showCurrent()
 listError.value=''
 busy.value=false
 void load()
},{immediate:true,flush:'sync'})
onBeforeUnmount(()=>{generation++})

const points=computed(()=>shown.value?.result.points||history.value?.expectedPoints||[])
function state(point:VerificationPoint){
 if(point.state==='WAITING'){
  const cutoff=historical.value?shown.value?.dueThrough:history.value?.cutoffDate
  if(cutoff&&point.targetDate<=cutoff)return '已到期，等待实际行情核验'
  return historical.value?'当时尚未到期':'尚未到期'
 }
 return {PENDING:'已到期，等待实际行情核验',VERIFIED:'已核验',MISSING:'已检查，行情缺失',SUSPENDED:'停牌',CALENDAR_CHANGED:'交易日历已变更'}[point.state]
}
const chart=computed(()=>{const rows=points.value;const actual=rows.map(p=>p.state==='VERIFIED'&&p.actualPrice!==null&&p.actualPrice!==undefined?Number(p.actualPrice):null);const vals=[...rows.map(p=>Number(p.referencePrice)),...actual.filter((x):x is number=>x!==null)];const low=Math.min(...vals),span=Math.max(...vals)-low||1;const xy=(v:number,i:number)=>({x:40+i*110,y:170-(v-low)/span*120});const dots=actual.flatMap((v,i)=>v===null?[]:[{...xy(v,i),step:i}]);const segments=actual.flatMap((v,i)=>i>0&&v!==null&&actual[i-1]!==null?[{a:xy(actual[i-1]!,i-1),b:xy(v,i),step:i}]:[]);return {forecast:rows.map((p,i)=>{const t=xy(Number(p.referencePrice),i);return `${t.x},${t.y}`}).join(' '),dots,segments}})
const metrics=computed(()=>shown.value?.result.metrics)
function displayMetric(key:keyof NonNullable<typeof metrics.value>){
 const raw=metrics.value?.[key]
 if(raw===null||raw===undefined||raw.trim()==='')return '暂无有效值'
 const value=Number(raw)
 if(!Number.isFinite(value))return '暂无有效值'
 if(key==='directionAccuracy'){
  const verified=points.value.filter(p=>p.state==='VERIFIED')
  const known=verified.filter(p=>typeof p.directionHit==='boolean')
  const count=shown.value?.result.verifiedCount||0
  const details=count>0&&verified.length===count&&known.length===count
   ?`命中${known.filter(p=>p.directionHit).length}/${count}个已核验点`
   :`基于${count}个已核验点`
  return `${(value*100).toFixed(2)}%（${details}）`
 }
 if(key==='relativeMaeImprovement')return `${(value*100).toFixed(2)}%`
 return value.toFixed(key==='priceMae'||key==='priceRmse'?4:6)
}

const metricFields:[keyof NonNullable<typeof metrics.value>,string][]=[['priceMae','价格 MAE（元）'],['priceRmse','价格 RMSE（元）'],['ratioMae','归一化 MAE（比例）'],['ratioRmse','归一化 RMSE（比例）'],['directionAccuracy','方向准确率'],['baselineRatioMae','朴素基线 MAE（比例）'],['baselineRatioRmse','朴素基线 RMSE（比例）'],['relativeMaeImprovement','相对基线改善']]
</script>
<template><section class="verification" aria-labelledby="verification-heading"><div class="heading"><h3 id="verification-heading">事后验证</h3><button data-refresh :disabled="busy||!auth.profile" @click="load(history?.page||1)">{{busy?'正在查询…':'刷新验证记录'}}</button></div><p class="hint">对照固定五日预测与后来取得的真实行情。系统按日检查已到期目标，验证结果按版本保存；刷新只查询保存结果。</p><p v-if="listError" role="alert">{{listError}}</p><p v-if="detailError" role="alert">{{detailError}}</p><p v-if="detailBusy" role="status">正在读取所选版本…</p><p v-if="history?.latestAttempt?.state==='FAILED'" role="status">本次更新没有完成，仍显示上次验证。原因编号：{{history.latestAttempt.errorCode}}</p><p v-else-if="history?.latestAttempt?.expired" role="status">上次执行已超过租约，结果待确认。</p><p v-else-if="history?.latestAttempt?.state==='RUNNING'" role="status">验证正在处理，稍后刷新记录。</p><p v-if="!history&&!busy">{{auth.profile?'尚未取得验证记录。':'请登录后查看本人验证记录。'}}</p><template v-if="history"><p class="hint">当前核验截止日 {{history.cutoffDate}}</p><div v-if="history.total" class="versions"><label>验证版本 <select data-version :value="chosenId" @change="choose(($event.target as HTMLSelectElement).value)"><option v-if="history.currentVersionId&&!history.items.some(v=>v.verificationVersionId===history!.currentVersionId)" :value="history.currentVersionId">当前结果</option><option v-if="chosenId&&chosenId!==history.currentVersionId&&!history.items.some(v=>v.verificationVersionId===chosenId)" :value="chosenId">已选历史版本 · {{shown?.dueThrough||chosenId}}</option><option v-for="v in history.items" :key="v.verificationVersionId" :value="v.verificationVersionId">{{v.verificationVersionId===history.currentVersionId?'当前':'历史'}} · 截至{{v.dueThrough}} · {{v.verifiedCount}}个有效点 · {{v.createdAt}}</option></select></label><div class="paging"><button v-if="chosenId&&chosenId!==history.currentVersionId&&history.current" data-current @click="showCurrent">返回当前验证</button><button :disabled="busy||history.page<=1" @click="load(history.page-1)">上一页</button><span>第 {{history.page}} 页，共 {{history.total}} 个版本</span><button :disabled="busy||history.page*history.size>=history.total" @click="load(history.page+1)">下一页</button></div></div><p v-if="shown" role="status">{{shown.verificationVersionId===history.currentVersionId?'当前验证结果':'历史验证结果'}} · 验证证据截止日 {{shown.dueThrough}} · 有效样本 {{shown.result.verifiedCount}} / 5</p><p v-if="!shown||shown.result.verifiedCount===0">尚无有效样本，评价指标为空。</p><p v-if="shown?.result.originRawRevised" role="status">来源的原点行情已有修订。本次验证按保存证据换算，原预测快照保留。</p><template v-if="points.length"><p class="legend">虚线：预测参考价格；圆点与实线：已核验实际价格。缺失点不连线。</p><svg viewBox="0 0 540 210" role="img" aria-label="预测与真实价格对照；准确数值和状态见下方表格"><path d="M40 20 V180 H500" fill="none" stroke="var(--border)"/><polyline :points="chart.forecast" fill="none" stroke="var(--accent)" stroke-width="2" stroke-dasharray="6 4"/><line v-for="s in chart.segments" :key="s.step" data-actual-segment :x1="s.a.x" :y1="s.a.y" :x2="s.b.x" :y2="s.b.y" stroke="var(--ink)" stroke-width="2"/><circle v-for="p in chart.dots" :key="p.step" data-actual-point :cx="p.x" :cy="p.y" r="4" fill="var(--ink)"/><text v-for="(p,i) in points" :key="p.step" :x="40+i*110" y="204" text-anchor="middle" fill="var(--muted)" font-size="12">{{p.targetDate.slice(5)}}</text></svg><div class="table-scroll" tabindex="0" role="region" aria-label="逐日实际行情验证"><table><caption>统一原点股本口径，价格及误差单位：元；有符号误差＝参考价格－实际价格</caption><thead><tr><th>目标交易日</th><th>参考价格</th><th>实际价格</th><th>有符号误差</th><th>绝对误差</th><th>状态</th></tr></thead><tbody><tr v-for="p in points" :key="p.step"><td>{{p.targetDate}}</td><td>{{p.referencePrice}}</td><td>{{p.state==='VERIFIED'?(p.actualPrice??'—'):'—'}}</td><td>{{p.state==='VERIFIED'?(p.signedError??'—'):'—'}}</td><td>{{p.state==='VERIFIED'?(p.absoluteError??'—'):'—'}}</td><td>{{state(p)}}</td></tr></tbody></table></div></template><dl v-if="shown&&shown.result.verifiedCount>0" class="metrics"><template v-for="[key,label] in metricFields" :key="key"><dt>{{label}}</dt><dd>{{displayMetric(key)}}</dd></template></dl><p v-if="shown" class="hint">指标只统计已核验点，停牌、缺失或日历变更不进入分母。方向相对预测原点收盘价判断。汇总指标已四舍五入，底层计算保留原精度。朴素基线独立对照，预测表现不代表未来收益。</p><details v-if="shown"><summary>查看验证依据</summary><dl><dt>验证版本</dt><dd>{{shown.verificationVersionId}}</dd><dt>来源证据版本</dt><dd>{{shown.realizationVersionId}}</dd><dt>行情版本</dt><dd>{{shown.evidence.rawVersionId}}</dd><dt>采集时间</dt><dd>{{shown.evidence.capturedAt}}</dd><dt>规则版本</dt><dd>{{shown.ruleVersion}}</dd></dl></details></template></section></template>
<style scoped>.verification{margin-top:24px;padding-top:18px;border-top:1px solid var(--border)}.heading{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap}h3{margin:0;font-size:20px}button,select{font:inherit;color:var(--ink);background:var(--surface);border:1px solid var(--border);border-radius:8px;padding:10px 14px;min-height:44px}button{cursor:pointer}button:disabled{cursor:default;opacity:.6}button:focus-visible,select:focus-visible{outline:2px solid var(--accent);outline-offset:3px}.versions label{display:flex;align-items:center;gap:12px;flex-wrap:wrap}select{max-width:100%}.paging{display:flex;align-items:center;gap:12px;flex-wrap:wrap;margin-top:12px}.table-scroll{overflow:auto}table{width:100%;border-collapse:collapse;text-align:left;font-variant-numeric:tabular-nums}th,td{padding:12px;border-bottom:1px solid var(--border);white-space:nowrap}caption{text-align:left;padding:10px 0;color:var(--muted)}svg{display:block;width:100%;max-width:700px;height:auto}.legend{font-size:14px;color:var(--muted)}.metrics{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,2fr);gap:10px}.metrics dd{font-variant-numeric:tabular-nums}dd{margin:0 0 10px;overflow-wrap:anywhere}details{margin-top:16px}summary{cursor:pointer;min-height:44px}dt{font-weight:600}.hint{color:var(--muted);line-height:1.6}</style>
