<script setup lang="ts">
import {priceBasisLabel} from '../../components/priceBasisLabel'
import ExportSavedResult from '../../components/ExportSavedResult.vue'
import type {ComparisonResult,Metric} from '../../api/comparisons'
import SavedResultNotice from '../../components/SavedResultNotice.vue'
import ComparisonChart from './ComparisonChart.vue'
import {reasonText} from './comparisonState'
defineProps<{result:ComparisonResult}>()
function metric(m:Metric){return m.status==='AVAILABLE'?`${m.value} ${m.unit}`:`不可计算：${reasonText(m.reason)}`}
</script>
<template><section class="result-panel" aria-labelledby="comparison-result-title"><h2 id="comparison-result-title">对比结果</h2><SavedResultNotice label="历史对比" :saved-at="result.createdAt"><span v-if="result.actualRange">共同有效区间截至 {{result.actualRange.to}}</span><span v-else>请求区间截至 {{result.requestedRange.to}}</span><span>{{result.provider}} · {{priceBasisLabel(result.basis)}}</span><span>计算版本 {{result.formulaVersion}}</span></SavedResultNotice><ExportSavedResult :record="result"/><p class="muted">请求区间：{{result.requestedRange.from}} — {{result.requestedRange.to}}<br>共同有效区间：{{result.actualRange?`${result.actualRange.from} — ${result.actualRange.to}`:'不足两个共同有效端点'}}<br>年化约定 {{result.annualizationDays}} 个交易日</p><p class="muted">归一化曲线以共同起点为100。缺口断线；历史表现仅供比较，不代表未来走势或收益。</p><ComparisonChart v-if="result.actualRange" :stocks="result.stocks"/><div class="metric-grid"><article v-for="stock in result.stocks" :key="stock.instrumentId" class="stock-card"><h3>{{stock.instrumentId}}</h3><dl><template v-for="[key,label] in [['intervalReturn','区间涨跌幅'],['annualizedVolatility','年化波动率'],['maxDrawdown','最大回撤'],['meanVolume','日均成交量']] as const" :key="key"><dt>{{label}}</dt><dd>{{metric(stock[key])}}<small>有效观测：{{stock[key].observationCount}}</small></dd></template></dl><ul v-if="stock.dataIssues?.length" class="data-issues"><li v-for="issue in stock.dataIssues" :key="issue.date+issue.field">{{issue.date}} · {{issue.field==='backwardClose'?'价格':'成交量'}}：{{issue.state==='SUSPENDED'?'停牌':'数据缺失'}}</li></ul></article></div><details class="metric-guide">
 <summary>如何读懂对比指标</summary>
 <dl>
  <dt>区间涨跌幅</dt><dd>用共同区间终点的收盘价与起点相比，衡量从起点到终点的变化。</dd>
  <dt>年化波动率</dt><dd>衡量日收益上下波动的幅度，按每年 {{result.annualizationDays}} 个交易日换算。</dd>
  <dt>最大回撤</dt><dd>共同区间内，从某个历史高点到之后低点的最大跌幅。</dd>
  <dt>日均成交量</dt><dd>共同区间内每日成交股数的平均值。</dd>
 </dl>
</details>
<details><summary>查看保存证据与归一化明细</summary><p class="source-ref">对比编号 {{result.comparisonId}}。以下日期、来源与指标均取自当次保存的对比记录。</p><p class="muted">成交量单位为股，股数不能直接等同于资金流动性。指标由后端计算，展示值不回用于计算。</p><div v-for="source in result.sourceRefs" :key="source.evidenceId" class="source-ref"><strong>{{source.instrumentId}}</strong><p>证据覆盖 {{source.from}} — {{source.to}} · 采集 {{source.capturedAt}}</p><p>证据 {{source.evidenceId}}<br>原始版本 {{source.rawVersionId}}<br>内容校验 {{source.contentHash}}</p></div><div v-for="stock in result.stocks" :key="stock.instrumentId" class="detail-table"><table><caption>{{stock.instrumentId}} 归一化明细</caption><thead><tr><th>日期</th><th>共同起点=100</th></tr></thead><tbody><tr v-for="point in stock.chart" :key="point.date"><td>{{point.date}}</td><td>{{point.normalizedClose??'缺失 / 停牌'}}</td></tr></tbody></table></div></details></section></template>
<style scoped>.result-panel h2{margin-top:0}.muted{color:var(--muted);font-size:14px}.data-issues{font-size:13px;max-height:160px;overflow:auto;padding-left:20px;color:var(--muted)}.metric-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,240px),1fr));gap:16px}.stock-card{border:1px solid var(--border);border-radius:10px;padding:18px;min-width:0}h3{margin-top:0}dl{margin:0}dt{color:var(--muted);font-size:14px}dd{margin:2px 0 18px;font-variant-numeric:tabular-nums;overflow-wrap:anywhere}small{display:block;color:var(--muted);font-size:12px}details{margin-top:24px}summary{cursor:pointer;padding:12px 0;min-height:44px}.source-ref{overflow-wrap:anywhere;font-size:13px}.detail-table{max-height:320px;overflow:auto}table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}th,td{text-align:left;border-bottom:1px solid var(--border);padding:8px}caption{text-align:left;padding:12px 0;font-weight:600}</style>
