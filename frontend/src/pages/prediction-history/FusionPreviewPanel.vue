<script setup lang="ts">
import {computed,onUnmounted,ref,watch} from 'vue'
import {previewPredictionFusion,type FusionPreview,type PredictionRecord} from '../../api/predictions'

const props=defineProps<{records:PredictionRecord[]}>()
const selected=ref<string[]>([])
const weights=ref<Record<string,string>>({})
const preview=ref<FusionPreview|null>(null)
const busy=ref(false)
const error=ref('')
let requestVersion=0

const candidates=computed(()=>props.records.filter(row=>row.state==='SUCCEEDED'&&row.horizon===5&&row.snapshot&&!row.hidden))
const chosen=computed(()=>selected.value.flatMap(id=>candidates.value.find(row=>row.predictionId===id)??[]))
const submissions=computed(()=>chosen.value.map(row=>{
  const text=weights.value[row.predictionId]??''
  const basisPoints=/^(?:[0-9]{1,2}|100)(?:[.][0-9]{1,2})?$/.test(text)?Math.round(Number(text)*100):-1
  return {predictionId:row.predictionId,weightBps:basisPoints}
}))
const total=computed(()=>submissions.value.reduce((sum,row)=>sum+Math.max(0,row.weightBps),0))
const valid=computed(()=>submissions.value.length>=2&&submissions.value.every(row=>row.weightBps>=0)&&total.value===10000)

function key(row:PredictionRecord){
  const input=row.snapshot?.input
  return [row.instrumentId,input?.inputVersionId,input?.rawVersionId,input?.inputHash,input?.originDate].join('|')
}
const groups=computed(()=>{
  const keys=new Map<string,Set<string>>()
  for(const row of candidates.value){
    const item=key(row)
    if(!keys.has(item))keys.set(item,new Set())
    keys.get(item)?.add(row.modelId)
  }
  return candidates.value.filter(row=>(keys.get(key(row))?.size??0)>=2)
})

function canSelect(row:PredictionRecord){
  if(selected.value.includes(row.predictionId))return true
  const first=chosen.value[0]
  return selected.value.length<3&&(!first||key(first)===key(row)&&chosen.value.every(item=>item.modelId!==row.modelId))
}

function toggle(id:string){
  if(selected.value.includes(id))selected.value=selected.value.filter(item=>item!==id)
  else selected.value=[...selected.value,id]
  const size=selected.value.length
  if(!size){weights.value={};return}
  const regular=Math.floor(10000/size)
  weights.value=Object.fromEntries(selected.value.map((item,index)=>
    [item,((regular+(index===0?10000-regular*size:0))/100).toFixed(2)]))
}

async function calculate(){
  if(busy.value||!valid.value)return
  const version=++requestVersion
  busy.value=true
  error.value=''
  preview.value=null
  try {
    const result=await previewPredictionFusion(submissions.value)
    if(version===requestVersion)preview.value=result
  } catch(reason) {
    if(version===requestVersion)error.value=reason instanceof Error?reason.message:'暂时无法计算融合。'
  } finally {
    if(version===requestVersion)busy.value=false
  }
}
watch(weights,()=>{preview.value=null;error.value=''},{deep:true})
watch(()=>props.records,()=>{
  requestVersion++
  selected.value=[]
  weights.value={}
  preview.value=null
  busy.value=false
  error.value=''
})
const score=(ratio:string|null|undefined)=>ratio==null?'—':(Number(ratio)*100).toFixed(3)+'%'
const assessmentNotes={
  NO_SHARED_EVIDENCE:'所选记录暂无共同的验证行情版本。',
  EVIDENCE_UNAVAILABLE:'共同验证证据暂不可用。',
  PENDING:'共同验证行情尚无可核验的目标日。',
} as const
const assessmentNote=computed(()=>{
  const state=preview.value?.assessment?.state
  return state&&state in assessmentNotes?assessmentNotes[state as keyof typeof assessmentNotes]:''
})
onUnmounted(()=>{requestVersion++})
</script>

