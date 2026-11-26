<script setup lang="ts">
import {computed} from 'vue'

const props = defineProps<{label: string; savedAt: string; timeLabel?: string}>()
const formatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  dateStyle: 'medium',
  timeStyle: 'short',
})
const displayTime = computed(() => {
  const date = new Date(props.savedAt)
  return Number.isNaN(date.getTime()) ? props.savedAt : formatter.format(date)
})
</script>

<template>
  <div class="saved-result">
    <div class="saved-heading">
      <strong>{{ label }}</strong>
      <span>{{ timeLabel ?? "生成时间" }} <time :datetime="savedAt">{{ displayTime }}</time></span>
    </div>
    <div class="saved-basis"><slot /></div>
    <div v-if="$slots.actions" class="saved-actions"><slot name="actions" /></div>
  </div>
</template>

<style scoped>
.saved-result {margin:12px 0 16px;padding:12px 16px;border:1px solid var(--border);border-inline-start:3px solid var(--accent);border-radius:8px}
.saved-heading,.saved-basis,.saved-actions {display:flex;align-items:baseline;gap:6px 16px;flex-wrap:wrap}
.saved-heading span,.saved-basis {color:var(--muted);font-size:14px}
.saved-basis {margin-top:5px;overflow-wrap:anywhere}
.saved-actions :slotted(a) {display:inline-flex;align-items:center;min-height:44px}
</style>
