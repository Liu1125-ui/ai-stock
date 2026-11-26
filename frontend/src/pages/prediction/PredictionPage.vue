<script setup lang="ts">
import PageState from '../../components/PageState.vue'
import {computed,onMounted,onUnmounted,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {analysisReturn,listReturn} from '../../router/listContext'
import {ApiError} from '../../api/auth'
import {instrument,type Summary} from '../../api/market'
import {useAuthStore} from '../../stores/auth'
import {publishedModels,supportsFiveDayPrediction,createPrediction,prediction,type PublishedModel,type PredictionInput,type PredictionRecord} from '../../api/predictions'
import PredictionResult from './PredictionResult.vue'

const route=useRoute()
const auth=useAuthStore()
const models=ref<PublishedModel[]>([])
const market=ref<Summary|null>(null)
const selected=ref('')
const error=ref('')
const catalogError=ref('')
const marketError=ref('')
const busy=ref(false)
const loading=ref(false)
const record=ref<PredictionRecord|null>(null)
const pending=ref<PredictionInput|null>(null)
const pendingId=ref<string|null>(null)

const stock=computed(()=>String(route.params.id||''))
const quoteOnly=computed(()=>stock.value==='HK00700')
const owner=computed(()=>auth.profile?.userId||'')
const supported=computed(()=>models.value.filter(model=>supportsFiveDayPrediction(model,stock.value)))
const selectedModel=computed(()=>supported.value.find(model=>model.modelId===selected.value))
const marketBlocked=computed(()=>market.value?.state==='NO_DATA'||market.value?.state==='UNAVAILABLE')
const canSubmit=computed(()=>!quoteOnly.value&&!!owner.value&&!busy.value&&(!!pending.value||!loading.value&&!marketBlocked.value&&!!selectedModel.value))
const returnTarget=computed(()=>listReturn(route.query.from)||analysisReturn(route.query.from,stock.value)||{path:'/market/'+stock.value})
const marketNote=computed(()=>{
 if(marketError.value)return '行情摘要读取失败，发起后服务端会核对实际输入。'
 const summary=market.value
 if(!summary)return '已发布行情待核对。'
 if(summary.state==='NO_DATA')return '暂无已发布行情，请等待行情更新后重新检查。'
 if(summary.state==='UNAVAILABLE')return '已发布行情暂不可用，请稍后重新检查。'
 if(summary.freshness==='FRESH')return '已有日线，近期覆盖已核验。'
 if(summary.freshness==='STALE')return '行情摘要显示缺少预期交易日，服务端会核对实际输入窗口。'
 return '交易日历覆盖待确认，服务端会核对实际输入窗口。'
})

let turn=0
let timer:ReturnType<typeof setTimeout>|undefined
let inFlight:{id:string;token:number;request:Promise<void>}|undefined
function storageKey(){return ['aistock','prediction-request',owner.value,stock.value].join(':')}
function remember(){
 if(!owner.value)return
 try{
  if(pending.value)sessionStorage.setItem(storageKey(),JSON.stringify({...pending.value,predictionId:pendingId.value}))
  else sessionStorage.removeItem(storageKey())
 }catch{}
}
function restore(){
 if(!owner.value)return null
 try{
  const raw=sessionStorage.getItem(storageKey())
  if(!raw)return null
  const saved=JSON.parse(raw) as PredictionInput & {predictionId?:string}
  if(saved.instrumentId!==stock.value||saved.horizon!==5||typeof saved.operationId!=='string'||!/^[a-f0-9-]{36}$/.test(saved.operationId)||typeof saved.modelId!=='string')return null
  pendingId.value=typeof saved.predictionId==='string'&&/^[a-f0-9-]{36}$/i.test(saved.predictionId)?saved.predictionId:null
  return {operationId:saved.operationId,modelId:saved.modelId,instrumentId:saved.instrumentId,horizon:5}
 }catch{return null}
}
async function loadChecks(){
 const token=turn
 loading.value=true
 catalogError.value=''
 marketError.value=''
 const [catalog,quote]=await Promise.allSettled([publishedModels(),instrument(stock.value)])
 if(token!==turn)return
 if(catalog.status==='fulfilled'){
  models.value=catalog.value.items
  selected.value=pending.value?.modelId||selectedModel.value?.modelId||supported.value.find(model=>model.recommended)?.modelId||supported.value[0]?.modelId||''
 }else{
  models.value=[]
  catalogError.value=(catalog.reason as Error).message
 }
 if(quote.status==='fulfilled')market.value=quote.value.summary
 else{
  market.value=null
  marketError.value=(quote.reason as Error).message
 }
 loading.value=false
}
function load(){
 const token=++turn
 clearTimeout(timer)
 busy.value=false
 record.value=null
 error.value=''
 models.value=[]
 market.value=null
 selected.value=''
 pendingId.value=null
 pending.value=null
 loading.value=false
 if(quoteOnly.value)return
 pending.value=restore()
 if(pendingId.value)void poll(pendingId.value,token)
 void loadChecks()
}
function follow(value:PredictionRecord,token:number){
 if(token!==turn)return
 clearTimeout(timer)
 record.value=value
 error.value=''
 if(value.state==='RUNNING'){
  pendingId.value=value.predictionId
  remember()
  timer=setTimeout(()=>void poll(value.predictionId,token),3000)
 }else{
  pending.value=null
  pendingId.value=null
  remember()
 }
}
function poll(id:string,token:number){
 if(inFlight?.id===id&&inFlight.token===token)return inFlight.request
 const request=prediction(id).then(value=>follow(value,token)).catch((cause:Error)=>{
  if(token===turn)error.value='查询原预测状态失败。'+cause.message
 }).finally(()=>{if(inFlight?.request===request)inFlight=undefined})
 inFlight={id,token,request}
 return request
}

async function submit(){
 if(!canSubmit.value)return
 const token=turn
 const originalId=pendingId.value
 clearTimeout(timer)
 busy.value=true
 error.value=''
 try{
  if(originalId){
   await poll(originalId,token)
   return
  }
  record.value=null
  if(!pending.value){
   pending.value={operationId:crypto.randomUUID(),modelId:selected.value,instrumentId:stock.value,horizon:5}
   remember()
  }
  follow(await createPrediction({...pending.value}),token)
 }catch(cause){
  if(token!==turn)return
  if(cause instanceof ApiError&&[400,404,409,413,422].includes(cause.status)){
   pending.value=null
   remember()
   error.value=cause.message
  }else error.value=(cause as Error).message+'。结果尚未确认，重试会复用原请求编号。'
 }finally{if(token===turn)busy.value=false}
}

onMounted(load)
watch([stock,owner],load)
onUnmounted(()=>{turn++;clearTimeout(timer)})
</script>

<template>
  <main class="account-wrap">
    <section class="account-card wide">
      <router-link :to="returnTarget">{{ returnTarget.path==='/watchlist'?'← 返回自选股':returnTarget.path==='/'?'← 返回行情':'← 返回行情分析' }}</router-link>
      <h1>{{ quoteOnly ? '腾讯控股' : stock }} · 五交易日预测</h1>
      <template v-if="quoteOnly">
        <PageState kind="empty" message="腾讯控股暂不支持五日预测。当前可查看盘中报价，五日预测需另外验证港股日线和模型。"/>
        <router-link class="quote-return" to="/market/HK00700">查看腾讯控股行情</router-link>
      </template>
      <template v-else>
      <p class="hint">使用已发布的 Transformer 模型。预测原点和之后五个交易日的日期由服务端核验后随结果保存。</p>

      <section aria-labelledby="prediction-ready-heading" class="readiness">
        <h2 id="prediction-ready-heading">预测准备</h2>
        <PageState v-if="loading" kind="loading" message="正在检查已发布模型和行情…"/>
        <template v-else>
          <PageState v-if="catalogError" kind="error" :message="'读取模型目录失败。'+catalogError"/>
          <PageState v-else-if="!supported.length" kind="empty" message="暂无已发布且支持此股票的五日模型。管理员审核后才能发起新的预测。"/>
          <p class="market-check" :class="{'blocked':marketBlocked}">
            <strong>行情</strong> {{ marketNote }} <span v-if="market?.lastTradeDate">已发布日线截至 {{ market.lastTradeDate }}。</span>
          </p>
        </template>
        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="预测模型">
            <el-select v-model="selected" :disabled="loading||busy||!!pending" placeholder="请选择已发布模型" aria-label="预测模型">
              <el-option v-for="model in supported" :key="model.modelId" :value="model.modelId" :label="model.artifactId+(model.recommended?'（推荐）':'')"/>
            </el-select>
          </el-form-item>
          <p v-if="selectedModel" class="hint">所选模型需要最近 {{ selectedModel.window }} 个交易日的输入。发起后还会核对复权数据和目标交易日历。</p>
          <div class="prediction-actions">
            <el-button data-test="predict" native-type="submit" type="primary" :disabled="!canSubmit" :loading="busy" @click.prevent="submit">
              {{ pendingId?'查询原预测状态':busy?'正在提交':pending?'用原编号重试':'发起五日预测' }}
            </el-button>
            <router-link :to="pendingId?{path:'/predictions',query:{predictionId:pendingId}}:'/predictions'">查看本人预测记录</router-link>
            <el-button v-if="!pending" :disabled="busy||loading" @click="loadChecks">重新检查条件</el-button>
          </div>
        </el-form>
      </section>

      <PageState v-if="busy" kind="loading" :message="pendingId?'正在查询原预测状态…':'正在准备预测输入并计算，完成后自动保存。'"/>
      <PageState v-else-if="pending&&(!record||error)" kind="pending" :message="error || (pendingId?'正在恢复原预测记录，可查询原预测状态。':'原预测请求的结果待确认。可使用原编号重试或查看本人记录。')"/>
      <PageState v-else-if="error" kind="error" :message="error"/>
      <PredictionResult v-if="record" :record="record"/>
      </template>
    </section>
  </main>
</template>

<style scoped>
.readiness{border:1px solid var(--border);border-radius:10px;padding:16px}
.readiness h2{margin:0 0 12px}
.market-check{margin:0 0 16px;color:var(--muted)}
.market-check strong{color:var(--ink);margin-inline-end:6px}
.market-check.blocked{color:#9b351c}
.prediction-actions{display:flex;gap:8px 16px;align-items:center;flex-wrap:wrap}
.prediction-actions a,.quote-return{display:inline-flex;align-items:center;min-height:44px}
.el-select{width:100%;max-width:680px}
.el-button{min-height:44px;margin-left:0}
</style>
