<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {getMessageEmailDelivery,type EmailDeliveryView} from '../../api/messages'
const props=defineProps<{messageId:string}>(),auth=useAuthStore()
const value=ref<EmailDeliveryView|null>(null),busy=ref(false),error=ref('')
let generation=0
const labels:Record<string,string>={PENDING:'等待投递',CHECKING:'正在核验发送条件',SENDING:'正在提交邮件',RETRY_WAIT:'等待有限重试',SENT:'邮件服务器已接受',FAILED:'发送未完成，已停止重试',CANCELLED:'已取消投递',EXPIRED:'投递已过期',UNKNOWN:'结果待确认',NOT_REQUESTED:'本次未请求邮件',NOT_RECORDED:'未记录邮件状态'}
const reasons:Record<string,string>={SYSTEM_DISABLED:'触发时邮件投递未启用',PREFERENCE_OFF:'触发时个人邮件偏好关闭',EMAIL_UNVERIFIED:'触发时邮箱未验证',RECIPIENT_CHANGED:'账号或收件人状态已改变',RULE_CHANGED:'规则已停用或修改',PREFERENCE_CHANGED:'邮件偏好已改变',DELIVERY_EXPIRED:'超过有效期',SENDING_LEASE_EXPIRED:'发送中断，无法确认结果',SMTP_RESULT_UNKNOWN:'无法确认邮件服务器是否接受',SMTP_NOT_ACCEPTED:'邮件服务器明确未接受',ACCOUNT_UNAVAILABLE:'账号核验暂不可用',PAYLOAD_INVALID:'固定邮件依据未通过核验',ATTEMPTS_EXHAUSTED:'已达到尝试上限'}
async function load(){if(busy.value||!auth.profile)return;const turn=generation,id=props.messageId;busy.value=true;error.value='';try{const result=await getMessageEmailDelivery(id);if(turn===generation)value.value=result}catch(e){if(turn===generation)error.value=(e as Error).message}finally{if(turn===generation)busy.value=false}}
watch(()=>[auth.profile?.userId,props.messageId],()=>{generation++;value.value=null;busy.value=false;error.value=''}, {flush:'sync'})
onUnmounted(()=>{generation++})
</script>
<template><section class="mail-status" aria-label="预警邮件投递"><button class="mail-button" :disabled="busy" @click="load">{{busy?'正在读取…':value?'刷新邮件状态':'查看邮件投递状态'}}</button><p v-if="error" role="alert">{{error}}</p><div v-if="value"><p role="status" aria-atomic="true">预警邮件：{{labels[value.state]??'未知投递状态'}}</p><p v-if="value.reasonCode">{{reasons[value.reasonCode]??'投递状态已记录，请稍后查看。'}}</p><p v-if="value.state==='SENT'">该状态表示邮件服务器接受请求，实际到达情况请查看邮箱。</p><p v-if="value.state==='UNKNOWN'">系统不会自动重复发送，站内提醒仍可查看。</p><p v-if="value.state==='NOT_RECORDED'">这条历史消息没有邮件决策记录，无法据此判断当时的接收偏好。</p><p v-if="value.updatedAt" class="small">SMTP尝试 {{value.attempts}} 次 · 状态更新 <time>{{value.updatedAt}}</time></p></div></section></template>
<style scoped>.mail-status{border-top:1px solid var(--border);margin-top:14px;padding-top:8px}.mail-button{font:inherit;color:var(--accent);background:transparent;border:1px solid var(--border);border-radius:8px;padding:8px 14px;min-height:44px;cursor:pointer}.mail-button:disabled{cursor:wait;opacity:.65}.mail-button:focus-visible{outline:2px solid var(--accent);outline-offset:2px}.mail-status p{margin:8px 0;overflow-wrap:anywhere}.small{font-size:13px}</style>
