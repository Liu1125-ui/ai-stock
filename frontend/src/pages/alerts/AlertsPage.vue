<script setup lang="ts">
import {useUnsavedDraft} from '../../router/useUnsavedDraft'
import PageState from '../../components/PageState.vue'
import {computed,ref,watch,onUnmounted} from 'vue'
import {analysisReturn,listReturn,validStock} from '../../router/listContext'
import {useRoute} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {ApiError} from '../../api/auth'
import {listAlerts,createAlert,patchAlert,type AlertRule,type AlertCheck,type AlertCondition,type AlertCreate} from '../../api/alerts'
import TriggerHistoryPanel from '../messages/TriggerHistoryPanel.vue'
const auth=useAuthStore(),route=useRoute()
const returnTarget=computed(()=>listReturn(route.query.from)||analysisReturn(route.query.from,route.query.instrumentId))
const historyRule=ref(''),listError=ref('')
const recentChecks=ref<Record<string,AlertCheck>>({})
const rows=ref<AlertRule[]>([]),page=ref(1),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),success=ref('')
const instrument=ref(''),condition=ref<AlertCondition>('PRICE_ABOVE'),threshold=ref(''),editing=ref<AlertRule|null>(null),editConflict=ref(false)
const stopTarget=ref<AlertRule|null>(null)
const dirty=computed(()=>!!auth.profile&&(
 instrument.value!==(editing.value?.instrumentId??validStock(route.query.instrumentId))||
 condition.value!==(editing.value?.condition??'PRICE_ABOVE')||
 threshold.value!==(editing.value?.threshold??'')
))
const discardDraft=useUnsavedDraft(dirty,'预警规则草稿')
function cancelEdit(){if(discardDraft())reset()}
function changeEnabled(rule:AlertRule){
 if(rule.enabled)stopTarget.value=rule
 else void toggle(rule)
}
async function confirmStop(){
 const target=stopTarget.value
 if(!target||busy.value)return
 stopTarget.value=null
 await toggle(target)
}
const options:{value:AlertCondition;label:string}[]=[{value:'PRICE_ABOVE',label:'收盘价 > 阈值'},{value:'PRICE_BELOW',label:'收盘价 < 阈值'},{value:'DAILY_RISE',label:'日涨幅 > 阈值（%）'},{value:'DAILY_FALL',label:'日跌幅 > 阈值（%）'}]
let epoch=0,loadSequence=0,pending:AlertCreate|undefined
const checkLabels:Record<string,string>={
 RUNNING:'正在检查',
 TRIGGERED:'已触发，站内提醒已生成',
 ALREADY_TRIGGERED:'当日已触发',
 NOT_MET:'已检查，未触发',
 SKIPPED_STALE:'行情待更新或版本已变化',
 EVIDENCE_UNAVAILABLE:'行情缺失或待核验',
 FAILED:'检查失败',
 SKIPPED_NOT_EFFECTIVE:'当日规则尚未生效',
 SKIPPED_RULE_CHANGED:'检查期间规则已修改',
 SKIPPED_INACTIVE:'规则或账号已停用',
}
const checkTime=new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',dateStyle:'medium',timeStyle:'short'})
function checkLabel(rule:AlertRule){
 const check=recentChecks.value[rule.id]
 if(check)return checkLabels[check.state]??'检查状态待确认'
 if(!rule.enabled)return '规则已停用'
 if(rule.evaluationAvailability==='NOT_ENABLED')return '自动检查尚未启用'
 return '自动检查已启用 · 暂无当前版本的检查记录'
}
function displayCheckTime(value:string){
 const date=new Date(value)
 return Number.isNaN(date.getTime())?'暂无':checkTime.format(date)
}
const label=(value:string)=>options.find(x=>x.value===value)?.label||value
function reset(){instrument.value=validStock(route.query.instrumentId);condition.value='PRICE_ABOVE';threshold.value='';editing.value=null;editConflict.value=false;pending=undefined}
function valid(){const value=threshold.value,price=condition.value.startsWith('PRICE_');if(!/^(SH|SZ)[0-9]{6}$/.test(instrument.value)||!/^\d{1,14}(\.\d{1,6})?$/.test(value)||!/[1-9]/.test(value))return false;return price||(!value.includes('.')||value.split('.')[1]!.length<=4)&&Number(value)<=100}
async function load(target=page.value){const generation=epoch,sequence=++loadSequence;loading.value=true;listError.value='';try{const result=await listAlerts(target);if(generation!==epoch||sequence!==loadSequence)return;rows.value=result.items;recentChecks.value=result.recentChecks??{};page.value=result.page;total.value=result.total}catch(e){if(generation===epoch&&sequence===loadSequence)listError.value=(e as Error).message}finally{if(generation===epoch&&sequence===loadSequence)loading.value=false}}
function edit(rule:AlertRule){if(busy.value||!discardDraft())return;stopTarget.value=null;editing.value=rule;editConflict.value=false;instrument.value=rule.instrumentId;condition.value=rule.condition as AlertCondition;threshold.value=rule.threshold;pending=undefined;success.value='';error.value=''}
async function save(){if(busy.value||editConflict.value||!auth.profile)return;if(!valid()){error.value='请检查股票代码和正数阈值：价格最多6位小数，涨跌幅最多4位且不超过100%';return}const generation=epoch;busy.value=true;error.value='';success.value='';try{if(editing.value){await patchAlert(editing.value.id,{revision:editing.value.revision,condition:condition.value,threshold:threshold.value})}else{if(!pending||pending.instrumentId!==instrument.value||pending.condition!==condition.value||pending.threshold!==threshold.value)pending={requestId:crypto.randomUUID(),instrumentId:instrument.value,condition:condition.value,threshold:threshold.value};await createAlert(pending)}if(generation!==epoch)return;success.value=editing.value?'规则已修改':'规则已保存';reset();await load(1)}catch(e){if(generation!==epoch)return;error.value=(e as Error).message;if(e instanceof ApiError&&e.code==='ALERT_REVISION_CONFLICT'){editConflict.value=true;error.value='规则已在其他页面修改。已保留草稿，请在列表中重新点击修改，核对最新规则后再保存。';await load()}}finally{if(generation===epoch)busy.value=false}}
async function toggle(rule:AlertRule){if(busy.value||!auth.profile)return;const generation=epoch;busy.value=true;error.value='';success.value='';try{await patchAlert(rule.id,{revision:rule.revision,enabled:!rule.enabled});if(generation!==epoch)return;success.value=rule.enabled?'规则已停用，后续自动检查暂停。历史提醒仍可查看。':'规则已启用，后续按新的生效日期检查。';if(editing.value?.id===rule.id){if(dirty.value)editConflict.value=true;else reset();}await load()}catch(e){if(generation!==epoch)return;error.value=(e as Error).message;if(e instanceof ApiError&&e.code==='ALERT_REVISION_CONFLICT')await load()}finally{if(generation===epoch)busy.value=false}}
watch(()=>auth.profile?.userId,owner=>{epoch++;loadSequence++;historyRule.value='';listError.value='';stopTarget.value=null;rows.value=[];recentChecks.value={};total.value=0;page.value=1;busy.value=false;loading.value=false;error.value='';success.value='';reset();if(owner)void load(1)},{immediate:true,flush:'sync'})
watch(()=>route.query.instrumentId,value=>{
 if(!busy.value&&!editing.value&&!pending&&!threshold.value)instrument.value=validStock(value)
})
watch(()=>route.query.ruleId,value=>{historyRule.value=typeof value==='string'?value:''},{immediate:true})
onUnmounted(()=>{epoch++;loadSequence++})
</script>
<template><main class="account-wrap"><section class="account-card wide"><router-link v-if="returnTarget" :to="returnTarget">{{returnTarget.path==='/watchlist'?'← 返回自选股':returnTarget.path==='/'?'← 返回行情':'← 返回行情分析'}}</router-link><p class="eyebrow">我的预警</p><h1>预警规则</h1><p class="hint">为股票设置收盘后日线条件，管理规则的启用与停用。</p>
<p class="hint small">自动检查在收盘后核对已发布日线，只检查规则生效日期之后的交易日。触发后可在消息中心查看站内提醒，邮件投递状态见对应消息。</p>
<PageState v-if="error" :kind="pending?'pending':'error'" :message="pending ? error+'。可填写原条件后重试保存，保留原请求编号。' : error"/><el-alert v-if="success" :title="success" type="success" :closable="false" role="status"/>
<section class="rule-form"><h2>{{editing?'修改规则':'新建规则'}}</h2><el-form label-position="top" @submit.prevent="save"><div class="rule-fields"><el-form-item label="股票代码"><el-input v-model="instrument" placeholder="例如 SH600030" :disabled="busy||!!editing" maxlength="8" aria-label="股票代码"/></el-form-item><el-form-item label="预警条件"><el-select v-model="condition" :disabled="busy" aria-label="预警条件"><el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value"/></el-select></el-form-item><el-form-item :label="condition.startsWith('PRICE_')?'价格阈值（元，未复权）':'幅度阈值（%，输入正数）'"><el-input v-model="threshold" data-testid="threshold" :disabled="busy" inputmode="decimal" placeholder="输入正数" aria-label="阈值"/></el-form-item></div><p class="hint small">价格规则比较当日未复权收盘价。涨跌幅规则用同一来源的前一交易日与当日后复权收盘价计算，阈值填写正数。行情值等于阈值时不会触发。</p><el-button type="primary" data-testid="create-alert" :disabled="busy||editConflict||!auth.profile" :loading="busy" @click="save">{{editing?'保存修改':'保存规则'}}</el-button><el-button v-if="editing" :disabled="busy" @click="cancelEdit">取消修改</el-button></el-form></section>
<div class="rule-list-head"><h2>已保存规则（{{total}}）</h2><el-button :disabled="loading||busy" @click="load()">刷新列表</el-button></div><PageState v-if="loading" kind="loading" message="正在读取规则…"/><PageState v-if="listError" kind="error" :message="listError"/><PageState v-else-if="!loading&&!rows.length" kind="empty" message="暂无规则，可在上方新建，或从股票详情页进入设置。"/><div v-if="rows.length" class="rule-table-wrap" tabindex="0" role="region" aria-label="预警规则及最近检查"><table><caption class="hint small">只检查生效日期之后的交易日；重新启用或修改条件将更新生效日期。</caption><thead><tr><th>股票</th><th>条件与阈值</th><th>状态</th><th>生效日期之后</th><th>最近检查</th><th>操作</th></tr></thead><tbody><tr v-for="rule in rows" :key="rule.id"><td><router-link :to="'/market/'+rule.instrumentId">{{rule.instrumentId}}</router-link></td><td>{{label(rule.condition)}} {{rule.threshold}}{{rule.condition.startsWith('DAILY_')?'%':'元'}}</td><td>{{rule.enabled?'已启用':'已停用'}}</td><td>{{rule.effectiveAfterTradeDate}}</td>
<td class="rule-check">
 <strong>{{checkLabel(rule)}}</strong>
 <template v-if="recentChecks[rule.id]">
  <time :datetime="recentChecks[rule.id]!.checkedAt">{{displayCheckTime(recentChecks[rule.id]!.checkedAt)}}</time>
  <span>行情日期 {{recentChecks[rule.id]!.tradeDate??'暂无'}}</span>
 </template>
