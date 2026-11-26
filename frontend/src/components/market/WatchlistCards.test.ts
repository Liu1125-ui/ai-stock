import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createRouter,createMemoryHistory} from 'vue-router'
import ElementPlus from 'element-plus'
import WatchlistCards from './WatchlistCards.vue'
import {createPinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import PredictionPage from '../../pages/prediction/PredictionPage.vue'
import {publishedModels,createPrediction,latestStockPredictions,type PublishedModel} from '../../api/predictions'
import type {Summary} from '../../api/market'
vi.mock('../../api/predictions',async importOriginal=>({...await importOriginal<typeof import('../../api/predictions')>(),publishedModels:vi.fn(),createPrediction:vi.fn(),latestStockPredictions:vi.fn(async()=>[])}))
const items=[{instrumentId:'SH600030',createdAt:'2026-10-02T12:00:00Z'}]
const base={instrumentId:'SH600030',state:'AVAILABLE' as const,versionId:'version',provider:'baostock',priceBasis:'RAW' as const,lastTradeDate:'2026-09-30',close:'25.86',freshness:'FRESH' as 'FRESH'|'STALE'|'UNKNOWN',lastUpdateError:null}
const model:PublishedModel={modelId:'33333333-3333-3333-3333-333333333333',artifactId:'fixture',window:60,horizon:5,instruments:['SH600030'],recommended:true,featureDefinition:'six',trainingCutoff:'2023-12-31'}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(publishedModels).mockResolvedValue({items:[],headRevision:0})})
async function render(quotes:Record<string,Summary>={SH600030:base},pinia=createPinia()){
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div/>'}},{path:'/alerts',component:{template:'<div/>'}},{path:'/predictions',component:{template:'<div/>'}},{path:'/market/:id',component:{template:'<div/>'}},{path:'/market/:id/predict',component:{template:'<div>预测表单</div>'}}]})
 await router.push('/');await router.isReady()
 const wrapper=mount(WatchlistCards,{props:{items,summaries:quotes},global:{plugins:[router,ElementPlus,pinia]}})
 return {wrapper,router}
}
it('uses verified server metadata for watchlist names',async()=>{
 const quote={...base,displayName:'中信证券',displayNameVerified:true,listingStatus:'ACTIVE'}
 const {wrapper}=await render({SH600030:quote});await flushPromises()
 try{expect(wrapper.text()).toContain('中信证券');expect(wrapper.text()).toContain('名称已核验');expect(wrapper.text()).not.toContain('名称待核验')}finally{wrapper.unmount()}
})
it('keeps an explicit unverified label when metadata is unavailable',async()=>{
 const {wrapper}=await render({});await flushPromises()
 try{expect(wrapper.text()).toContain('SH600030');expect(wrapper.text()).toContain('名称待核验');expect(wrapper.text()).not.toContain('名称已核验')}finally{wrapper.unmount()}
})
it('navigates a supported stock to the existing five-day prediction form',async()=>{
 vi.mocked(publishedModels).mockResolvedValue({items:[model],headRevision:1})
 const {wrapper,router}=await render();await flushPromises()
 try{const link=wrapper.get('a[href="/market/SH600030/predict"]');expect(link.text()).toBe('发起预测');await link.trigger('click');await flushPromises();expect(router.currentRoute.value.path).toBe('/market/SH600030/predict')}finally{wrapper.unmount()}
})
it('does not offer a prediction link when no published model supports the stock',async()=>{
 vi.mocked(publishedModels).mockResolvedValue({items:[{...model,instruments:['SZ000002']}],headRevision:1})
 const {wrapper}=await render();await flushPromises()
 try{expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false);expect(wrapper.text()).toContain('暂无支持该股票的已发布五日模型');expect(wrapper.text()).not.toContain('预测模型尚未接通')}finally{wrapper.unmount()}
})
it('does not treat a ten-day model as support for the current five-day flow',async()=>{
 vi.mocked(publishedModels).mockResolvedValue({items:[{...model,horizon:10}],headRevision:1})
 const {wrapper}=await render();await flushPromises()
 try{expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false);expect(wrapper.text()).toContain('暂无支持该股票的已发布五日模型')}finally{wrapper.unmount()}
})
it('keeps prediction unavailable while catalog loading without hiding analysis',async()=>{
 let resolve!:(value:{items:PublishedModel[];headRevision:number})=>void
 vi.mocked(publishedModels).mockReturnValue(new Promise(done=>{resolve=done}))
 const {wrapper}=await render()
 try{expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false);expect(wrapper.text()).toContain('正在检查预测能力');expect(wrapper.get('a[href="/market/SH600030"]').text()).toBe('查看分析');resolve({items:[model],headRevision:1});await flushPromises();expect(wrapper.get('a[href$="/predict"]').text()).toBe('发起预测')}finally{wrapper.unmount()}
})
it('keeps quotes and removal available after catalog failure and supports read-only retry',async()=>{
 vi.mocked(publishedModels).mockRejectedValueOnce(new Error('模型目录连接失败')).mockResolvedValueOnce({items:[model],headRevision:1})
 const {wrapper}=await render();await flushPromises()
 try{expect(wrapper.text()).toContain('暂无法确认预测模型');expect(wrapper.text()).toContain('25.86');expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false);await wrapper.findAll('button').find(b=>b.text()==='移除自选')!.trigger('click');expect(wrapper.emitted('remove')).toEqual([['SH600030']]);await wrapper.findAll('button').find(b=>b.text()==='重新检查预测入口')!.trigger('click');await flushPromises();expect(wrapper.get('a[href$="/predict"]').text()).toBe('发起预测');expect(wrapper.text()).not.toContain('暂无法确认预测模型')}finally{wrapper.unmount()}
})
it('updates stock eligibility after page items change',async()=>{
 vi.mocked(publishedModels).mockResolvedValue({items:[model],headRevision:1})
 const {wrapper}=await render();await flushPromises()
 try{expect(wrapper.find('a[href$="/predict"]').exists()).toBe(true);await wrapper.setProps({items:[{instrumentId:'SZ000002',createdAt:items[0].createdAt}],summaries:{}});expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false);expect(wrapper.get('a[href="/market/SZ000002"]').text()).toBe('查看分析')}finally{wrapper.unmount()}
})

