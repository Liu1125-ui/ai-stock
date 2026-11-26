<script setup lang="ts">
import {computed,onUnmounted,reactive,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import * as api from '../../api/auth'
import {validateRegisterForm,type RegisterForm} from './validation'
const form=reactive<RegisterForm>({email:'',nickname:'',code:'',password:'',confirmPassword:''})
const errors=ref<Partial<Record<keyof RegisterForm,string>>>({}),notice=ref(''),error=ref(''),busy=ref(false),sending=ref(false)
const challenge=ref<api.Challenge>(),now=ref(Date.now()),requestId=ref(''),router=useRouter()
const cooldown=computed(()=>challenge.value?Math.max(0,Math.ceil((Date.parse(challenge.value.resendAfter)-now.value)/1000)):0)
const tick=setInterval(()=>{now.value=Date.now()},1000);let poll:ReturnType<typeof setInterval>|undefined
onUnmounted(()=>{clearInterval(tick);if(poll)clearInterval(poll)})
watch(()=>form.email,()=>{challenge.value=undefined;requestId.value='';notice.value='';if(poll)clearInterval(poll)})
async function send(){if(sending.value||cooldown.value)return;error.value='';sending.value=true
 try{challenge.value=await api.requestChallenge(form.email);requestId.value=crypto.randomUUID();form.code='';notice.value='申请已受理。如邮箱可用于注册，验证码将通过邮件发送；已有账号请直接登录。'
 if(poll)clearInterval(poll);const id=challenge.value.challengeId
 poll=setInterval(async()=>{try{const result=await api.challengeStatus(id);if(challenge.value?.challengeId===id){challenge.value=result;if(result.state==='FAILED'||result.state==='EXPIRED'){error.value='本次验证码发送失败或已过期，请重新申请。';if(poll)clearInterval(poll)}else if(result.state==='SENT'){notice.value='邮件已提交至发件服务器，请查看收件箱。验证码验证成功后才会创建账号。';if(poll)clearInterval(poll)}}}catch{error.value='暂时无法查询发送状态，请检查邮件或稍后重新申请。';if(poll)clearInterval(poll)}},2000)
 }catch(e){error.value=(e as Error).message}finally{sending.value=false}}
async function submit(){errors.value=validateRegisterForm(form);if(Object.keys(errors.value).length)return
 if(!challenge.value){error.value='请先申请邮箱验证码';return}busy.value=true;error.value=''
 try{await api.register({registrationRequestId:requestId.value,challengeId:challenge.value.challengeId,email:form.email,code:form.code,password:form.password,nickname:form.nickname});form.password='';form.confirmPassword='';await router.push({path:'/login',query:{registered:'1'}})}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
</script>
<template><main class="account-wrap"><section class="account-card narrow"><p class="eyebrow">创建账户</p><h1>验证邮箱并注册</h1><p class="hint">验证码验证成功后才创建账号。邮箱忽略首尾空格和大小写。</p>
<el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><el-alert v-if="notice" :title="notice" type="info" :closable="false"/>
<el-form label-position="top" @submit.prevent="submit"><el-form-item label="邮箱" :error="errors.email"><el-input v-model="form.email" type="email" autocomplete="email" :disabled="busy||sending"/></el-form-item>
<el-form-item label="邮箱验证码" :error="errors.code"><div class="inline-field"><el-input v-model="form.code" maxlength="6" inputmode="numeric" autocomplete="one-time-code" :disabled="busy"/><el-button :loading="sending" :disabled="busy||cooldown>0||!form.email" @click="send">{{cooldown?`${cooldown}秒后重发`:'发送验证码'}}</el-button></div></el-form-item>
<el-form-item label="昵称" :error="errors.nickname"><el-input v-model="form.nickname" maxlength="60" autocomplete="nickname" :disabled="busy"/></el-form-item>
<el-form-item label="密码" :error="errors.password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" :disabled="busy"/><span class="hint small">12至64个字符，不超过72字节；空格也属于密码。</span></el-form-item>
<el-form-item label="确认密码" :error="errors.confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" :disabled="busy"/></el-form-item>
<el-button type="primary" native-type="submit" :loading="busy" :disabled="sending" class="full">验证并创建账号</el-button></el-form><p class="footnote">已有账号？<router-link to="/login">登录</router-link></p></section></main></template>
