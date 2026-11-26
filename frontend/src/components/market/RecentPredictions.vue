<script setup lang="ts">
import {onUnmounted, ref, watch} from 'vue'
import {predictionHistory, type PredictionRecord} from '../../api/predictions'
import {useAuthStore} from '../../stores/auth'

const auth = useAuthStore()
const rows = ref<PredictionRecord[]>([])
const status = ref<'loading'|'ready'|'error'>('loading')
const labels = {RUNNING: '处理中', SUCCEEDED: '已生成', FAILED: '未完成'}
let generation = 0

async function load() {
  const owner = auth.profile?.userId
  const current = ++generation
  rows.value = []
  status.value = owner ? 'loading' : 'ready'
  if (!owner) return
  try {
    const result = await predictionHistory()
    if (current !== generation || owner !== auth.profile?.userId) return
    rows.value = result.items.slice(0, 3)
    status.value = 'ready'
  } catch {
    if (current === generation && owner === auth.profile?.userId) status.value = 'error'
  }
}

const dateFormat = new Intl.DateTimeFormat('zh-CN', {timeZone: 'Asia/Shanghai', dateStyle: 'medium'})
function displayDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : dateFormat.format(date)
}
watch(() => auth.profile?.userId, load, {immediate: true, flush: 'sync'})
onUnmounted(() => {generation++})
</script>

<template>
  <section v-if="auth.profile" class="recent-predictions" aria-labelledby="recent-forecast-heading">
    <header>
      <h2 id="recent-forecast-heading">近期预测</h2>
      <router-link to="/predictions">查看全部</router-link>
    </header>
    <p v-if="status === 'loading'" class="hint" role="status">正在读取预测记录…</p>
    <p v-else-if="status === 'error'" class="hint" role="status">
      预测记录暂时无法读取 <el-button text @click="load">重试</el-button>
    </p>
    <p v-else-if="!rows.length" class="hint">暂无预测记录。可从上方的股票搜索或自选股进入预测。</p>
    <ul v-else>
      <li v-for="row in rows" :key="row.predictionId">
        <router-link :to="{path:'/predictions', query:{predictionId:row.predictionId}}">
          <strong>{{ row.instrumentId }}</strong> · {{ row.horizon }} 个交易日
        </router-link>
        <span>{{ labels[row.state] }} · {{ displayDate(row.createdAt) }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.recent-predictions {background:var(--surface,#fff);border:1px solid var(--border);border-radius:14px;padding:20px 24px;margin-top:24px}
header,li {display:flex;align-items:center;justify-content:space-between;gap:4px 16px;flex-wrap:wrap}
h2 {font-size:20px;margin:0}
ul {margin:8px 0 0;padding:0;list-style:none}
li + li {border-top:1px solid var(--border)}
a {display:inline-flex;align-items:center;min-height:44px;gap:4px}
li span {font-size:13px;color:var(--muted)}
@media(max-width:620px){.recent-predictions{padding:16px 18px}}
</style>
