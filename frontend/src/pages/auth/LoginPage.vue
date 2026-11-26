<script setup lang="ts">
import PageState from '../../components/PageState.vue'
import {ref} from 'vue'
import {useRouter,useRoute} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {isPageModuleError} from '../../router/pageRecovery'
import {loginReturn} from '../../router/loginReturn'
const email=ref(''),password=ref(''),busy=ref(false),error=ref('')
const auth=useAuthStore(),router=useRouter(),route=useRoute()
async function submit(){error.value='';busy.value=true;try{await auth.signIn(email.value,password.value);password.value='';await router.push(loginReturn(router,route.query.returnTo)??'/profile')}catch(e){error.value=isPageModuleError(e)?'页面资源正在更新，请刷新页面后重试。':(e as Error).message||'登录未完成'}finally{busy.value=false}}
</script>
<template><main class="account-wrap"><section class="account-card narrow"><p class="eyebrow">账户访问</p><h1>登录</h1><p class="hint">使用已验证的邮箱进入你的个人中心。</p>
<el-alert v-if="route.query.emailChanged" title="邮箱已更换，请使用新邮箱登录。" type="success" :closable="false"/>
<el-alert v-if="route.query.registered" title="邮箱验证成功，账号已创建，请登录。" type="success" :closable="false"/>
<PageState v-if="route.query.unavailable&&!auth.loaded&&!auth.profile&&!error" kind="error" message="账号服务暂不可用，请稍后重试登录。"/>
<PageState v-if="error" kind="error" :message="error"/>
<el-form label-position="top" @submit.prevent="submit"><el-form-item label="邮箱"><el-input v-model="email" type="email" autocomplete="username" :disabled="busy" required /></el-form-item>
<el-form-item label="密码"><el-input v-model="password" type="password" show-password autocomplete="current-password" :disabled="busy" required /></el-form-item>
<el-button type="primary" native-type="submit" :loading="busy" class="full">登录</el-button></el-form>
<p class="footnote">还没有账号？<router-link to="/register">验证邮箱并注册</router-link></p></section></main></template>
