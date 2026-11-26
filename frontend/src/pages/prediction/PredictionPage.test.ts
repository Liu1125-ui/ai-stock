import {mount,flushPromises} from '@vue/test-utils'
import {describe,it,expect,vi,beforeEach} from 'vitest'
import ElementPlus from 'element-plus'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import PredictionPage from './PredictionPage.vue'
import * as api from '../../api/predictions'
import { instrument } from '../../api/market'
const routeState=vi.hoisted(()=>({params:{id:'SH600030'},query:{from:'/watchlist?page=2'}}))
vi.mock('vue-router',()=>({useRoute:()=>routeState}))
vi.mock('../../api/predictions',async importOriginal=>({...await importOriginal<typeof import('../../api/predictions')>(),publishedModels:vi.fn(),createPrediction:vi.fn(),prediction:vi.fn()}))
vi.mock('../../api/market',()=>({instrument:vi.fn()}))
const availableStock={instrumentId:'SH600030',summary:{state:'AVAILABLE',lastTradeDate:'2026-10-23',freshness:'FRESH'}} as Awaited<ReturnType<typeof instrument>>
beforeEach(()=>{routeState.params.id="SH600030";vi.clearAllMocks();vi.mocked(instrument).mockResolvedValue(availableStock);sessionStorage.clear();setActivePinia(createPinia());useAuthStore().profile={userId:'user-one',role:'USER'} as never})
describe('five day prediction',()=>{
 it('explains empty published catalog without allowing a request',async()=>{vi.mocked(api.publishedModels).mockResolvedValue({items:[],headRevision:0});const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true}}});await flushPromises();expect(w.text()).toContain('暂无已发布');expect(w.get('[data-test="predict"]').attributes('disabled')).toBeDefined();w.unmount()})
 it('keeps original operation on uncertain network outcome and clears display on account change',async()=>{vi.mocked(api.publishedModels).mockResolvedValue({items:[{modelId:'33333333-3333-3333-3333-333333333333',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}],headRevision:1});vi.mocked(api.createPrediction).mockRejectedValue(new Error('连接失败'));const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true}}});await flushPromises();await w.get('[data-test="predict"]').trigger('click');await flushPromises();const first=vi.mocked(api.createPrediction).mock.calls[0][0];await w.get('[data-test="predict"]').trigger('click');await flushPromises();expect(vi.mocked(api.createPrediction).mock.calls[1][0]).toEqual(first);expect(first.horizon).toBe(5);useAuthStore().profile={userId:'user-two',role:'USER'} as never;await flushPromises();expect(w.text()).not.toContain('连接失败');w.unmount()})
})
it('clears a prior successful curve before starting another prediction that fails',async()=>{
 const {default:PredictionResult}=await import('./PredictionResult.vue');const base={modelId:'33333333-3333-3333-3333-333333333333',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}
 vi.mocked(api.publishedModels).mockResolvedValue({items:[base],headRevision:1});vi.mocked(api.createPrediction).mockResolvedValueOnce({predictionId:'old-success',state:'SUCCEEDED'} as api.PredictionRecord).mockRejectedValueOnce(new Error('本次连接失败'))
 const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,PredictionResult:{props:['record'],template:'<p data-test="result">{{record.predictionId}}</p>'}}}});await flushPromises();await w.get('[data-test="predict"]').trigger('click');await flushPromises();expect(w.get('[data-test="result"]').text()).toBe('old-success');await w.get('[data-test="predict"]').trigger('click');await flushPromises();expect(w.find('[data-test="result"]').exists()).toBe(false);w.unmount();void PredictionResult
})

it('opens the preselected stock and enables the return to its watchlist page without creating a request',async()=>{
 vi.mocked(api.publishedModels).mockResolvedValue({items:[],headRevision:0})
 const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:{
  props:['to'],template:'<a :data-page="to.query?.page"><slot/></a>',
 }}}})
 await flushPromises()
 expect(w.text()).toContain('SH600030 · 五交易日预测')
 expect(w.get('a[data-page="2"]').text()).toContain('返回自选股')
 expect(api.createPrediction).not.toHaveBeenCalled()
 w.unmount()
})

