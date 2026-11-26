<script setup lang="ts">
import PageState from '../../components/PageState.vue'
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {watchlistPage} from '../../router/listContext'
import {useListScroll} from '../../router/listScroll'
import {summaries,type Summary} from '../../api/market'
import {useWatchlistStore} from '../../stores/watchlist'
import WatchlistCards from '../../components/market/WatchlistCards.vue'
const route=useRoute(),router=useRouter(),restoreListScroll=useListScroll()
const store=useWatchlistStore(),quotes=ref<Record<string,Summary>>({}),marketError=ref(''),error=ref(''),busyId=ref('');let sequence=0
async function load(page=1){const turn=++sequence;error.value='';marketError.value='';quotes.value={};try{await store.load(page)}catch(e){if(turn===sequence)error.value=(e as Error).message;return}if(turn!==sequence)return;try{const data=store.items.length?await summaries(store.items.map(x=>x.instrumentId)):[];if(turn===sequence)quotes.value=Object.fromEntries(data.map(x=>[x.instrumentId,x]))}catch{if(turn===sequence)marketError.value='行情暂不可用'}}
async function remove(id:string){busyId.value=id;error.value='';try{await store.remove(id);sequence++;delete quotes.value[id]}catch(e){error.value=(e as Error).message}finally{busyId.value=''}}
function changePage(page:number){
 void router.replace({path:'/watchlist',query:page>1?{page:String(page)}:{}})
}
watch(()=>watchlistPage(route.query.page),page=>void load(page))
onMounted(()=>{
 const path=route.fullPath
 void load(watchlistPage(route.query.page)).then(()=>restoreListScroll(path))
})
onUnmounted(()=>{sequence++})
</script>
<template><main class="market-page"><p class="eyebrow">我的关注</p><h1>自选股</h1><p class="hint">这份列表仅属于你。行情加载失败时，仍可查看或移除已保存的股票。</p><section class="market-panel"><div class="heading"><h2>已保存 {{store.total}} 只</h2><div class="heading"><router-link :to="{path:'/comparisons',query:{source:'watchlist'}}">选择股票对比</router-link><router-link to="/">搜索更多股票</router-link><el-button :loading="store.loading" @click="load(store.page)">刷新行情状态</el-button></div></div><PageState v-if="error" kind="error" :message="'自选股操作未完成。'+error" action-label="重新读取自选股" @action="load(store.page)"/><WatchlistCards :items="store.items" :return-to="route.fullPath" :list-loading="store.loading" :list-loaded="!error" :summaries="quotes" :market-error="marketError" :busy-id="busyId" @remove="remove"/><el-pagination v-if="store.total>20" :current-page="store.page" :page-size="20" :total="store.total" layout="prev,pager,next" @current-change="changePage"/></section></main></template>
<style scoped>.market-page{max-width:1100px;margin:auto;padding:36px 24px 80px}.market-page h1{font-size:32px;margin:0}.market-panel{background:white;border:1px solid var(--border);border-radius:14px;padding:24px;margin-top:24px}.heading{display:flex;justify-content:space-between;align-items:center}.heading h2{font-size:20px}@media(max-width:620px){.market-page{padding:24px 16px}.market-panel{padding:18px}}</style>
