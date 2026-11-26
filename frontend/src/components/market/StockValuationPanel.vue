<script setup lang="ts">
import {onMounted, onUnmounted, ref} from 'vue'
import {stockValuation, type StockValuation} from '../../api/market'
import {formatDecimal} from './format'

const props = defineProps<{instrumentId: string; versionId: string}>()
const data = ref<StockValuation>()
const loading = ref(true)
const failed = ref(false)
let active = true

onMounted(async () => {
  try {
    const result = await stockValuation(props.instrumentId, props.versionId)
    if (active && result.instrumentId === props.instrumentId && result.versionId === props.versionId) {
      data.value = result
    } else if (active) {
      failed.value = true
    }
  } catch {
    if (active) failed.value = true
  } finally {
    if (active) loading.value = false
  }
})
onUnmounted(() => {active = false})
</script>

<template>
  <section class="stock-valuation" aria-labelledby="stock-valuation-heading" :aria-busy="loading">
    <h2 id="stock-valuation-heading">个股估值</h2>
    <p v-if="loading" class="hint" role="status">正在读取估值…</p>
    <p v-else-if="failed" class="hint" role="status">估值暂不可用</p>
    <p v-else-if="data?.state === 'NO_DATA'" class="hint">
      <template v-if="data.tradeDate">{{ data.tradeDate }} 的估值暂无可用数据</template>
      <template v-else>暂无可用于核对估值的成交日</template>
    </p>
    <template v-else-if="data">
      <dl class="metrics">
        <div><dt>市盈率（TTM）</dt><dd>{{ data.peTtm === null ? '暂无' : formatDecimal(data.peTtm, 2) + ' 倍' }}</dd></div>
        <div><dt>市净率（MRQ）</dt><dd>{{ data.pbMrq === null ? '暂无' : formatDecimal(data.pbMrq, 2) + ' 倍' }}</dd></div>
      </dl>
      <p class="hint"><time v-if="data.tradeDate" :datetime="data.tradeDate">{{ data.tradeDate }}</time> · {{ data.provider === 'baostock' ? 'Baostock' : data.provider }}</p>
      <p v-if="data.peTtm?.startsWith('-')" class="hint">负市盈率表示近四个季度盈利为负。</p>
    </template>
  </section>
</template>

<style scoped>
.stock-valuation {background: var(--surface, #fff); border: 1px solid var(--border); border-radius: 14px; padding: 20px 24px; margin-top: 24px}
.stock-valuation h2 {font-size: 20px; margin: 0 0 12px}
.metrics {display: flex; flex-wrap: wrap; gap: 16px 36px; margin: 0 0 12px}
.metrics dt {color: var(--muted); font-size: 14px}
.metrics dd {margin: 4px 0 0; font-weight: 600; font-size: 20px; font-variant-numeric: tabular-nums}
.stock-valuation p {margin: 4px 0 0}
@media (max-width: 680px) {.stock-valuation {padding: 18px}}
</style>
