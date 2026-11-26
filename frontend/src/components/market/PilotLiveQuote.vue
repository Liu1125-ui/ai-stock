<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { pilotLiveQuote, type LiveQuote } from '../../api/market'

const quote = ref<LiveQuote>()
const failed = ref(false)
const labels: Record<LiveQuote['state'], string> = {
  LIVE: '盘中更新',
  WAITING: '等待报价',
  CLOSED: '非交易时段',
  STALE: '报价可能延迟',
  DISCONNECTED: '行情连接中断',
  DISABLED: '盘中报价未启用'
}
const status = computed(() => failed.value ? '暂时无法读取报价' : quote.value ? labels[quote.value.state] : '正在读取报价')
const formatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Hong_Kong', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
})
let timer: number | undefined
let stopped = false
let pending = false

async function refresh() {
  if (stopped || pending || document.hidden) return
  pending = true
  try {
    const data = await pilotLiveQuote()
    if (!stopped) {
      quote.value = data
      failed.value = false
    }
  } catch {
    if (!stopped) failed.value = true
  } finally {
    pending = false
  }
}

onMounted(() => {
  void refresh()
  timer = window.setInterval(refresh, 5000)
  document.addEventListener('visibilitychange', refresh)
})
onBeforeUnmount(() => {
  stopped = true
  window.clearInterval(timer)
  document.removeEventListener('visibilitychange', refresh)
})
</script>

<template>
  <div class="live-quote" aria-label="腾讯控股盘中报价">
    <div>
      <strong v-if="quote?.price">{{ quote.price }} 港元</strong>
      <span v-else>暂无报价</span>
    </div>
    <small>{{ status }}</small>
    <small v-if="quote?.quoteTime">富途 OpenD · {{ formatter.format(new Date(quote.quoteTime)) }}（港时）</small>
    <small v-if="quote?.price && (failed || quote.state !== 'LIVE')">上次收到的报价</small>
  </div>
</template>

<style scoped>
.live-quote { display: grid; gap: 4px; min-width: 0; }
.live-quote strong { font-size: 1.25rem; }
.live-quote small { color: var(--muted); }
</style>
