import {afterEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ModelsPage from './ModelsPage.vue'
import * as api from '../../api/models'
import {modelActions,canSubmitReview} from './modelPolicy'
vi.mock('../../api/models',()=>({listModels:vi.fn(),modelEvents:vi.fn(),modelAction:vi.fn()}))
vi.mock('../../stores/auth',()=>({useAuthStore:()=>({profile:{role:'ADMIN'}})}))
const version={modelId:'11111111-1111-1111-1111-111111111111',artifactId:'transformer-s3-2023-seed42',manifestHash:'a'.repeat(64),checkpointHash:'c'.repeat(64),state:'PENDING' as const,revision:0,everPublished:false,registrationHash:'d'.repeat(64),manifest:{trainingCutoff:'2023-12-31',instruments:['SH600030','SH600276','SZ000002'],config:{seq_len:60,enc_in:6,pred_len:5},featureDefinition:'baostock-six-price-volume-v1'},evaluation:{sourceHash:'b'.repeat(64),scope:'RETROSPECTIVE_DEVELOPMENT',seed:42,validationYear:2024,validationMae:'0.032',validationBaselineMae:'0.0321',mae2025:'0.0237',mae2026:'0.0311',direction2026:'0.4775'},createdAt:'2026-10-03T00:00:00Z'}
const head={scope:'TRANSFORMER_60_5_V1',currentModelId:null,revision:0}
afterEach(()=>{vi.resetAllMocks();document.body.innerHTML=''})
it('keeps pending and previously published rollback controls separate and requires review acknowledgement',()=>{
 expect(modelActions('USER',version,null)).toEqual([])
 expect(modelActions('ADMIN',version,null)).toEqual(['publish','reject'])
 expect(modelActions('ADMIN',{...version,state:'OFFLINE',everPublished:true},null)).toContain('rollback')
 expect(canSubmitReview('publish','完整核对误差与基线并接受局限',false)).toBe(false)
 expect(canSubmitReview('publish','完整核对误差与基线并接受局限',true)).toBe(true)
})
it('shows aligned validation baseline and retrospective metrics without claiming accuracy',async()=>{
 vi.mocked(api.listModels).mockResolvedValue({items:[version],head})
 const wrapper=mount(ModelsPage,{global:{plugins:[ElementPlus]}})
 try{await flushPromises();expect(wrapper.text()).toContain('3.2000%');expect(wrapper.text()).toContain('3.2100%');expect(wrapper.text()).toContain('47.75%');expect(wrapper.text()).toContain('回顾性');expect(wrapper.text()).toContain('尚未设置推荐版本');expect(wrapper.text()).toContain('待审核')}finally{wrapper.unmount()}
})
it('requires acknowledgement then sends revision and reloads after an optimistic conflict',async()=>{
 vi.mocked(api.listModels).mockResolvedValueOnce({items:[version],head}).mockResolvedValueOnce({items:[{...version,state:'PUBLISHED',revision:1}],head:{...head,revision:1}})
 vi.mocked(api.modelAction).mockRejectedValue({status:409,message:'模型状态已更新，请重新审核'})
 const wrapper=mount(ModelsPage,{attachTo:document.body,global:{plugins:[ElementPlus],stubs:{Teleport:true}}})
 try{await flushPromises();await wrapper.find('[data-test="publish-model"]').trigger('click');await flushPromises();await wrapper.find('textarea').setValue('完整核对误差与基线并接受当前模型局限');expect(wrapper.find('[data-test="confirm-model-action"]').attributes('disabled')).toBeDefined();await wrapper.find('input[type="checkbox"]').setValue(true);await wrapper.find('[data-test="confirm-model-action"]').trigger('click');await flushPromises();expect(api.modelAction).toHaveBeenCalledWith(version.modelId,'publish',expect.objectContaining({expectedRevision:0,expectedHeadRevision:0,researchLimitationsAcknowledged:true}));expect(api.listModels).toHaveBeenCalledTimes(2);expect(wrapper.text()).toContain('模型状态已更新')}finally{wrapper.unmount()}
})

it('shows the rolling validation dates and holdout baseline under the pending candidate',async()=>{
 const span={from:'2025-07-01',through:'2026-09-24'}
 const rolling:api.ModelVersion={...version,artifactId:'transformer-s3-rolling-20241231-seed42-aaaaaaaaaaaaaaaa',
  manifest:{...version.manifest,trainingCutoff:'2024-12-31',trainingData:{train:span,validation:span,holdout:span}},
  evaluation:{sourceHash:'b'.repeat(64),scope:'ROLLING_OUT_OF_SAMPLE',seed:42,validationMae:'0.02',
   validationBaselineMae:'0.021',rolling:{datasetHash:'d'.repeat(64),holdoutMae:'0.023',holdoutBaselineMae:'0.022',
    holdoutDirection:'0.49',holdoutWindows:593}}}
 vi.mocked(api.listModels).mockResolvedValue({items:[rolling],head})
 const wrapper=mount(ModelsPage,{global:{plugins:[ElementPlus]}})
 try {
  await flushPromises()
  expect(wrapper.text()).toContain('留出期 2025-07-01 至 2026-09-24')
  expect(wrapper.text()).toContain('留出期基线 MAE')
  expect(wrapper.text()).toContain('593')
  expect(wrapper.text()).toContain('待审核')
 } finally {wrapper.unmount()}
})
