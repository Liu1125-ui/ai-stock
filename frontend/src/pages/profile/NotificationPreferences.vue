<script setup lang="ts">
import {ref,watch,onUnmounted} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {getPreferences,patchPreferences,type NotificationPreference} from '../../api/alerts'
import {ApiError} from '../../api/auth'
const auth=useAuthStore(),preference=ref<NotificationPreference|null>(null),busy=ref(false),error=ref(''),success=ref('')
let epoch=0,sequence=0
async function load(){const generation=epoch,request=++sequence;try{const value=await getPreferences();if(generation===epoch&&request===sequence)preference.value=value}catch(e){if(generation===epoch&&request===sequence)error.value=(e as Error).message}}
async function toggle(){if(!preference.value||busy.value||!auth.profile)return;const generation=epoch;busy.value=true;error.value='';success.value='';try{const value=await patchPreferences(preference.value.revision,!preference.value.emailEnabled);if(generation!==epoch)return;preference.value=value;success.value='通知偏好已保存'}catch(e){if(generation!==epoch)return;error.value=(e as Error).message;if(e instanceof ApiError&&e.code==='ALERT_REVISION_CONFLICT')await load()}finally{if(generation===epoch)busy.value=false}}
watch(()=>auth.profile?.userId,owner=>{epoch++;sequence++;preference.value=null;busy.value=false;error.value='';success.value='';if(owner)void load()},{immediate:true,flush:'sync'})
onUnmounted(()=>{epoch++;sequence++})
</script>
<template><section class="notification-preferences"><h2>通知偏好</h2><p class="hint small" v-if="preference?.deliveryAvailability==='ENABLED'">邮件服务可用。开启后，仅为新触发且通过发送前核验的预警投递邮件，不影响注册验证码。</p><p class="hint small" v-else-if="preference?.deliveryAvailability==='NOT_CONFIGURED'">邮件配置尚未就绪，接收意愿可以保存，站内提醒仍可查看。</p><p class="hint small" v-else>邮件投递尚未启用。这里保存接入预警邮件后的接收意愿，默认关闭，不影响注册验证码。</p><p class="hint small">关闭后重新开启不会补发旧提醒；已经进入发送的邮件无法保证撤回。</p><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><p v-if="success" role="status">{{success}}</p><template v-if="preference"><p>预警邮件：{{preference.emailEnabled?'当前已开启接收意愿':'当前关闭'}} · {{preference.emailVerified?'邮箱已验证':'邮箱未验证'}}</p><el-button data-testid="toggle-email" :disabled="busy||!preference.emailVerified&&!preference.emailEnabled" :loading="busy" @click="toggle">{{preference.emailEnabled?'关闭预警邮件接收':'开启预警邮件接收意愿'}}</el-button></template><el-button v-else :disabled="busy" @click="load">重新读取通知偏好</el-button></section></template>
<style scoped>.notification-preferences{margin-top:32px;padding-top:24px;border-top:1px solid var(--border)}</style>
