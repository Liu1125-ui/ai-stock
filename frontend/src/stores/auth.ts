import {clearMarketReviewOwner} from './marketReviewRecovery'
import {clearOwner as clearMarketRefreshOwner} from '../pages/admin/data/marketRefreshPending'
import {clearTaskActionPendingOwner} from '../pages/admin/tasks/taskActionPending'
import {clearOwner as clearComparisonAiRecovery} from '../pages/comparison/comparisonAiState'
import {clearPending as clearComparisonRecovery} from '../pages/comparison/comparisonState'
import {clearQuestionRecovery} from './questionRecovery'
import {defineStore} from 'pinia'
import {computed,ref,watch} from 'vue'
import * as api from '../api/auth'
export const useAuthStore=defineStore('auth',()=>{
 const profile=ref<api.Profile|null>(null);const loaded=ref(false)
 watch(()=>profile.value?.userId,(next,previous)=>{if(next!==previous){if(previous){clearMarketReviewOwner(previous);clearTaskActionPendingOwner(previous);clearMarketRefreshOwner(previous);clearQuestionRecovery(previous);clearComparisonRecovery(previous);clearComparisonAiRecovery(previous)}api.invalidateAuthenticatedRequests()}},{flush:'sync'})
 watch(()=>profile.value?.role,(next,previous)=>{if(previous==='ADMIN'&&next!=='ADMIN'&&profile.value?.userId){clearMarketReviewOwner(profile.value.userId);clearTaskActionPendingOwner(profile.value.userId);clearMarketRefreshOwner(profile.value.userId)}},{flush:'sync'})
 const isAdmin=computed(()=>profile.value?.role==='ADMIN')
 let refreshPending:Promise<void>|undefined,identityEpoch=0
 function cancelRefresh(){identityEpoch++;refreshPending=undefined}
 function refresh(){
  if(refreshPending)return refreshPending
  const epoch=identityEpoch
  const pending=api.me().then(value=>{if(epoch!==identityEpoch)return;profile.value=value;loaded.value=true}).catch(error=>{if(epoch!==identityEpoch)return;if((error as {status?:number}).status===401){profile.value=null;loaded.value=true}else{loaded.value=false;throw error}}).finally(()=>{if(refreshPending===pending)refreshPending=undefined})
  refreshPending=pending;return pending
 }
 async function signIn(email:string,password:string){cancelRefresh();await api.login(email,password);cancelRefresh();await refresh()}
 async function signOut(){cancelRefresh();await api.logout();cancelRefresh();profile.value=null;loaded.value=true}
 function forget(){cancelRefresh();profile.value=null;loaded.value=false}
 return {profile,loaded,isAdmin,refresh,signIn,signOut,forget}
})