</td><td class="rule-actions"><el-button data-testid="trigger-history" @click="historyRule=rule.id">触发历史</el-button><el-button :disabled="busy" @click="edit(rule)">修改</el-button><el-button data-testid="toggle-rule" :disabled="busy" @click="changeEnabled(rule)">{{rule.enabled?'停用':'启用'}}</el-button></td></tr></tbody></table></div><el-pagination v-if="total>20" :current-page="page" :page-size="20" :total="total" layout="prev,pager,next" :disabled="loading||busy" @current-change="load"/>
<section v-if="stopTarget" class="stop-confirm" role="group" aria-label="确认停用预警">
 <p>停用 {{stopTarget.instrumentId}} 的这条规则后，系统将停止后续自动检查。历史提醒仍可查看，已交给邮件服务器的通知无法撤回。</p>
 <el-button data-testid="confirm-stop-rule" :disabled="busy" @click="confirmStop">确认停用</el-button>
 <el-button @click="stopTarget=null">取消</el-button>
</section>
<TriggerHistoryPanel v-if="historyRule" :rule-id="historyRule"/></section></main></template>
<style scoped>.rule-form{border-block:1px solid var(--border);padding:24px 0;margin:24px 0}.rule-fields{display:grid;grid-template-columns:1fr 1.3fr 1fr;gap:20px}.rule-fields .el-select{width:100%}.rule-list-head{display:flex;justify-content:space-between;align-items:center;gap:16px}.rule-list-head h2{margin:0}.rule-table-wrap{overflow-x:auto;margin-top:20px}table{width:100%;border-collapse:collapse;min-width:720px;text-align:left}caption{text-align:left;caption-side:bottom;padding-top:12px}th,td{padding:14px 12px;border-bottom:1px solid var(--border)}th{background:var(--canvas);font-size:14px}.rule-actions{white-space:nowrap}.rule-check{min-width:160px}.stop-confirm{margin:14px 0;padding:12px 16px;border:1px solid var(--border);border-radius:8px;background:var(--canvas)}.rule-check time,.rule-check span{display:block;color:var(--muted);font-size:13px}@media(max-width:700px){.rule-fields{grid-template-columns:1fr;gap:0}.rule-list-head{align-items:flex-start}}</style>