<template>
  <section class="fusion" aria-labelledby="fusion-title">
    <h2 id="fusion-title">模型融合预览</h2>
    <p>选择同一行情版本下由不同模型生成的 2–3 条记录，比较加权价格与起点收盘价基线。</p>
    <p v-if="!groups.length">本页暂无可配对的预测记录。</p>
    <form v-else @submit.prevent="calculate">
      <fieldset :disabled="busy">
        <legend>选择本人预测</legend>
        <div v-for="(row,index) in groups" :key="row.predictionId" class="fusion-source">
          <label>
            <input type="checkbox" :checked="selected.includes(row.predictionId)" :disabled="!canSelect(row)"
              @change="toggle(row.predictionId)">
            {{row.instrumentId}} · {{row.snapshot?.input.originDate}} · {{row.modelSnapshot.artifactId}}
            <small>{{row.predictionId.slice(0,8)}}</small>
          </label>
          <label v-if="selected.includes(row.predictionId)" :for="'fusion-weight-'+index">
            权重（%）
            <input :id="'fusion-weight-'+index" v-model="weights[row.predictionId]" type="text" inputmode="decimal"
              maxlength="6" :aria-label="'第 '+(index+1)+' 条预测权重百分比'">
          </label>
        </div>
      </fieldset>
      <p v-if="selected.length">权重合计 {{(total/100).toFixed(2)}}%，最多两位小数。</p>
      <button type="submit" :disabled="busy||!valid">{{busy?'正在核对并计算…':'计算融合预览'}}</button>
    </form>
    <p v-if="error" role="alert">{{error}}</p>
    <div v-if="preview">
      <p>{{preview.instrumentId}} · 起点 {{preview.originDate}} · 使用已保存的预测证据</p>
      <div class="fusion-table">
        <table>
          <caption>未来五个交易日的参考价格（元）</caption>
          <thead><tr>
            <th scope="col">目标交易日</th>
            <th v-for="source in preview.sources" :key="source.predictionId" scope="col">
              {{source.artifactId}}（{{(source.weightBps/100).toFixed(2)}}%）
            </th>
            <th scope="col">融合参考价</th><th scope="col">起点收盘价基线</th>
          </tr></thead>
          <tbody><tr v-for="point in preview.points" :key="point.step">
            <th scope="row">{{point.targetDate}}</th>
            <td v-for="source in preview.sources" :key="source.predictionId">{{source.referencePrices[point.step-1]}}</td>
            <td>{{point.referencePrice}}</td><td>{{preview.baselinePrice}}</td>
          </tr></tbody>
        </table>
      </div>
      <p v-if="assessmentNote" role="status">{{assessmentNote}}</p>
      <div v-if="preview.assessment?.fusion?.verifiedCount" class="fusion-table">
        <p>共同核验 {{preview.assessment.fusion.verifiedCount}} / 5 个交易日。</p>
        <p v-if="preview.assessment.fusion.originRawRevised">验证行情修订过起点原始收盘价，误差仍按预测时的起点计算。</p>
        <table>
          <caption>共同验证行情上的相对 MAE</caption>
          <thead><tr><th scope="col">预测方式</th><th scope="col">相对 MAE</th></tr></thead>
          <tbody>
            <tr v-for="source in preview.sources" :key="source.predictionId">
              <th scope="row">{{source.artifactId}}</th>
              <td>{{score(preview.assessment.members.find(row=>row.predictionId===source.predictionId)?.metrics.ratioMae)}}</td>
            </tr>
            <tr><th scope="row">融合</th><td>{{score(preview.assessment.fusion.metrics.ratioMae)}}</td></tr>
            <tr><th scope="row">朴素基线</th><td>{{score(preview.assessment.fusion.metrics.baselineRatioMae)}}</td></tr>
          </tbody>
        </table>
        <details><summary>查看验证行情版本</summary>{{preview.assessment.fusion.evidenceVersionId}}</details>
      </div>
    </div>
  </section>
</template>

<style scoped>
.fusion{margin-block:24px;padding:16px 0;border-top:1px solid var(--border)}
fieldset{min-width:0;border:1px solid var(--border);border-radius:6px}
.fusion-source{display:flex;flex-wrap:wrap;gap:8px 16px;padding:8px 0;overflow-wrap:anywhere}
.fusion-source label{display:flex;align-items:center;flex-wrap:wrap;gap:6px}
.fusion-source input[type=text]{width:6em;min-height:32px}
button{min-height:44px;padding:6px 12px;font:inherit}
.fusion-table{overflow-x:auto}
table{width:100%;border-collapse:collapse;text-align:left}
caption{text-align:left;font-weight:700}
th,td{padding:8px;border-bottom:1px solid var(--border);white-space:nowrap}
</style>
