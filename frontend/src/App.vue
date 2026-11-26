<script setup lang="ts">
import PageState from './components/PageState.vue'
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {useAuthStore} from './stores/auth'
import {useWatchlistStore} from './stores/watchlist'
import {useInboxStore} from './stores/inbox'
const inbox=useInboxStore()
const auth=useAuthStore(),watchlist=useWatchlistStore(),router=useRouter(),error=ref(''),kind=ref<'error'|'expired'>('error'),recovery=ref<'account'|'page'|null>(null)
watch(()=>auth.profile?.userId,owner=>{if(owner)void inbox.refresh()},{immediate:true})
watch(()=>auth.profile?.userId,owner=>{if(owner&&(kind.value==='expired'||recovery.value==='account'))dismissNotice()})
function pageUnavailable(){kind.value='error';recovery.value='page';error.value='页面资源加载失败，请刷新页面后重试；已保存的数据保留。'}
onMounted(()=>window.addEventListener('aistock:page-unavailable',pageUnavailable))
onUnmounted(()=>window.removeEventListener('aistock:page-unavailable',pageUnavailable))
async function checkAccount(){
 try{await auth.refresh();if(recovery.value==='account')dismissNotice()}
 catch{kind.value='error';recovery.value='account';error.value='账号服务暂时无法连接，请稍后重试'}
}
function dismissNotice(){error.value='';recovery.value=null}
function runRecovery(){if(recovery.value==='page')window.location.reload();else if(recovery.value==='account')void checkAccount()}
onMounted(()=>void checkAccount())
function expired(){if(auth.profile){auth.forget();watchlist.clear();kind.value='expired';recovery.value=null;error.value='登录已失效，请重新登录';void router.push({path:'/login',query:{returnTo:router.currentRoute.value.fullPath}})}}
onMounted(()=>window.addEventListener('aistock:auth-required',expired))
onUnmounted(()=>window.removeEventListener('aistock:auth-required',expired))
async function logout(){try{await auth.signOut();watchlist.clear();await router.push('/login')}catch(e){kind.value='error';recovery.value=null;error.value=(e as Error).message}}
</script>
<template>
  <header class="topbar">
    <div class="topbar-heading">
      <router-link class="brand" to="/">股票时序分析<span>辅助决策系统</span></router-link>
      <div class="account-links" aria-label="账户操作">
        <template v-if="auth.profile">
          <router-link to="/profile" class="profile-link">
            <span class="profile-avatar" :data-style="auth.profile.avatarStyle" aria-hidden="true">{{ Array.from(auth.profile.nickname)[0] || '我' }}</span>
            个人中心
          </router-link>
          <el-button text @click="logout">退出登录</el-button>
        </template>
        <template v-else>
          <router-link to="/login">登录</router-link>
          <router-link to="/register">注册</router-link>
        </template>
      </div>
    </div>
    <div v-if="auth.profile" class="topbar-navigation">
      <nav aria-label="主导航">
        <router-link to="/">行情首页</router-link>
        <router-link to="/watchlist">我的自选</router-link>
        <router-link to="/portfolio">我的持仓</router-link>
        <router-link to="/comparisons">股票对比</router-link>
        <router-link to="/predictions">预测记录</router-link>
        <router-link to="/training">训练工作台</router-link>
        <router-link to="/alerts">预警规则</router-link>
        <router-link to="/messages">
          消息中心<span v-if="inbox.count!==null&&inbox.count>0" class="unread-badge" :aria-label="inbox.count+'条未读消息'">{{ inbox.count>99?'99+':inbox.count }}</span>
        </router-link>
      </nav>
      <details v-if="auth.isAdmin" class="admin-menu">
        <summary>管理后台</summary>
        <nav aria-label="管理导航">
          <router-link to="/admin/users">用户管理</router-link>
          <router-link to="/admin/models">模型管理</router-link>
          <router-link to="/admin/tasks">任务管理</router-link>
          <router-link to="/admin/data">行情数据</router-link>
          <router-link to="/admin/statistics">运营统计</router-link>
        </nav>
      </details>
    </div>
  </header>
  <PageState v-if="error" :kind="kind" :message="error" :action-label="recovery==='page'?'刷新页面':recovery==='account'?'重试连接':undefined" dismissible @action="runRecovery" @dismiss="dismissNotice"/>
  <router-view/>
