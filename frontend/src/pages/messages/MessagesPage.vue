<script setup lang="ts">
import PageState from '../../components/PageState.vue'
import {ref,watch,onUnmounted} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {useInboxStore} from '../../stores/inbox'
import {listMessages,markRead,markBatchRead,type Message} from '../../api/messages'
import MessageEvidence from './MessageEvidence.vue'
const auth=useAuthStore(),inbox=useInboxStore()
const rows=ref<Message[]>([]),page=ref(1),total=ref(0),unread=ref(0),unreadOnly=ref(false),loading=ref(false),busy=ref(false),error=ref(''),selected=ref<string[]>([])
let epoch=0,sequence=0
async function load(target=page.value){if(!auth.profile)return;const generation=epoch,turn=++sequence;loading.value=true;error.value='';try{const result=await listMessages(target,20,unreadOnly.value);if(generation!==epoch||turn!==sequence)return;rows.value=result.items;page.value=result.page;total.value=result.total;unread.value=result.unreadCount;selected.value=[];void inbox.refresh()}catch(e){if(generation===epoch&&turn===sequence)error.value=(e as Error).message}finally{if(generation===epoch&&turn===sequence)loading.value=false}}
function filter(value:boolean){if(busy.value)return;unreadOnly.value=value;selected.value=[];void load(1)}
function select(id:string,event:Event){const checked=(event.target as HTMLInputElement).checked;if(checked&&!selected.value.includes(id)&&selected.value.length<50)selected.value.push(id);else if(!checked)selected.value=selected.value.filter(x=>x!==id);else (event.target as HTMLInputElement).checked=false}
async function read(ids:string[],batch=false){if(busy.value||loading.value||!auth.profile||!ids.length)return;const generation=epoch;busy.value=true;error.value='';sequence++;try{const changed=batch?(await markBatchRead(ids)).items:[await markRead(ids[0]!)];if(generation!==epoch)return;const updates=new Map(changed.map(x=>[x.id,x]));let newly=0;rows.value=rows.value.map(x=>{const update=updates.get(x.id);if(update&&!x.readAt&&update.readAt)newly++;return update||x});unread.value=Math.max(0,unread.value-newly);selected.value=[];if(unreadOnly.value){rows.value=rows.value.filter(x=>!x.readAt);total.value=Math.max(0,total.value-newly);await load(total.value>0?Math.min(page.value,Math.ceil(total.value/20)):1)}void inbox.refresh()}catch(e){if(generation===epoch)error.value=(e as Error).message}finally{if(generation===epoch)busy.value=false}}
watch(()=>auth.profile?.userId,owner=>{epoch++;sequence++;rows.value=[];selected.value=[];page.value=1;total.value=0;unread.value=0;unreadOnly.value=false;loading.value=false;busy.value=false;error.value='';if(owner)void load(1)},{immediate:true,flush:'sync'})
onUnmounted(()=>{epoch++;sequence++})
</script>
<template>
 <main class="account-wrap"><section class="account-card wide">
  <p class="eyebrow">站内提醒</p><div class="message-heading"><div><h1>消息中心</h1><p class="hint">查看预警触发时的行情与条件。记录保留当时的依据，不随最新行情变化。</p></div><el-button data-testid="refresh" :disabled="busy||loading" @click="load()">刷新消息</el-button></div>
  <PageState v-if="error" kind="error" :message="error"/>
  <div class="message-tools"><div role="group" aria-label="消息筛选"><el-button :aria-pressed="!unreadOnly" :type="!unreadOnly?'primary':'default'" :disabled="busy" @click="filter(false)">全部消息</el-button><el-button data-testid="unread-filter" :aria-pressed="unreadOnly" :type="unreadOnly?'primary':'default'" :disabled="busy" @click="filter(true)">未读消息（{{unread}}）</el-button></div><el-button data-testid="batch-read" :disabled="!selected.length||busy||loading" :loading="busy" @click="read([...selected],true)">选中标为已读（{{selected.length}} / 50）</el-button></div>
  <PageState v-if="loading" kind="loading" message="正在读取消息…"/><PageState v-else-if="!error&&!rows.length" kind="empty" :message="unreadOnly?'当前没有未读消息。':'暂无触发消息。可以前往预警规则设置条件。'"/>
  <div :aria-busy="loading" class="message-list"><article v-for="item in rows" :key="item.id" class="message-item" :class="{unread:!item.readAt}">
   <div class="message-heading"><label class="message-select"><input type="checkbox" :aria-label="'选择消息 '+item.instrumentId+' '+item.tradeDate" :checked="selected.includes(item.id)" :disabled="busy||loading||(!selected.includes(item.id)&&selected.length>=50)" @change="select(item.id,$event)"/><strong>{{item.instrumentId}}</strong></label><span class="read-status">站内提醒 · {{item.readAt?'已读':'未读'}}</span></div>
   <MessageEvidence :message="item"/>
   <div class="message-actions"><router-link data-testid="history-link" :to="{path:'/alerts',query:{ruleId:item.ruleId}}">查看规则触发历史</router-link><router-link :to="'/market/'+item.instrumentId">查看股票</router-link><el-button data-testid="single-read" :disabled="!!item.readAt||busy||loading" @click="read([item.id])">{{item.readAt?'已读':'标为已读'}}</el-button></div>
  </article></div>
  <el-pagination v-if="total>20" :current-page="page" :page-size="20" :total="total" layout="prev,pager,next" :disabled="busy||loading" @current-change="load"/>
 </section></main>
</template>
<style scoped>
.message-heading,.message-tools,.message-actions{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.message-heading .hint{margin:0 0 16px}.message-tools{border-block:1px solid var(--border);padding:20px 0;margin:16px 0}.message-list{display:grid;gap:16px;margin-top:20px}.message-item{border:1px solid var(--border);border-radius:12px;padding:22px;background:var(--surface)}.message-item.unread{border-left:4px solid var(--accent)}.message-select{display:flex;align-items:center;gap:12px;min-height:44px}.message-select input{width:20px;height:20px;accent-color:var(--accent)}.read-status{font-size:14px;color:var(--muted)}.message-actions{justify-content:flex-start;border-top:1px solid var(--border);padding-top:14px;margin-top:16px}.message-actions a{padding:8px 0}.message-actions button{margin-left:auto}@media(max-width:600px){.message-item{padding:16px}.message-tools{align-items:stretch}.message-actions button{margin-left:0}}
</style>
