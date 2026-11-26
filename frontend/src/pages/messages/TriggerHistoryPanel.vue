<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {listAlertTriggers,type Message} from '../../api/messages'
import MessageEvidence from './MessageEvidence.vue'
const props=defineProps<{ruleId:string}>(),auth=useAuthStore()
const rows=ref<Message[]>([]),page=ref(1),total=ref(0),loading=ref(false),error=ref('')
let epoch=0,sequence=0
async function load(target=page.value){if(!props.ruleId||!auth.profile)return;const generation=epoch,turn=++sequence;loading.value=true;error.value='';try{const result=await listAlertTriggers(props.ruleId,target);if(generation!==epoch||turn!==sequence)return;rows.value=result.items;page.value=result.page;total.value=result.total}catch(e){if(generation===epoch&&turn===sequence)error.value=(e as Error).message}finally{if(generation===epoch&&turn===sequence)loading.value=false}}
watch(()=>[props.ruleId,auth.profile?.userId],()=>{epoch++;sequence++;rows.value=[];page.value=1;total.value=0;error.value='';loading.value=false;void load(1)},{immediate:true,flush:'sync'})
onUnmounted(()=>{epoch++;sequence++})
</script>
<template><section class="history-panel" aria-label="规则触发历史"><div class="history-head"><h2>触发历史（{{total}}）</h2><el-button :disabled="loading" @click="load()">刷新历史</el-button></div><p class="hint small">规则 {{ruleId}} · 以下为触发当时的固定记录，当前阈值修改不影响历史。</p><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><p v-if="loading" role="status">正在读取触发历史…</p><p v-else-if="!error&&!rows.length" class="hint">暂无触发记录，不代表历史上从未满足条件。规则生效边界之前不补发；数据缺失或待核验时不生成触发消息。</p><article v-for="item in rows" :key="item.triggerId" class="history-item"><strong>{{item.instrumentId}}</strong><MessageEvidence :message="item"/></article><el-pagination v-if="total>20" :current-page="page" :page-size="20" :total="total" layout="prev,pager,next" :disabled="loading" @current-change="load"/></section></template>
<style scoped>.history-panel{margin-top:28px;border-top:1px solid var(--border);padding-top:24px}.history-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.history-head h2{margin:0}.history-panel>.hint{overflow-wrap:anywhere}.history-item{padding:18px;margin-top:16px;border:1px solid var(--border);border-radius:10px}</style>