</template>
<style>
.profile-link{display:inline-flex;align-items:center;gap:6px;min-height:44px}.profile-avatar{width:28px;height:28px;display:inline-grid;place-items:center;flex:none;border-radius:50%;background:#e3ebf7;color:#213c67;font-weight:700}.profile-avatar[data-style=ocean]{background:#d7ebff;color:#123d66}.profile-avatar[data-style=jade]{background:#d7f3e4;color:#154b36}.profile-avatar[data-style=plum]{background:#eee2f6;color:#532a66}.profile-avatar[data-style=amber]{background:#fff0cc;color:#5f3a0a}
.unread-badge{display:inline-block;background:var(--accent);color:#fff;font-size:12px;line-height:20px;min-width:20px;text-align:center;border-radius:10px;padding:0 5px;margin-left:6px}
:root{--surface:#fff;--canvas:#f4f7fb;--ink:#17253d;--muted:#526179;--border:#dbe3ed;--accent:#315ed0;--el-color-primary:var(--accent)}
*{box-sizing:border-box}body{margin:0;background:var(--canvas);color:var(--ink);font:16px/1.6 "Microsoft YaHei",system-ui,sans-serif}a{color:var(--accent);text-decoration:none}a:focus-visible,button:focus-visible{outline:3px solid var(--accent);outline-offset:3px}
.topbar{background:var(--surface);border-bottom:1px solid var(--border);padding:12px max(24px,calc((100vw - 1180px)/2)) 8px}
.topbar-heading,.topbar-navigation,.account-links,.topbar nav{display:flex;align-items:center;flex-wrap:wrap;gap:4px 12px}
.topbar-heading,.topbar-navigation{justify-content:space-between}
.topbar-navigation{border-top:1px solid var(--border);margin-top:8px;padding-top:4px}
.brand{font-size:20px;font-weight:700;color:var(--ink)}
.brand span{display:block;font-size:12px;font-weight:400;color:var(--muted)}
.topbar nav a,.account-links>a,.admin-menu summary{min-height:44px;align-content:center;border-radius:6px;padding:4px 8px}
.topbar nav a.router-link-exact-active,.admin-menu:has(a.router-link-exact-active) summary{background:#e9effb;color:var(--ink);font-weight:700}
.admin-menu{position:relative;color:var(--accent)}
.admin-menu summary{cursor:pointer}
.admin-menu summary:focus-visible{outline:3px solid var(--accent);outline-offset:2px}
.admin-menu nav{position:absolute;right:0;z-index:10;display:grid;min-width:140px;background:var(--surface);border:1px solid var(--border);border-radius:10px;padding:6px;box-shadow:0 8px 20px #17253d1a}
.account-wrap{padding:48px 24px 72px}.account-card{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:32px;margin:auto}.narrow{max-width:480px}.wide{max-width:1100px}.account-card h1{font-size:28px;line-height:1.3;margin:6px 0 12px}.account-card h2{font-size:20px;margin:0 0 20px}.eyebrow{font-size:13px;font-weight:600;color:var(--accent);margin:0}.hint{color:var(--muted);margin-bottom:24px}.small{font-size:13px}.full{width:100%}.footnote{text-align:center;color:var(--muted);margin:24px 0 0}.inline-field{display:flex;gap:12px;width:100%}.account-card .el-alert{margin-bottom:18px}.el-form-item{margin-bottom:22px}.el-input__wrapper{min-height:44px}.el-button{min-height:40px}.profile-grid{display:grid;grid-template-columns:1fr 1fr;gap:64px}.filter-row{display:flex;align-items:center;flex-wrap:wrap;gap:16px}.filter-row .el-form-item{margin:0}.el-pagination{margin-top:24px}
@media(max-width:700px){.topbar{padding:10px 16px}.topbar nav{gap:2px 4px}.topbar nav a{padding-inline:7px}.admin-menu nav{position:static;display:flex;box-shadow:none}.admin-menu[open]{width:100%}.account-wrap{padding:24px 16px}.account-card{padding:24px 18px}.profile-grid{grid-template-columns:1fr;gap:40px}.account-card h1{font-size:24px}.filter-row{align-items:stretch;flex-direction:column}.inline-field{gap:8px}}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important;transition:none!important}}
</style>
