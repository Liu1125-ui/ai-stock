<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {listMessages,type Message} from '../../api/messages'
const auth=useAuthStore(),rows=ref<Message[]>([]),error=ref(''),loading=ref(false)
let epoch=0,sequence=0
async function load(){if(!auth.profile)return;const generation=epoch,turn=++sequence;loading.value=true;error.value='';try{const result=await listMessages(1,3,false);if(generation===epoch&&turn===sequence)rows.value=result.items.slice(0,3)}catch(e){if(generation===epoch&&turn===sequence)error.value=(e as Error).message}finally{if(generation===epoch&&turn===sequence)loading.value=false}}
watch(()=>auth.profile?.userId,()=>{epoch++;sequence++;rows.value=[];error.value='';loading.value=false;void load()},{immediate:true,flush:'sync'});onUnmounted(()=>{epoch++;sequence++})
</script>
<template><section class="recent-messages" aria-label="最近提醒"><div class="recent-head"><h2>最近提醒</h2><router-link to="/messages">查看全部</router-link></div><p v-if="error" role="alert">{{error}} <el-button text :disabled="loading" @click="load">重试</el-button></p><p v-else-if="loading" role="status">正在读取提醒…</p><p v-else-if="!rows.length" class="hint">暂无触发消息。<router-link to="/alerts">设置预警规则</router-link></p><ul v-if="rows.length"><li v-for="item in rows" :key="item.id"><router-link :to="{path:'/alerts',query:{ruleId:item.ruleId}}"><strong>{{item.instrumentId}}</strong> · {{item.tradeDate}} <span>{{item.readAt?'已读':'未读'}}</span></router-link><p>已保存该次触发依据，可查看当时条件与数据。</p></li></ul></section></template>
<style scoped>.recent-messages{background:var(--surface);border:1px solid var(--border);border-radius:14px;padding:24px;margin-top:24px}.recent-head{display:flex;align-items:center;justify-content:space-between;gap:16px}.recent-head h2{font-size:20px;margin:0}.recent-messages ul{list-style:none;padding:0;margin-bottom:0}.recent-messages li{border-top:1px solid var(--border);margin-top:14px;padding-top:14px}.recent-messages li p{font-size:13px;color:var(--muted);margin:4px 0}.recent-messages span{font-size:13px}.recent-messages a{display:inline-block;min-height:32px}.recent-messages>.hint{margin-bottom:0}</style>
