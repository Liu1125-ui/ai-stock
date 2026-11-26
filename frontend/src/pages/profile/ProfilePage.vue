<script setup lang="ts">
import EmailChangeForm from './EmailChangeForm.vue'
import NotificationPreferences from './NotificationPreferences.vue'
import {onMounted,ref} from 'vue'
import {useRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {rename,changePassword} from '../../api/auth'
const auth=useAuthStore(),router=useRouter(),nickname=ref(''),avatarStyle=ref('default'),oldPassword=ref(''),newPassword=ref(''),confirmation=ref(''),error=ref(''),success=ref(''),busy=ref(false)
onMounted(()=>{nickname.value=auth.profile?.nickname||'';avatarStyle.value=auth.profile?.avatarStyle||'default'})
async function save(){if(!auth.profile)return;busy.value=true;error.value='';success.value='';try{auth.profile=await rename(nickname.value,auth.profile.revision,avatarStyle.value);success.value='个人资料已保存'}catch(e){error.value=(e as Error).message;await auth.refresh().catch(()=>{});nickname.value=auth.profile?.nickname||'';avatarStyle.value=auth.profile?.avatarStyle||'default'}finally{busy.value=false}}
async function password(){if(newPassword.value!==confirmation.value){error.value='两次输入的新密码不一致';return}busy.value=true;error.value='';try{await changePassword(oldPassword.value,newPassword.value);auth.forget();oldPassword.value='';newPassword.value='';confirmation.value='';await router.push('/login')}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><main class="account-wrap"><section class="account-card wide"><p class="eyebrow">我的账户</p><h1>个人中心</h1><p class="hint">管理个人资料与登录安全。</p>
<el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><el-alert v-if="success" :title="success" type="success" :closable="false"/>
<div v-if="auth.profile" class="profile-grid"><section><h2>账户资料</h2><el-descriptions :column="1"><el-descriptions-item label="邮箱">{{auth.profile.email}}</el-descriptions-item><el-descriptions-item label="邮箱状态">{{auth.profile.emailVerified?'已验证':'未验证'}}</el-descriptions-item><el-descriptions-item label="角色">{{auth.isAdmin?'管理员':'普通用户'}}</el-descriptions-item><el-descriptions-item label="实验权限">{{auth.profile.experimentAllowed?'已授予':'未授予'}}</el-descriptions-item></el-descriptions>
<el-form label-position="top" @submit.prevent="save"><el-form-item label="昵称"><el-input v-model="nickname" :disabled="busy"/></el-form-item><el-form-item label="头像样式">
  <div class="avatar-field">
    <span class="profile-avatar" :data-style="avatarStyle" aria-hidden="true">{{Array.from(nickname.trim())[0]||'我'}}</span>
    <select v-model="avatarStyle" aria-label="头像样式" :disabled="busy">
      <option value="default">默认</option><option value="ocean">海蓝</option><option value="jade">青绿</option>
      <option value="plum">梅紫</option><option value="amber">琥珀</option>
    </select>
  </div>
</el-form-item><el-button type="primary" native-type="submit" :loading="busy">保存个人资料</el-button></el-form></section>
<section><h2>修改密码</h2><p class="hint small">修改成功后，所有设备的登录会话都会失效，需要重新登录。</p><el-form label-position="top" @submit.prevent="password"><el-form-item label="当前密码"><el-input v-model="oldPassword" type="password" show-password autocomplete="current-password" :disabled="busy"/></el-form-item><el-form-item label="新密码"><el-input v-model="newPassword" type="password" show-password autocomplete="new-password" :disabled="busy"/></el-form-item><el-form-item label="确认新密码"><el-input v-model="confirmation" type="password" show-password autocomplete="new-password" :disabled="busy"/></el-form-item><el-button native-type="submit" :loading="busy">修改密码并重新登录</el-button></el-form></section></div><EmailChangeForm v-if="auth.profile"/><NotificationPreferences v-if="auth.profile"/></section></main></template>
<style scoped>
.avatar-field{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.avatar-field select{min-height:44px;padding:0 10px;border:1px solid var(--border);border-radius:6px;color:var(--ink);background:var(--surface);font:inherit}
.avatar-field select:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
.avatar-field .profile-avatar{width:44px;height:44px}
</style>