it('loads the real destination after catalog retry without submitting a prediction',async()=>{
 vi.mocked(publishedModels).mockRejectedValueOnce(new Error('目录暂不可用')).mockResolvedValue({items:[model],headRevision:1})
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:WatchlistCards,props:{items,summaries:{SH600030:base}}},{path:'/market/:id/predict',component:PredictionPage}]})
 const pinia=createPinia();useAuthStore(pinia).profile={userId:'test-owner',role:'USER'} as never
 await router.push('/');await router.isReady()
 const wrapper=mount({template:'<router-view/>'},{global:{plugins:[router,pinia,ElementPlus]}})
 try{
  await flushPromises();expect(createPrediction).not.toHaveBeenCalled()
  await wrapper.findAll('button').find(b=>b.text()==='重新检查预测入口')!.trigger('click');await flushPromises()
  expect(createPrediction).not.toHaveBeenCalled()
  await wrapper.get('a[href="/market/SH600030/predict"]').trigger('click');await flushPromises()
  expect(wrapper.text()).toContain('SH600030 · 五交易日预测')
  expect(wrapper.get('[data-test="predict"]').attributes('disabled')).toBeUndefined()
  expect(createPrediction).not.toHaveBeenCalled()
 }finally{wrapper.unmount()}
})

it('shows freshness, preselects the alert instrument and blocks predictions when quotes are missing',async()=>{
 vi.mocked(publishedModels).mockResolvedValue({items:[model],headRevision:1})
 const {wrapper}=await render({SH600030:{...base,freshness:'STALE' as typeof base.freshness}})
 await flushPromises()
 try{
  expect(wrapper.text()).toContain('行情待更新')
  const link=wrapper.findComponent({name:'RouterLink'})
  expect(link.exists()).toBe(true)
  expect(wrapper.findAll('a').some(a=>a.attributes('href')==='/alerts?instrumentId=SH600030')).toBe(true)
  await wrapper.setProps({summaries:{}})
  expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('暂缺已发布日线')
 }finally{wrapper.unmount()}
})

it('shows the verified daily change and links to the current owners latest prediction',async()=>{
 const pinia=createPinia()
 const auth=useAuthStore(pinia)
 auth.profile={userId:'first',role:'USER'} as never
 vi.mocked(latestStockPredictions).mockResolvedValueOnce([{
  instrumentId:'SH600030', predictionId:'original-record', state:'SUCCEEDED', horizon:5,createdAt:'2026-11-13T08:00:00Z',
 }]).mockResolvedValueOnce([])
 const {wrapper}=await render({SH600030:{...base,dailyChangePercent:'-0.9321'}},pinia)
 try{
  await flushPromises()
  expect(wrapper.text()).toContain('下跌 0.9321%')
  expect(wrapper.get('a[href="/predictions?predictionId=original-record"]').text()).toContain('已生成')
  auth.profile={userId:'second',role:'USER'} as never
  await flushPromises()
  expect(wrapper.find('a[href*="original-record"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('暂无本人预测')
 }finally{wrapper.unmount()}
})

it('uses the preparation report before showing a prediction link',async()=>{
 vi.mocked(publishedModels).mockResolvedValue({items:[model],headRevision:1})
 const {wrapper}=await render({SH600030:{...base,forecastReadiness:'INSUFFICIENT_HISTORY'}})
 try{
  await flushPromises()
  expect(wrapper.find('a[href$="/predict"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('有效日线不足 60 个交易日')
  await wrapper.setProps({summaries:{SH600030:{...base,forecastReadiness:'READY'}}})
  expect(wrapper.find('a[href$="/predict"]').exists()).toBe(true)
  expect(wrapper.text()).toContain('五日预测输入已就绪')
 }finally{wrapper.unmount()}
})