it('uses a read-only request to check a known running prediction',async()=>{
 const model={modelId:'m',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}
 const running={predictionId:'known',state:'RUNNING',expiresAt:'2026-11-17T11:00:00Z'} as api.PredictionRecord
 vi.mocked(api.publishedModels).mockResolvedValue({items:[model],headRevision:1})
 vi.mocked(api.createPrediction).mockResolvedValue(running)
 vi.mocked(api.prediction).mockResolvedValue({...running,state:'SUCCEEDED'})
 const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,PredictionResult:true}}})
 try{
  await flushPromises()
  await w.get('[data-test="predict"]').trigger('click')
  await flushPromises()
  expect(w.get('[data-test="predict"]').text()).toBe('查询原预测状态')
  await w.get('[data-test="predict"]').trigger('click')
  await flushPromises()
  expect(api.prediction).toHaveBeenCalledWith('known')
  expect(api.createPrediction).toHaveBeenCalledTimes(1)
 }finally{w.unmount()}
})

it('recovers a known prediction on return without resubmitting it',async()=>{
 const model={modelId:'m',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}
 const predictionId='11111111-1111-1111-1111-111111111111'
 const running={predictionId,state:'RUNNING'} as api.PredictionRecord
 vi.mocked(api.publishedModels).mockResolvedValue({items:[model],headRevision:1})
 vi.mocked(api.createPrediction).mockResolvedValue(running)
 vi.mocked(api.prediction).mockResolvedValue({...running,state:'SUCCEEDED'})
 const open=()=>mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,PredictionResult:{props:['record'],template:'<p data-test="result">{{record.state}}</p>'}}}})
 const first=open()
 await flushPromises()
 await first.get('[data-test="predict"]').trigger('click')
 await flushPromises()
 first.unmount()
 const second=open()
 try{
  await flushPromises()
  expect(api.prediction).toHaveBeenCalledWith(predictionId)
  expect(api.createPrediction).toHaveBeenCalledTimes(1)
  expect(second.get('[data-test="result"]').text()).toBe('SUCCEEDED')
 }finally{second.unmount()}
})

it('checks published market availability before allowing a new forecast',async()=>{
 const model={modelId:'m',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}
 vi.mocked(api.publishedModels).mockResolvedValue({items:[model],headRevision:1})
 vi.mocked(instrument).mockResolvedValue({...availableStock,summary:{...availableStock.summary,state:'NO_DATA',lastTradeDate:null}})
 const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,PredictionResult:true}}})
 try{
  await flushPromises()
  expect(instrument).toHaveBeenCalledWith('SH600030')
  expect(w.text()).toContain('暂无已发布行情')
  expect(w.get('[data-test="predict"]').attributes('disabled')).toBeDefined()
  expect(api.createPrediction).not.toHaveBeenCalled()
 }finally{w.unmount()}
})

it('submits only once while the same request is in flight',async()=>{
 const model={modelId:'m',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}
 vi.mocked(api.publishedModels).mockResolvedValue({items:[model],headRevision:1})
 let complete!:(record:api.PredictionRecord)=>void
 vi.mocked(api.createPrediction).mockReturnValue(new Promise(resolve=>{complete=resolve}))
 const w=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,PredictionResult:true}}})
 try{
  await flushPromises()
  const submit=w.get('[data-test="predict"]')
  await Promise.all([submit.trigger('click'),submit.trigger('click')])
  expect(api.createPrediction).toHaveBeenCalledTimes(1)
  expect(w.text()).toContain('正在准备预测输入')
  complete({predictionId:'done',state:'SUCCEEDED'} as api.PredictionRecord)
  await flushPromises()
 }finally{w.unmount()}
})

it('offers the Tencent quote page without requesting forecast preparation or recovery',async()=>{
 routeState.params.id='HK00700'
 vi.mocked(api.prediction).mockResolvedValue({state:'SUCCEEDED'} as api.PredictionRecord)
 const operationId='11111111-1111-1111-1111-111111111111'
 sessionStorage.setItem('aistock:prediction-request:user-one:HK00700',JSON.stringify({
  operationId,modelId:operationId,predictionId:operationId,instrumentId:'HK00700',horizon:5
 }))
 const view=mount(PredictionPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:{
  props:['to'],template:'<a :href="typeof to===\'string\'?to:to.path"><slot/></a>',
 }}}})
 try{
  await flushPromises()
  expect(view.text()).toContain('腾讯控股暂不支持五日预测')
  expect(view.get('a[href="/market/HK00700"]').text()).toContain('查看腾讯控股行情')
  expect(view.find('[data-test="predict"]').exists()).toBe(false)
  expect(api.publishedModels).not.toHaveBeenCalled()
  expect(instrument).not.toHaveBeenCalled()
  expect(api.prediction).not.toHaveBeenCalled()
  expect(api.createPrediction).not.toHaveBeenCalled()
 }finally{view.unmount()}
})
