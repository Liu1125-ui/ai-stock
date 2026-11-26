<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {ElMessageBox} from 'element-plus'
import {useAuthStore} from '../../stores/auth'
import {listUsers,setUserStatus,setExperimentGrant} from '../../api/userAdmin'
import type {Profile} from '../../api/auth'
import {canManageUser} from './policy'
const auth=useAuthStore(),q=ref(''),state=ref(''),page=ref(1),total=ref(0),users=ref<Profile[]>([]),busy=ref(false),error=ref('')
async function load(){busy.value=true;error.value='';try{const result=await listUsers(q.value,state.value,page.value);users.value=result.items;total.value=result.total}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
async function update(user:Profile,grant:boolean){try{await ElMessageBox.confirm(grant?`确认${user.experimentAllowed?'撤销':'授予'} ${user.email} 的实验权限？`:`确认${user.status==='ENABLED'?'禁用':'启用'} ${user.email}？禁用会撤销其登录会话。`,'确认账号操作',{confirmButtonText:'确认',cancelButtonText:'取消',type:'warning'});}catch{return}
 busy.value=true;error.value='';try{await(grant?setExperimentGrant(user):setUserStatus(user));await load()}catch(e){const message=(e as Error).message;if((e as {status?:number}).status===409)await load();error.value=message;busy.value=false}}
function search(){page.value=1;void load()}
onMounted(()=>void load())
</script>
<template><main class="account-wrap"><section class="account-card wide"><p class="eyebrow">管理员工作区</p><h1>用户管理</h1><p class="hint">管理普通账号状态与实验权限。管理员账号由受控初始化命令预置。</p><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/>
<el-form class="filter-row" @submit.prevent="search"><el-form-item label="邮箱或昵称"><el-input v-model="q" clearable :disabled="busy"/></el-form-item><el-form-item label="状态"><el-select v-model="state" :disabled="busy" style="width:150px"><el-option label="全部" value=""/><el-option label="启用" value="ENABLED"/><el-option label="禁用" value="DISABLED"/></el-select></el-form-item><el-button type="primary" native-type="submit" :loading="busy">查询</el-button></el-form>
<el-table :data="users" v-loading="busy" empty-text="没有匹配的普通账号"><el-table-column prop="email" label="邮箱" min-width="220"/><el-table-column prop="nickname" label="昵称" min-width="120"/><el-table-column label="状态" width="90"><template #default="scope">{{scope.row.status==='ENABLED'?'启用':'禁用'}}</template></el-table-column><el-table-column label="实验权限" width="100"><template #default="scope">{{scope.row.experimentAllowed?'已授予':'未授予'}}</template></el-table-column><el-table-column label="操作" min-width="240"><template #default="scope"><template v-if="canManageUser(auth.profile?.role||'',scope.row.role)"><el-button :disabled="busy" @click="update(scope.row,false)">{{scope.row.status==='ENABLED'?'禁用':'启用'}}</el-button><el-button :disabled="busy" @click="update(scope.row,true)">{{scope.row.experimentAllowed?'撤销实验权限':'授予实验权限'}}</el-button></template></template></el-table-column></el-table>
<el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="prev,pager,next,total" :disabled="busy" @current-change="load"/></section></main></template>
