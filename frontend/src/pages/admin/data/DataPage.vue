<script setup lang="ts">
import {ref,reactive,computed,watch,onUnmounted,nextTick} from 'vue'
import {useAuthStore} from '../../../stores/auth'
import {clearMarketReviewOwner} from '../../../stores/marketReviewRecovery'
import * as api from '../../../api/adminMarketData'
import type {ListFilter,HeadPage,HeadRow,VersionPage,VersionSummary,VersionDetail,DetailFilter} from '../../../api/adminMarketData'
import {defaultWindow,validateWindow,label} from './marketDataPresentation'
import MarketHeadList from './MarketHeadList.vue'
import MarketRefreshPanel from './MarketRefreshPanel.vue'
import VersionReferencesPanel from './VersionReferencesPanel.vue'
import VersionReviewPanel from './VersionReviewPanel.vue'
import MarketVersionPanel from './MarketVersionPanel.vue'
import MarketVersionDetail from './MarketVersionDetail.vue'
import MarketLifecyclePanel from './MarketLifecyclePanel.vue'
const detailHeading=ref<HTMLElement|null>(null)
const auth=useAuthStore(),denied=ref(false),queryError=ref(''),dateError=ref('')
const draft=reactive({q:'',provider:'',headState:''}),applied=ref<ListFilter>({page:1,size:20})
const dateDraft=reactive(defaultWindow()),dateApplied=ref<DetailFilter>(defaultWindow())
const heads=reactive({data:null as HeadPage|null,busy:false,error:'',stale:false})
const versions=reactive({data:null as VersionPage|null,busy:false,error:'',stale:false})
const detail=reactive({data:null as VersionDetail|null,busy:false,error:'',stale:false})
const selectedStock=ref(''),selectedVersion=ref(''),historyPages=ref<VersionPage[]>([]),historyPage=ref(0)
let alive=true,epoch=0,headEpoch=0,historyEpoch=0,detailEpoch=0,detailPending=''
const canRead=computed(()=>auth.profile?.role==='ADMIN'&&!denied.value)
function identity(){return auth.profile?.userId+':'+auth.profile?.role}
function clearDetail(){detailEpoch++;detailPending='';selectedVersion.value='';Object.assign(detail,{data:null,busy:false,error:'',stale:false})}
function clearVersions(){historyEpoch++;selectedStock.value='';historyPages.value=[];historyPage.value=0;Object.assign(versions,{data:null,busy:false,error:'',stale:false});clearDetail()}
function clear(){epoch++;headEpoch++;Object.assign(heads,{data:null,busy:false,error:'',stale:false});clearVersions()}
function valid(n:number,owner:string){return alive&&n===epoch&&owner===identity()&&canRead.value}
function errorOf(error:unknown){return (error as {message?:string}|null)?.message||'本区暂不可用，请稍后刷新'}
function refreshAccessDenied(){if(auth.profile?.userId)clearMarketReviewOwner(auth.profile.userId);denied.value=true;clear();void Promise.resolve().then(()=>auth.refresh()).catch(()=>{})}
function deniedBy(error:unknown,n:number,owner:string){if(!valid(n,owner))return true;const status=(error as {status?:number}|null)?.status;if(status===401||status===403){if(auth.profile?.userId)clearMarketReviewOwner(auth.profile.userId);denied.value=true;clear();void Promise.resolve().then(()=>auth.refresh()).catch(()=>{});return true}return false}
async function loadHeads(page=applied.value.page,newFilter?:ListFilter){
 if(!canRead.value||heads.busy)return
 const previous=JSON.stringify(applied.value),filter=newFilter||{...applied.value,page};applied.value={...filter}
 if(previous!==JSON.stringify(filter))Object.assign(heads,{data:null,stale:false})
 const n=epoch,owner=identity(),request=++headEpoch;heads.busy=true;heads.error=''
 try{const value=await api.getHeads(filter);if(valid(n,owner)&&request===headEpoch){heads.data=value;heads.stale=false}}
 catch(error){if(request!==headEpoch||deniedBy(error,n,owner))return;heads.error=errorOf(error);heads.stale=heads.data!==null}
 finally{if(n===epoch&&request===headEpoch)heads.busy=false}
}
function applyFilter(){if(heads.busy||!canRead.value)return;const q=draft.q.trim();if([...q].length>64||/[\u0000-\u001f\u007f]/.test(q)){queryError.value='股票代码或名称最多64个字符，不接受控制字符';return}queryError.value='';const filter:ListFilter={q:q||null,provider:draft.provider||null,headState:draft.headState||null,page:1,size:20};if(q!==(applied.value.q||'')||draft.provider!==(applied.value.provider||'')||draft.headState!==(applied.value.headState||''))clearVersions();void loadHeads(1,filter)}
function selectStock(row:HeadRow){if(!canRead.value)return;if(row.instrumentId===selectedStock.value&&versions.busy)return;clearVersions();selectedStock.value=row.instrumentId;void loadHistory(null,true)}
async function loadHistory(cursor:string|null,reset=false){
 const stock=selectedStock.value;if(!canRead.value||!stock||versions.busy)return
 const n=epoch,owner=identity(),request=++historyEpoch,provider=applied.value.provider||null
 versions.busy=true;versions.error='';const prior=versions.data
 if(cursor!==null)versions.data=null
 try{const result=await api.getVersions(stock,{provider,cursor,size:20});if(!valid(n,owner)||request!==historyEpoch||stock!==selectedStock.value)return
  if(reset){historyPages.value=[result];historyPage.value=0}else{historyPages.value=historyPages.value.slice(0,historyPage.value+1);historyPages.value.push(result);historyPage.value++}
  versions.data=result;versions.stale=false
 }catch(error){if(request!==historyEpoch||deniedBy(error,n,owner))return;versions.error=(cursor!==null?'后一页未能加载，仍显示已读页。':'')+errorOf(error);versions.data=prior;versions.stale=versions.data!==null}
 finally{if(n===epoch&&request===historyEpoch)versions.busy=false}
}
function previousHistory(){if(!versions.busy&&historyPage.value>0){historyPage.value--;versions.data=historyPages.value[historyPage.value]||null;versions.error='';versions.stale=false}}
function nextHistory(){if(versions.busy)return;const cached=historyPages.value[historyPage.value+1];if(cached){historyPage.value++;versions.data=cached;versions.error='';versions.stale=false}else if(versions.data?.nextCursor)void loadHistory(versions.data.nextCursor)}
async function selectVersion(v:VersionSummary){dateError.value='';const window=defaultWindow(),n=epoch,owner=identity();Object.assign(dateDraft,window);void loadDetail(v.versionId,window);await nextTick();if(valid(n,owner)&&selectedVersion.value===v.versionId)detailHeading.value?.focus()}
async function loadDetail(versionId:string,window:DetailFilter){
 const stock=selectedStock.value,key=stock+':'+versionId+':'+window.from+':'+window.to
 if(!canRead.value||!stock||detail.busy&&detailPending===key)return
 const same=selectedVersion.value===versionId&&JSON.stringify(dateApplied.value)===JSON.stringify(window)
 selectedVersion.value=versionId;dateApplied.value={...window};if(!same)detail.data=null
 const n=epoch,owner=identity(),request=++detailEpoch;detailPending=key;detail.busy=true;detail.error=''
 try{const value=await api.getVersionDetail(stock,versionId,window);if(valid(n,owner)&&request===detailEpoch){detail.data=value;detail.stale=false}}
 catch(error){if(request!==detailEpoch||deniedBy(error,n,owner))return;detail.error=errorOf(error);detail.stale=detail.data!==null}
 finally{if(n===epoch&&request===detailEpoch){detail.busy=false;detailPending=''}}
}
function applyDates(){dateError.value=validateWindow(dateDraft);if(!dateError.value&&selectedVersion.value)void loadDetail(selectedVersion.value,{...dateDraft})}
watch(identity,()=>{clear();denied.value=false;if(auth.profile?.role==='ADMIN')void loadHeads(1)},{immediate:true})
onUnmounted(()=>{alive=false;clear()})
</script>
<template><main class="md-page"><header><p class="eyebrow">管理员 · 行情数据</p><h1>行情覆盖与版本查询</h1><p class="hint">查看已保存数据和核验依据；选择股票后可预检、登记行情重取并查看操作记录。</p></header><p v-if="denied" role="alert">访问已失效，请确认管理员账号后重新进入。</p><p v-else-if="!canRead" role="alert">仅管理员可以访问此功能。</p><template v-else><section class="md-card" data-test="heads-section" :aria-busy="heads.busy"><h2>覆盖与来源</h2><form class="md-form" data-test="head-query" @submit.prevent="applyFilter"><label>股票代码或名称<input v-model="draft.q" name="q" maxlength="128" autocomplete="off"></label><label>来源<select v-model="draft.provider" name="provider"><option value="">全部已保存来源</option><option value="baostock">Baostock</option><option value="tushare">Tushare</option></select></label><label>当前头状态<select v-model="draft.headState" name="headState"><option value="">全部状态</option><option v-for="state in ['AVAILABLE','STAGING','REJECTED','UNAVAILABLE','NO_VERSION','REFERENCE_ERROR']" :key="state" :value="state">{{label(state)}}</option></select></label><button type="submit" :disabled="heads.busy">应用筛选</button><button type="button" data-test="head-refresh" :disabled="heads.busy" @click="loadHeads()">刷新列表</button></form><p v-if="queryError" role="alert">{{queryError}}</p><p v-if="heads.busy" role="status">正在查询覆盖列表…</p><p v-if="heads.error" role="alert">{{heads.error}}<span v-if="heads.stale"> · 尚未刷新，下方保留原查询时间。</span></p><MarketHeadList :data="heads.data" :busy="heads.busy" @select="selectStock" @previous="loadHeads(applied.page-1)" @next="loadHeads(applied.page+1)"/></section><MarketRefreshPanel v-if="selectedStock&&auth.profile" :key="auth.profile.userId+':'+selectedStock" :instrument-id="selectedStock" :actor-user-id="auth.profile.userId" @access-denied="refreshAccessDenied"/><section v-if="selectedStock" class="md-card" data-test="versions-section" :aria-busy="versions.busy"><h2>历史版本与回执 · {{selectedStock}}</h2><p v-if="versions.busy" role="status">正在查询历史记录…</p><p v-if="versions.error" role="alert">{{versions.error}}<span v-if="versions.stale"> · 尚未刷新，保留原查询记录。</span></p><button v-if="!versions.data&&!versions.busy" type="button" @click="loadHistory(null,true)">重新查询历史</button><MarketVersionPanel :data="versions.data" :busy="versions.busy" :page="historyPage" @select="selectVersion" @previous="previousHistory" @next="nextHistory" @refresh="loadHistory(null,true)"/></section><section v-if="selectedVersion" class="md-card" :aria-busy="detail.busy"><h2 ref="detailHeading" tabindex="-1" class="md-detail-heading">固定版本详情 · {{selectedStock}}</h2><VersionReferencesPanel v-if="auth.profile" :key="auth.profile.userId+':'+selectedStock+':'+selectedVersion" :instrument-id="selectedStock" :version-id="selectedVersion" :actor-user-id="auth.profile.userId" @access-denied="refreshAccessDenied"/><VersionReviewPanel v-if="auth.profile" :key="auth.profile.userId+':'+selectedStock+':'+selectedVersion" :instrument-id="selectedStock" :version-id="selectedVersion" :actor-user-id="auth.profile.userId" @access-denied="refreshAccessDenied"/><form class="md-form" @submit.prevent="applyDates"><label>诊断开始日期<input v-model="dateDraft.from" type="date" name="from"></label><label>诊断结束日期<input v-model="dateDraft.to" type="date" name="to"></label><button type="submit">应用诊断日期</button><button type="button" :disabled="detail.busy" @click="loadDetail(selectedVersion,dateApplied)">刷新本版本诊断</button></form><p v-if="dateError" role="alert">{{dateError}}</p><p v-if="detail.busy" role="status">正在核验所选版本…</p><p v-if="detail.error" role="alert">{{detail.error}}<span v-if="detail.stale"> · 尚未刷新，下方保留原查询时间。</span></p><MarketVersionDetail v-if="detail.data" :data="detail.data"/><MarketLifecyclePanel v-if="detail.data?.version.provider==='baostock'&&detail.data.version.priceBasis==='RAW'&&detail.data.version.status==='AVAILABLE'&&auth.profile" :key="auth.profile.userId+':'+selectedStock+':'+selectedVersion" :instrument-id="selectedStock" :version-id="selectedVersion" :actor-user-id="auth.profile.userId" @access-denied="refreshAccessDenied"/></section></template></main></template>
<style>
.md-page{max-width:1240px;margin:auto;padding:32px 24px 64px;min-width:0}.md-card{background:var(--surface);border:1px solid var(--border);border-radius:12px;padding:24px;margin:24px 0;min-width:0}.md-page h1{font-size:28px;margin:8px 0}.md-page h2{font-size:21px;margin:0 0 20px}.md-detail-heading{scroll-margin-top:24px}.md-page h3{font-size:16px}.md-form,.md-actions{display:flex;flex-wrap:wrap;align-items:end;gap:12px;margin:16px 0}.md-form label{display:flex;flex-direction:column;gap:6px}.md-page input,.md-page select,.md-page button{font:inherit;min-height:44px;border:1px solid var(--border);border-radius:8px;background:var(--surface);color:var(--ink);padding:8px 12px;max-width:100%}.md-page button{cursor:pointer;color:var(--accent)}.md-page button:disabled{opacity:.55;cursor:default}.md-page input:focus-visible,.md-page select:focus-visible,.md-page summary:focus-visible,.md-table:focus-visible{outline:3px solid var(--accent);outline-offset:3px}.md-table{position:relative;max-width:100%;overflow-x:auto;margin:20px 0;border:1px solid var(--border);border-radius:8px}.md-table table{border-collapse:collapse;min-width:880px;width:100%;font-size:14px}.md-table th,.md-table td{text-align:left;padding:14px;border-bottom:1px solid var(--border);vertical-align:top}.md-table th{background:var(--canvas);font-weight:600}.md-table small,.md-muted{color:var(--muted)}.md-page details{margin:16px 0;overflow-wrap:anywhere}.md-page summary{cursor:pointer;min-height:44px;padding:8px 0;font-weight:600}.md-page dl{display:grid;grid-template-columns:minmax(110px,1fr) minmax(0,3fr);gap:8px 20px;overflow-wrap:anywhere}.md-page dd{margin:0}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@media(max-width:600px){.md-page{padding:24px 12px 48px}.md-card{padding:20px 16px}.md-form{align-items:stretch}.md-form label{width:100%}.md-page h1{font-size:24px}.md-page dl{grid-template-columns:minmax(100px,1fr) minmax(0,1fr)}}
</style>
