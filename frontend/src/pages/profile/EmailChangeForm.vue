<script setup lang="ts">
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {confirmEmailChange,emailChangeStatus,requestEmailChange,type Challenge} from '../../api/auth'

const auth=useAuthStore(),router=useRouter()
const open=ref(false),email=ref(''),password=ref(''),code=ref(''),target=ref('')
const challenge=ref<Challenge|null>(null),busy=ref(false),error=ref('')
const deliveryText:Record<Challenge['state'],string>={
  PENDING:'验证码邮件已排队。',ACCEPTED:'邮件服务正在处理。',SENT:'验证码邮件已发送。',
  FAILED:'邮件投递失败，可以稍后重新申请。',EXPIRED:'验证码已失效，请重新申请。'
}
async function run(action:()=>Promise<void>){
  if(busy.value)return
  busy.value=true;error.value=''
  try{await action()}catch(e){error.value=(e as Error).message}finally{busy.value=false}
}
function send(){
  if(!email.value.trim()||!password.value)return
  return run(async()=>{
    const address=email.value.trim(),secret=password.value
    password.value=''
    challenge.value=await requestEmailChange(address,secret)
    target.value=address
  })
}
function refresh(){
  const id=challenge.value?.challengeId
  if(id)return run(async()=>{challenge.value=await emailChangeStatus(id)})
}
function confirm(){
  const id=challenge.value?.challengeId
  if(!id||!/^[0-9]{6}$/.test(code.value))return
  return run(async()=>{
    await confirmEmailChange(id,code.value)
    reset()
    auth.forget()
    await router.replace({path:'/login',query:{emailChanged:'1'}})
  })
}
function reset(){challenge.value=null;code.value='';password.value='';error.value=''}
function close(){reset();open.value=false;email.value='';target.value=''}
</script>

<template>
  <section class="email-change" aria-labelledby="email-change-title">
    <h2 id="email-change-title">更换邮箱</h2>
    <p class="hint small">新邮箱验证成功后才会替换当前邮箱。成功后，请使用新邮箱重新登录。</p>
    <el-button v-if="!open" @click="open=true">更换邮箱</el-button>
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/>
      <el-form v-if="!challenge" label-position="top" @submit.prevent="send">
        <el-form-item label="新邮箱">
          <el-input v-model="email" type="email" autocomplete="email" :maxlength="254" :disabled="busy" required/>
        </el-form-item>
        <el-form-item label="当前密码">
          <el-input v-model="password" type="password" autocomplete="current-password" :disabled="busy" show-password required/>
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="busy" :disabled="!email.trim()||!password">发送验证码</el-button>
        <el-button :disabled="busy" @click="close">关闭</el-button>
      </el-form>
      <el-form v-else label-position="top" @submit.prevent="confirm">
        <p>验证邮箱：{{ target }}</p>
        <p class="hint small" role="status">{{ deliveryText[challenge.state] }} 验证码十分钟内有效。</p>
        <el-form-item label="邮箱验证码">
          <el-input v-model="code" inputmode="numeric" autocomplete="one-time-code" :maxlength="6" :disabled="busy"/>
        </el-form-item>
        <div class="email-actions">
          <el-button type="primary" native-type="submit" :loading="busy" :disabled="challenge.state==='EXPIRED'||!/^\d{6}$/.test(code)">确认更换并重新登录</el-button>
          <el-button :disabled="busy" @click="refresh">查询投递状态</el-button>
          <el-button :disabled="busy" @click="reset">重新申请</el-button>
          <el-button :disabled="busy" @click="close">关闭</el-button>
        </div>
      </el-form>
    </template>
  </section>
</template>

<style scoped>
.email-change{margin-top:24px;padding-top:16px;border-top:1px solid var(--border)}
.email-change :deep(.el-form){max-width:460px}
.email-actions{display:flex;flex-wrap:wrap;gap:8px}
.email-actions :deep(.el-button){margin-left:0}
</style>
