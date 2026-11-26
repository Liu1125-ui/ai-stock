<script setup lang="ts">
import ExportSavedResult from '../../components/ExportSavedResult.vue'
import {computed} from 'vue'
import SavedResultNotice from '../../components/SavedResultNotice.vue'
import VerificationPanel from './VerificationPanel.vue'
import ExplanationPanel from './ExplanationPanel.vue'
import type {PredictionRecord} from '../../api/predictions'
import {predictionFailure} from '../../api/predictions'
const props=defineProps<{record:PredictionRecord;historical?:boolean}>()
const points=computed(()=>props.record.state==='SUCCEEDED'?props.record.snapshot?.prediction.points||[]:[])
const line=computed(()=>{const values=points.value.map(p=>Number(p.referencePrice));if(!values.length)return '';const lo=Math.min(...values),hi=Math.max(...values),span=hi-lo||1;return values.map((v,i)=>`${40+i*110},${170-(v-lo)/span*120}`).join(' ')})
</script>
<template><section class="result"><h2>{{record.state==='SUCCEEDED'?'五个交易日参考结果':record.state==='RUNNING'?'预测正在处理':'本次预测未完成'}}</h2><p v-if="record.state==='RUNNING'" role="status">正在准备固定输入或计算模型结果，可以稍后在本人记录中查询。截止时间：{{record.expiresAt}}</p><el-alert v-else-if="record.state==='FAILED'" :title="predictionFailure(record.errorCode)" type="warning" :closable="false"/><template v-else-if="record.snapshot"><SavedResultNotice :label="historical ? '历史预测' : '已保存预测'" :saved-at="record.createdAt" time-label="提交时间">
 <span>预测原点（输入最后一个交易日） {{record.snapshot.input.originDate}}</span>
 <span>原点收盘价 {{record.snapshot.input.originRawClose}} 元</span>
 <span>行情版本 {{record.snapshot.input.rawVersionId}}</span>
 <template v-if="historical" #actions>
  <router-link :to="'/market/'+record.instrumentId">查看最新行情</router-link>
  <router-link :to="'/market/'+record.instrumentId+'/predict'">准备新预测</router-link>
 </template>
</SavedResultNotice><ExportSavedResult :record="record"/><svg v-if="points.length" viewBox="0 0 540 210" role="img" aria-label="五个交易日参考价格走势；具体数值见下方明细"><path d="M40 20 V180 H500" fill="none" stroke="var(--border)"/><polyline :points="line" fill="none" stroke="var(--accent)" stroke-width="3"/><text v-for="(p,i) in points" :key="p.targetDate" :x="40+i*110" y="204" text-anchor="middle" fill="var(--muted)" font-size="12">{{p.targetDate.slice(5)}}</text></svg><div class="table-scroll" tabindex="0" role="region" aria-label="五个目标交易日参考价格"><table><caption>五个目标交易日 · 参考价按原点股本口径换算（元）</caption><thead><tr><th>步长</th><th>目标交易日</th><th>参考价格</th></tr></thead><tbody><tr v-for="p in points" :key="p.step"><td>第{{p.step}}日</td><td>{{p.targetDate}}</td><td>{{p.referencePrice}}</td></tr></tbody></table></div><p class="hint">历史误差参考区间：尚未校准，暂不展示。</p><VerificationPanel :record="record"/><ExplanationPanel :record="record"/></template><details><summary>查看版本依据</summary><dl><dt>预测编号</dt><dd>{{record.predictionId}}</dd><dt>模型制品 / 发布版本</dt><dd>{{record.modelSnapshot.artifactId}} / {{record.snapshot?.prediction.publicationRevision??record.modelSnapshot.revision}}</dd><dt>训练截止</dt><dd>{{record.modelSnapshot.manifest.trainingCutoff}}</dd><template v-if="record.snapshot"><dt>输入版本</dt><dd>{{record.snapshot.input.inputVersionId}}</dd><dt>行情版本</dt><dd>{{record.snapshot.input.rawVersionId}}</dd><dt>输入哈希</dt><dd>{{record.snapshot.input.inputHash}}</dd></template></dl></details></section></template>
<style scoped>.result{margin-top:24px;border-top:1px solid var(--border);padding-top:20px}.result h2{font-size:22px}svg{display:block;width:100%;max-width:700px;height:auto;margin:12px 0}table{width:100%;border-collapse:collapse;max-width:700px;text-align:left;font-variant-numeric:tabular-nums}caption{text-align:left;color:var(--muted);padding:10px 0}th,td{padding:12px;border-bottom:1px solid var(--border)}.table-scroll{overflow:auto}details{margin-top:18px}summary{cursor:pointer;padding:8px 0}dt{font-weight:600}dd{margin:2px 0 12px;overflow-wrap:anywhere;color:var(--muted)}</style>
