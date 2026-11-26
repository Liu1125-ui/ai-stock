<script setup lang="ts">
import SavedResultChanges from '../../components/SavedResultChanges.vue'
import {predictionChanges} from '../../components/savedResultChanges'
import PageState from '../../components/PageState.vue'
import {onMounted,onUnmounted,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {predictionHistory,prediction,hidePrediction,predictionFailure,type PredictionHistory,type PredictionRecord} from '../../api/predictions'
import {summaries} from '../../api/market'
import PredictionResult from '../prediction/PredictionResult.vue'
import FusionPreviewPanel from './FusionPreviewPanel.vue'
const route=useRoute()
const auth=useAuthStore()
const history=ref<PredictionHistory|null>(null)
const stockNames=ref(new Map<string,string>())
const detail=ref<PredictionRecord|null>(null)
const page=ref(1)
const error=ref('')
const detailError=ref('')
const notice=ref('')
const busy=ref(false)
let generation=0,listRequest=0,selection=0
let selectedId=''
async function loadStockNames(items:PredictionRecord[],owner:number,request:number){
 const ids=[...new Set(items.map(record=>record.instrumentId))]
 if(!ids.length)return
 try{
  const results=await summaries(ids)
  if(owner!==generation||request!==listRequest)return
  const names=new Map<string,string>()
  for(const stock of results){
   const name=stock.displayName?.trim()
   if(stock.displayNameVerified&&name&&name!==stock.instrumentId)names.set(stock.instrumentId,name)
  }
  stockNames.value=names
 }catch{
  // Stock labels are optional; history and its saved predictions remain usable.
 }
}
async function load(){
 if(!auth.profile)return
 const owner=generation,request=++listRequest
 busy.value=true
 error.value=''
 try{
  const result=await predictionHistory(page.value)
  if(owner===generation&&request===listRequest){
   history.value=result
   void loadStockNames(result.items,owner,request)
  }
 }catch(e){
  if(owner===generation&&request===listRequest)error.value='读取预测记录失败。'+(e as Error).message
 }finally{if(owner===generation&&request===listRequest)busy.value=false}
}
async function view(id:string){
 if(!auth.profile)return
 selectedId=id
 const owner=generation,chosen=++selection
 if(detail.value?.predictionId!==id)detail.value=null
 detailError.value=''
 try{
  const result=await prediction(id)
  if(owner===generation&&chosen===selection)detail.value=result
 }catch(e){
  if(owner===generation&&chosen===selection)detailError.value='读取预测详情失败。'+(e as Error).message
 }
}
async function hide(id:string){
 if(!window.confirm('从本人列表隐藏此记录？已保存的预测依据仍会保留。'))return
 const owner=generation
 error.value=''
 try{
  notice.value=''
  await hidePrediction(id)
  if(owner!==generation)return
  notice.value='已从本人列表隐藏。预测依据仍随原记录保存。'
  if(selectedId===id){selection++;selectedId='';detail.value=null}
  await load()
 }catch(e){if(owner===generation)error.value=(e as Error).message}
}
function linkedId(){
 const id=route.query.predictionId
 return typeof id==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)?id:null
}
function loadLinked(){
 const id=selectedId||linkedId()
 void load()
 if(id)void view(id)
}
onMounted(loadLinked)
watch(page,()=>void load())
watch(()=>auth.profile?.userId,owner=>{
 generation++
 detail.value=null
 history.value=null
 stockNames.value=new Map()
 notice.value=''
 detailError.value=''
 error.value=''
 busy.value=false
 if(page.value!==1)page.value=1
 else if(owner)void load()
},{flush:'sync'})
onUnmounted(()=>{generation++})
</script>
<template><main class="account-wrap"><section class="account-card wide"><p class="eyebrow">本人记录</p><h1>预测历史</h1><p class="hint">数值与生成时版本保持原样；隐藏只影响列表。成功记录的详情可查看真实行情验证进度及历史版本。</p><PageState v-if="error" kind="error" :message="error"/><p v-if="notice" role="status">{{notice}}</p><el-button :loading="busy" @click="loadLinked">刷新记录</el-button><PageState v-if="busy" kind="loading" message="正在加载本人记录…"/><PageState v-else-if="history&&!history.items.length" kind="empty" message="当前没有可见预测记录。可从行情分析页发起五日预测。"/><ul v-else-if="history" class="history-list"><li v-for="r in history.items" :key="r.predictionId"><div><strong>{{stockNames.get(r.instrumentId) || '股票名称暂不可用'}} · {{r.horizon}}交易日</strong><p>{{r.createdAt}} · {{r.state==='SUCCEEDED'?'数值已生成':r.state==='RUNNING'?'正在处理':predictionFailure(r.errorCode)}}</p></div><div><el-button @click="view(r.predictionId)">查看详情</el-button><el-button @click="hide(r.predictionId)">隐藏</el-button></div></li></ul><el-pagination v-if="history&&history.total>history.size" v-model:current-page="page" :page-size="history.size" :total="history.total" layout="prev,pager,next"/><FusionPreviewPanel v-if="history?.items.length" :records="history.items"/><PageState v-if="detailError" kind="error" :message="detailError"/><SavedResultChanges :owner="auth.profile?.userId" :snapshot="predictionChanges(detail)"/><PredictionResult v-if="detail" :key="detail.predictionId" :record="detail" historical/></section></main></template>
<style scoped>.history-list{padding:0;list-style:none}.history-list li{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:18px 0;border-bottom:1px solid var(--border)}.history-list p{margin:6px 0;color:var(--muted)}@media(max-width:680px){.history-list li{align-items:flex-start;flex-direction:column}}</style>
