<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {useAuthStore} from '../../stores/auth'
import {listModels,modelAction,modelEvents,compareModelHoldout,type ModelHoldoutComparison,type ModelAction,type ModelVersion,type ModelHead,type ModelReview,type ModelEvent} from '../../api/models'
import {actionLabels,modelActions,requiresAcknowledgement,canSubmitReview} from './modelPolicy'
const auth=useAuthStore(),items=ref<ModelVersion[]>([]),head=ref<ModelHead>({scope:'TRANSFORMER_60_5_V1',currentModelId:null,revision:0}),busy=ref(false),error=ref(''),success=ref('')
const dialog=ref(false),selected=ref<ModelVersion>(),action=ref<ModelAction>('publish'),reason=ref(''),acknowledged=ref(false),submitted=ref<ModelReview>(),operationId=ref('')
const historyOpen=ref(false),events=ref<ModelEvent[]>([]),historyTitle=ref('')
const stateLabels={PENDING:'待审核',PUBLISHED:'已发布',REJECTED:'未采用',OFFLINE:'已下线'}
const allowed=computed(()=>!busy.value&&canSubmitReview(action.value,reason.value,acknowledged.value))
const recommended=computed(()=>items.value.find(v=>v.modelId===head.value.currentModelId)?.artifactId)
function percent(value:string|undefined,digits=4){const n=value==null?NaN:Number(value);return Number.isFinite(n)?(100*n).toFixed(digits)+'%':'暂无'}
async function load(){comparison.value=undefined;comparisonError.value='';busy.value=true;error.value='';try{const result=await listModels();items.value=result.items;head.value=result.head}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
function open(version:ModelVersion,next:ModelAction){selected.value=version;action.value=next;reason.value='';acknowledged.value=false;submitted.value=undefined;operationId.value=crypto.randomUUID();dialog.value=true;success.value='';error.value=''}
async function confirm(){
 if(!selected.value||!allowed.value)return
 const payload=submitted.value||{operationId:operationId.value,expectedRevision:selected.value.revision,expectedHeadRevision:head.value.revision,reason:reason.value.trim(),researchLimitationsAcknowledged:acknowledged.value}
 submitted.value=payload;busy.value=true;error.value=''
 try{const result=await modelAction(selected.value.modelId,action.value,payload);items.value=items.value.map(v=>v.modelId===result.model.modelId?result.model:v);head.value=result.head;dialog.value=false;success.value='操作已保存，审核记录已更新。';await load()}
 catch(e){const status=(e as {status?:number}).status;const message=(e as Error).message;if(status===409){dialog.value=false;await load();error.value=message}else{error.value=message+'；可重试同一操作或刷新核对状态。'}}finally{busy.value=false}
}
async function history(version:ModelVersion){busy.value=true;error.value='';try{events.value=await modelEvents(version.modelId);historyTitle.value=version.artifactId;historyOpen.value=true}catch(e){error.value=(e as Error).message}finally{busy.value=false}}
const comparison=ref<ModelHoldoutComparison>()
const comparisonId=ref('')
const comparisonLoading=ref(false)
const comparisonError=ref('')
const comparisonRows=computed(()=>{
 const result=comparison.value?.metrics
 return result ? [
  {label:'候选模型', artifact:result.candidate.artifactId, ...result.candidate},
  {label:'当前推荐模型', artifact:result.reference.artifactId, ...result.reference},
  {label:'朴素基线（维持原点收盘价）', artifact:'', ...result.baseline},
 ] : []
})
async function compareHoldout(version:ModelVersion){
 comparisonId.value=version.modelId
 comparisonLoading.value=true
 comparison.value=undefined
 comparisonError.value=''
 try{
  const result=await compareModelHoldout(version.modelId)
  if(result.candidateId!==version.modelId||result.referenceId!==head.value.currentModelId||
     result.headRevision!==head.value.revision) throw new Error('推荐版本已变化，请刷新模型列表后重试')
  comparison.value=result
 }catch(e){comparisonError.value=(e as Error).message}
 finally{comparisonLoading.value=false}
}
onMounted(()=>void load())
</script>
<template>
 <main class="account-wrap"><section class="account-card wide model-workspace">
  <div class="model-header"><div><p class="eyebrow">管理员工作区</p><h1>模型审核与发布</h1><p class="hint">核对制品、支持范围和评价后，再决定是否发布。发布不会自动设为推荐。</p></div><el-button :loading="busy" :disabled="comparisonLoading" @click="load">刷新状态</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/>
  <el-alert v-if="success" :title="success" type="success" :closable="false" role="status"/>
  <div class="recommendation"><strong>当前推荐</strong><span>{{recommended||'尚未设置推荐版本'}}</span><small>五交易日 · 版本 {{head.revision}}</small></div>
  <p class="research-note">以下评价来自回顾性开发实验，未完成未来盲测。MAE是误差；方向命中率单独展示。已发布模型也需要结合真实误差与基线判断。</p>
  <div class="model-cards" v-loading="busy">
   <article v-for="version in items" :key="version.modelId" class="model-card">
    <div class="card-title"><h2>{{version.artifactId}}</h2><el-tag>{{stateLabels[version.state]}}</el-tag></div>
    <p class="model-meta">训练截止 {{version.manifest.trainingCutoff}} · {{version.manifest.config.seq_len}}日窗口 · {{version.manifest.config.enc_in}}列量价 · 未来{{version.manifest.config.pred_len}}交易日</p>
    <p class="model-meta">支持股票：{{version.manifest.instruments.join('、')}}</p>
    <template v-if="version.evaluation.pool">
      <p class="model-meta">股票池逐股回测 · {{version.evaluation.pool.length}} 只</p>
      <dl v-for="stock in version.evaluation.pool" :key="stock.instrumentId" class="metric-grid">
        <div><dt>股票及回测期</dt><dd>{{stock.instrumentId}} · {{stock.from}} 至 {{stock.through}}</dd></div>
        <div><dt>回测 MAE</dt><dd>{{percent(stock.mae)}}</dd></div>
        <div><dt>价格不变基线 MAE</dt><dd>{{percent(stock.baselineMae)}}</dd></div>
        <div><dt>五日窗口数</dt><dd>{{stock.windows}}</dd></div>
      </dl>
    </template>
    <template v-else-if="version.evaluation.expansion">
      <p class="model-meta">扩股回测 {{version.evaluation.expansion.from}} 至 {{version.evaluation.expansion.through}}</p>
      <dl class="metric-grid">
        <div><dt>回测 MAE</dt><dd>{{percent(version.evaluation.expansion.mae)}}</dd></div>
        <div><dt>价格不变基线 MAE</dt><dd>{{percent(version.evaluation.expansion.baselineMae)}}</dd></div>
        <div><dt>方向命中率</dt><dd>{{percent(version.evaluation.expansion.directionAccuracy,2)}}</dd></div>
        <div><dt>五日窗口数</dt><dd>{{version.evaluation.expansion.windows}}</dd></div>
      </dl>
    </template>
    <template v-else-if="version.evaluation.rolling">
      <p v-if="version.manifest.trainingData" class="model-meta">
        验证期 {{version.manifest.trainingData.validation.from}} 至 {{version.manifest.trainingData.validation.through}}
        · 留出期 {{version.manifest.trainingData.holdout.from}} 至 {{version.manifest.trainingData.holdout.through}}
      </p>
      <dl class="metric-grid">
        <div><dt>验证 MAE</dt><dd>{{percent(version.evaluation.validationMae)}}</dd></div>
        <div><dt>验证期基线 MAE</dt><dd>{{percent(version.evaluation.validationBaselineMae)}}</dd></div>
        <div><dt>留出 MAE</dt><dd>{{percent(version.evaluation.rolling.holdoutMae)}}</dd></div>
        <div><dt>留出期基线 MAE</dt><dd>{{percent(version.evaluation.rolling.holdoutBaselineMae)}}</dd></div>
        <div><dt>留出方向命中率</dt><dd>{{percent(version.evaluation.rolling.holdoutDirection,2)}}</dd></div>
        <div><dt>留出窗口数</dt><dd>{{version.evaluation.rolling.holdoutWindows}}</dd></div>
      </dl>
    </template>
    <dl v-else class="metric-grid"><div><dt>{{version.evaluation.validationYear}}验证MAE</dt><dd>{{percent(version.evaluation.validationMae)}}</dd></div><div><dt>同验证期基线MAE</dt><dd>{{percent(version.evaluation.validationBaselineMae)}}</dd></div><div><dt>2026回顾MAE</dt><dd>{{percent(version.evaluation.mae2026)}}</dd></div><div><dt>2026方向命中率</dt><dd>{{percent(version.evaluation.direction2026,2)}}</dd></div></dl>
    <details><summary>查看版本依据</summary><p class="model-meta">特征：{{version.manifest.featureDefinition}}<span v-if="!version.evaluation.rolling && !version.evaluation.expansion && !version.evaluation.pool">；2025回顾MAE：{{percent(version.evaluation.mae2025)}}</span>；随机种子：{{version.evaluation.seed}}</p><dl class="provenance"><dt>模型版本</dt><dd>{{version.modelId}}</dd><dt>制品清单SHA256</dt><dd>{{version.manifestHash}}</dd><dt>权重SHA256</dt><dd>{{version.checkpointHash}}</dd><dt>研究报告SHA256</dt><dd>{{version.evaluation.sourceHash}}</dd></dl></details>
    <section v-if="version.evaluation.rolling && head.currentModelId && head.currentModelId!==version.modelId" class="holdout-comparison" :aria-busy="comparisonId===version.modelId && comparisonLoading">
      <el-button :disabled="busy||comparisonLoading" :loading="comparisonId===version.modelId && comparisonLoading" @click="compareHoldout(version)">同样本对照</el-button>
      <template v-if="comparisonId===version.modelId">
        <p v-if="comparisonError" class="model-meta" role="alert">{{comparisonError}}</p>
        <template v-if="comparison">
          <p class="model-meta" role="status">留出期 {{comparison.metrics.holdout.from}} 至 {{comparison.metrics.holdout.through}} · {{comparison.metrics.windows}} 个五日窗口</p>
          <div class="comparison-rows">
            <article v-for="row in comparisonRows" :key="row.label" class="comparison-row">
              <strong>{{row.label}}</strong>
              <small v-if="row.artifact">{{row.artifact}}</small>
              <dl>
                <div><dt>相对价格 MAE</dt><dd>{{percent(String(row.mae))}}</dd></div>
                <div><dt>相对价格 RMSE</dt><dd>{{percent(String(row.rmse))}}</dd></div>
                <div><dt>方向命中率</dt><dd>{{percent(String(row.direction),2)}}</dd></div>
              </dl>
            </article>
          </div>
          <details><summary>查看对照数据指纹</summary><p class="model-meta">{{comparison.metrics.datasetHash}}</p></details>
        </template>
      </template>
    </section>
    <div class="model-actions"><el-button v-for="next in modelActions(auth.profile?.role||'',version,head.currentModelId)" :key="next" :data-test="next+'-model'" :type="next==='publish'?'primary':'default'" :disabled="busy||comparisonLoading" @click="open(version,next)">{{actionLabels[next]}}</el-button><el-button :disabled="busy" @click="history(version)">审核记录</el-button></div>
   </article>
   <p v-if="!busy&&items.length===0" class="hint">暂无已登记候选。完成制品与评价核验后，再导入候选。</p>
  </div>
  <el-dialog v-model="dialog" :title="actionLabels[action]+'模型'" width="min(560px,calc(100vw - 32px))" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy">
   <template v-if="selected"><p class="model-meta">{{selected.artifactId}} · 模型版本 {{selected.revision}}</p><p class="research-note">确认支持范围、验证误差和回顾性局限。下线当前推荐版本后，将暂停使用该推荐；历史结果仍保留。</p>
    <el-form label-position="top"><el-form-item label="审核理由（10—500字）"><el-input v-model="reason" type="textarea" :rows="4" maxlength="500" show-word-limit :disabled="busy||!!submitted"/></el-form-item><el-checkbox v-if="requiresAcknowledgement(action)" v-model="acknowledged" :disabled="busy||!!submitted">我已阅读评价与基线，了解当前结果的回顾性局限</el-checkbox></el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/>
   </template>
   <template #footer><el-button :disabled="busy" @click="dialog=false">取消</el-button><el-button data-test="confirm-model-action" type="primary" :disabled="!allowed" :loading="busy" @click="confirm">{{submitted?'重试同一操作':'确认'+actionLabels[action]}}</el-button></template>
  </el-dialog>
  <el-dialog v-model="historyOpen" title="审核记录" width="min(700px,calc(100vw - 32px))"><p class="model-meta">{{historyTitle}}</p><ol class="audit-events"><li v-for="event in events" :key="event.eventId"><strong>{{event.action}}</strong><time>{{event.createdAt}}</time><p>{{event.reason}}</p><small>{{event.actorId?'管理员：'+event.actorId:'可信服务登记'}}</small></li></ol></el-dialog>
 </section></main>
</template>
<style scoped>
.model-header{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.model-header .hint{margin-bottom:16px}.recommendation{display:flex;align-items:center;gap:16px;flex-wrap:wrap;padding:16px 20px;background:var(--canvas);border-radius:8px;border:1px solid var(--border)}.recommendation span{overflow-wrap:anywhere}.recommendation small{color:var(--muted)}.research-note{padding:14px 18px;border-left:3px solid var(--accent);background:var(--canvas);color:var(--muted);font-size:14px}.model-cards{display:grid;gap:20px}.model-card{padding:24px;border:1px solid var(--border);border-radius:10px}.card-title{display:flex;align-items:center;justify-content:space-between;gap:16px}.card-title h2{margin:0;font-size:18px;overflow-wrap:anywhere}.model-meta{font-size:14px;color:var(--muted);overflow-wrap:anywhere}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:24px 0}.metric-grid dt{font-size:13px;color:var(--muted)}.metric-grid dd{margin:6px 0 0;font-size:23px;font-variant-numeric:tabular-nums;font-weight:600}.model-actions{display:flex;gap:10px;flex-wrap:wrap;margin-top:20px}.model-actions .el-button{margin:0;min-height:44px}summary{cursor:pointer;color:var(--accent);min-height:44px;line-height:44px}summary:focus-visible{outline:3px solid var(--accent)}.provenance{font-size:13px}.provenance dd{margin:4px 0 12px;overflow-wrap:anywhere}.audit-events{padding-left:20px}.audit-events li{padding:12px 0;border-bottom:1px solid var(--border)}.audit-events time{display:block;font-size:13px;color:var(--muted)}.audit-events p{margin:8px 0;overflow-wrap:anywhere}.audit-events small{overflow-wrap:anywhere;color:var(--muted)}:deep(.el-checkbox){white-space:normal;height:auto}:deep(.el-checkbox__label){white-space:normal;line-height:1.6}
@media(max-width:700px){.model-header{flex-direction:column}.card-title{align-items:flex-start;flex-direction:column}.model-card{padding:18px}.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.metric-grid dd{font-size:21px}}
.holdout-comparison{margin-top:18px;padding:16px;border:1px solid var(--border);border-radius:8px;background:var(--canvas)}
.holdout-comparison>.el-button{min-height:44px}
.comparison-rows{display:grid;gap:8px}
.comparison-row{display:grid;gap:4px;padding:12px;border-bottom:1px solid var(--border);overflow-wrap:anywhere}
.comparison-row small,.comparison-row dt{color:var(--muted);font-size:13px}
.comparison-row dl{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:6px 0 0}
.comparison-row dd{margin:4px 0 0;font-variant-numeric:tabular-nums}
</style>
