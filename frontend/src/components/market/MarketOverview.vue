<script setup lang="ts">
import PageState from '../PageState.vue'
import {onMounted, onUnmounted, ref} from 'vue'
import {marketOverview, type MarketOverviewData} from '../../api/market'

const names = {SSE_COMPOSITE: '上证指数', CSI_300: '沪深300'}
const freshnessLabels = {
  FRESH: '已覆盖最近交易日',
  STALE: '行情待更新',
  UNKNOWN: '更新状态待确认',
  NO_DATA: '暂无已发布指数行情',
}
const report = ref<MarketOverviewData>()
const loading = ref(true)
const failed = ref(false)
let active = true
const points = new Intl.NumberFormat('zh-CN', {maximumFractionDigits: 8})

async function load() {
  loading.value = true
  failed.value = false
  try {
    const overview = await marketOverview()
    if (active) report.value = overview
  } catch {
    if (active) failed.value = true
  } finally {
    if (active) loading.value = false
  }
}

function changeLabel(value: string) {
  const amount = Number(value)
  if (amount === 0) return '持平 0.00%'
  return (amount > 0 ? '上涨 ' : '下跌 ') + Math.abs(amount).toFixed(4) + '%'
}

onMounted(load)
onUnmounted(() => {active = false})
</script>

<template>
  <section class="index-overview" aria-labelledby="index-overview-heading" :aria-busy="loading">
    <h2 id="index-overview-heading">市场概览</h2>
    <PageState v-if="loading" kind="loading" message="正在读取指数行情…"/>
    <PageState v-else-if="failed" kind="error" :message="report?.snapshot?'指数行情暂不可用，已加载的快照仍可查看。':'指数行情暂不可用'" action-label="重试指数行情" @action="load"/>
    <PageState v-else-if="report?.freshness==='NO_DATA'" kind="empty" :message="freshnessLabels.NO_DATA"/>
    <p v-else-if="report" class="hint overview-status" role="status">
      {{ freshnessLabels[report.freshness] }}<template v-if="report.freshness === 'STALE' && report.expectedTradeDate"> · 待更新至 {{ report.expectedTradeDate }}</template>
    </p>
    <div v-if="report?.snapshot" class="index-grid">
      <article v-for="index in report.snapshot.indices" :key="index.indexId" class="index-card">
        <h3>{{ names[index.indexId] }}</h3>
        <strong class="points">{{ points.format(Number(index.close)) }} <span>点</span></strong>
        <p class="change" :class="{up: Number(index.changePercent) > 0, down: Number(index.changePercent) < 0}">
          {{ changeLabel(index.changePercent) }}
        </p>
        <p class="hint meta"><time :datetime="index.date">{{ index.date }}</time> 收盘 · {{ report.snapshot.provider === 'baostock' ? 'Baostock' : report.snapshot.provider }}</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.index-overview {margin-top: 24px}
.index-overview h2 {font-size: 20px; margin: 0 0 8px}
.overview-status {margin: 0 0 12px}
.index-grid {display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px}
.index-card {padding: 16px 20px; border: 1px solid var(--border); border-radius: 14px; background: var(--surface, #fff)}
.index-card h3 {font-size: 15px; margin: 0 0 8px}
.points {font-size: 25px; font-variant-numeric: tabular-nums}
.points span, .meta {font-size: 13px}
.change {margin: 5px 0; font-variant-numeric: tabular-nums}
.up {color: #a5163f}
.down {color: #176044}
.meta {margin: 0}
button {min-height: 44px}
@media (max-width: 460px) {.index-grid {grid-template-columns: 1fr}}
</style>
