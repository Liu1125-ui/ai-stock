import {mount,flushPromises} from '@vue/test-utils'
import {it,expect,vi,beforeEach} from 'vitest'
import ElementPlus from 'element-plus'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import HistoryPage from './HistoryPage.vue'
import * as api from '../../api/predictions'
import {summaries} from '../../api/market'
vi.mock('../../api/market',()=>({summaries:vi.fn()}))
beforeEach(()=>{vi.mocked(summaries).mockResolvedValue([])})
vi.mock('../../api/predictions',()=>({predictionHistory:vi.fn(),prediction:vi.fn(),hidePrediction:vi.fn(),predictionFailure:()=> '未完成'}))
const routeQuery=vi.hoisted(()=>({predictionId:undefined as string|undefined}));
vi.mock('vue-router',()=>({useRoute:()=>({query:routeQuery})}));
const record={predictionId:'one',operationId:'op',modelId:'model',instrumentId:'SH600030',horizon:5,state:'FAILED',errorCode:'MODEL_NOT_PUBLISHED',hidden:false,createdAt:'2026-10-05',expiresAt:'2026-10-05',modelSnapshot:{artifactId:'fixture-one',manifestHash:'a',checkpointHash:'b',revision:1,manifest:{trainingCutoff:'2023-12-31'}},snapshot:null} as api.PredictionRecord
beforeEach(()=>{vi.clearAllMocks();routeQuery.predictionId=undefined;setActivePinia(createPinia());useAuthStore().profile={userId:'user-one',role:'USER'} as never;vi.mocked(api.predictionHistory).mockResolvedValue({items:[record,{...record,predictionId:'two',instrumentId:'SH600276',modelSnapshot:{...record.modelSnapshot,artifactId:'fixture-two'}}],total:2,page:1,size:20})})
it('keeps the latest selected detail when older responses arrive late',async()=>{let first!:(r:api.PredictionRecord)=>void;vi.mocked(api.prediction).mockImplementation(id=>id==='one'?new Promise(resolve=>{first=resolve}):Promise.resolve({...record,predictionId:'two',modelSnapshot:{...record.modelSnapshot,artifactId:'fixture-two'}}));const w=mount(HistoryPage,{global:{plugins:[ElementPlus]}});await flushPromises();const buttons=w.findAll('button').filter(b=>b.text()==='查看详情');await buttons[0].trigger('click');await buttons[1].trigger('click');await flushPromises();first(record);await flushPromises();expect(w.find('details').text()).toContain('fixture-two');expect(w.find('details').text()).not.toContain('fixture-one');w.unmount()})
it('hides only the requested personal record and reloads its list',async()=>{vi.spyOn(window,'confirm').mockReturnValue(true);vi.mocked(api.hidePrediction).mockResolvedValue({...record,hidden:true});const w=mount(HistoryPage,{global:{plugins:[ElementPlus]}});await flushPromises();await w.findAll('button').find(b=>b.text()==='隐藏')!.trigger('click');await flushPromises();expect(api.hidePrediction).toHaveBeenCalledWith('one');expect(api.predictionHistory).toHaveBeenCalledTimes(2);w.unmount();vi.restoreAllMocks()})

it('opens the owner checked original prediction from an analysis link',async()=>{const id='11111111-1111-4111-8111-111111111111';routeQuery.predictionId=id;vi.mocked(api.prediction).mockResolvedValue({...record,predictionId:id});const w=mount(HistoryPage,{global:{plugins:[ElementPlus]}});await flushPromises();expect(api.prediction).toHaveBeenCalledWith(id);expect(w.find('details').exists()).toBe(true);w.unmount()})
it('does not use malformed deep link as a prediction identifier',async()=>{routeQuery.predictionId='bad';const w=mount(HistoryPage,{global:{plugins:[ElementPlus]}});await flushPromises();expect(api.prediction).not.toHaveBeenCalled();w.unmount()})

it('keeps the open prediction when the list and detail refresh fail',async()=>{
 vi.mocked(api.prediction).mockResolvedValueOnce(record).mockRejectedValueOnce(new Error('detail offline'))
 const initialList={items:[record],total:1,page:1,size:20}
 vi.mocked(api.predictionHistory).mockReset().mockResolvedValueOnce(initialList).mockRejectedValueOnce(new Error('list offline'))
 const w=mount(HistoryPage,{global:{plugins:[ElementPlus]}})
 try{
  await flushPromises()
  await w.findAll('button').find(button=>button.text()==='查看详情')!.trigger('click')
  await flushPromises()
  await w.findAll('button').find(button=>button.text()==='刷新记录')!.trigger('click')
  await flushPromises()
  expect(w.find('details').text()).toContain('fixture-one')
  expect(w.text()).toContain('读取预测记录失败')
  expect(w.text()).toContain('读取预测详情失败')
 }finally{w.unmount()}
})

it('shows verified Chinese names for successful and failed history entries',async()=>{
 vi.mocked(api.predictionHistory).mockResolvedValue({items:[
  {...record,instrumentId:'SH600519',state:'SUCCEEDED'},
  {...record,predictionId:'two',instrumentId:'SH600276'},
  {...record,predictionId:'three',instrumentId:'SH600276'},
  {...record,predictionId:'four',instrumentId:'SH600030'},
 ],total:4,page:1,size:20})
 vi.mocked(summaries).mockResolvedValue([
  {instrumentId:'SH600519',displayName:'贵州茅台',displayNameVerified:true},
  {instrumentId:'SH600276',displayName:'恒瑞医药',displayNameVerified:true},
  {instrumentId:'SH600030',displayName:'SH600030',displayNameVerified:false},
 ] as Awaited<ReturnType<typeof summaries>>)
 const w=mount(HistoryPage,{global:{plugins:[ElementPlus]}})
 try{
  await flushPromises()
  expect(summaries).toHaveBeenCalledOnce()
  expect(summaries).toHaveBeenCalledWith(['SH600519','SH600276','SH600030'])
  expect(w.findAll('.history-list strong').map(node=>node.text())).toEqual([
   '贵州茅台 · 5交易日','恒瑞医药 · 5交易日','恒瑞医药 · 5交易日','股票名称暂不可用 · 5交易日',
  ])
 }finally{w.unmount()}
})
