import {mount} from '@vue/test-utils'
import {it,expect} from 'vitest'
import ElementPlus from 'element-plus'
import PredictionResult from './PredictionResult.vue'
import type {PredictionRecord} from '../../api/predictions'
const record={predictionId:'fixed-record',operationId:'op',modelId:'fixed-model',instrumentId:'SH600030',horizon:5,state:'SUCCEEDED',errorCode:null,hidden:false,createdAt:'2026-10-05',expiresAt:'2026-10-05',modelSnapshot:{artifactId:'fixture',manifestHash:'a',checkpointHash:'b',revision:1,manifest:{trainingCutoff:'2023-12-31'}},snapshot:{input:{inputVersionId:'fixed-input',rawVersionId:'fixed-raw',inputHash:'fixed-hash',rawContentHash:'raw-hash',originDate:'2026-09-30',originRawClose:'25.86'},prediction:{publicationRevision:1,referenceBasis:'ORIGIN_SHARE_BASIS',points:[8,9,12,13,14].map((day,i)=>({step:i+1,targetDate:`2026-10-${String(day).padStart(2,'0')}`,ratio:'1.02',referencePrice:'26.377200'}))}}} as PredictionRecord
it('shows exactly five target days and evidence with unavailable extras explicit',()=>{const w=mount(PredictionResult,{props:{record},global:{plugins:[ElementPlus],stubs:{VerificationPanel:true,ExplanationPanel:true}}});expect(w.findAll('tbody tr')).toHaveLength(5);expect(w.find('svg').exists()).toBe(true);expect(w.text()).toContain('2026-10-08');expect(w.text()).toContain('暂不展示');expect(w.find('explanation-panel-stub').exists()).toBe(true);expect(w.text()).toContain('fixed-input');w.unmount()})
it('never shows success chart for a failed record even with stray payload',()=>{const w=mount(PredictionResult,{props:{record:{...record,state:'FAILED',errorCode:'MODEL_NOT_PUBLISHED'}},global:{plugins:[ElementPlus],stubs:{VerificationPanel:true,ExplanationPanel:true}}});expect(w.find('svg').exists()).toBe(false);expect(w.find('tbody').exists()).toBe(false);expect(w.text()).toContain('尚未发布');w.unmount()})
it('marks a history snapshot and offers read and new-prediction entries',()=>{
 const w=mount(PredictionResult,{props:{record,historical:true},global:{plugins:[ElementPlus],stubs:{
  VerificationPanel:true,ExplanationPanel:true,
  RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'},
 }}})
 expect(w.text()).toContain('历史预测')
 expect(w.text()).toContain('fixed-raw')
 expect(w.get('a[href="/market/SH600030"]').text()).toBe('查看最新行情')
 expect(w.get('a[href="/market/SH600030/predict"]').text()).toBe('准备新预测')
 w.unmount()
})
