<script setup lang="ts">
defineProps<{
  kind: 'loading' | 'empty' | 'error' | 'pending' | 'expired'
  message: string
  actionLabel?: string
  busy?: boolean
  dismissible?: boolean
}>()
defineEmits<{action:[];dismiss:[]}>()
</script>

<template>
  <div class="page-state" :class="'state-'+kind" :data-state="kind">
    <p :role="kind==='error'||kind==='expired'?'alert':'status'">{{ message }}</p>
    <button v-if="actionLabel" type="button" :disabled="busy" @click="$emit('action')">{{ actionLabel }}</button>
    <button v-if="dismissible" class="dismiss" type="button" aria-label="关闭提示" @click="$emit('dismiss')">关闭</button>
  </div>
</template>

<style scoped>
.page-state{display:flex;align-items:center;gap:4px 12px;flex-wrap:wrap;margin:12px 0;padding:8px 12px;border:1px solid var(--border);border-radius:8px;color:var(--muted);background:var(--surface)}
.page-state p{margin:0;font-size:14px;overflow-wrap:anywhere}
.state-error,.state-expired{color:#9b351c;border-inline-start:3px solid currentColor}
.state-pending{color:#775117;border-inline-start:3px solid currentColor}
button{font:inherit;color:var(--accent);background:transparent;border:0;min-height:44px;padding:6px 8px;cursor:pointer}
button:disabled{opacity:.55;cursor:default}
.dismiss{margin-inline-start:auto}
</style>
