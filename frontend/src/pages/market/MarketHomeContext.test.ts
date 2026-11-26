import {flushPromises,mount} from '@vue/test-utils'
import {createPinia} from 'pinia'
import {createMemoryHistory,createRouter,useRoute} from 'vue-router'
import ElementPlus from 'element-plus'
import {expect,it,vi} from 'vitest'
import {searchInstruments,type Instrument} from '../../api/market'
import {listReturn} from '../../router/listContext'
import MarketHomePage from './MarketHomePage.vue'

vi.mock('../../api/market',()=>({searchInstruments:vi.fn(),summaries:vi.fn(async()=>[])}))
vi.mock('../../api/predictions',()=>({publishedModels:vi.fn(async()=>({items:[]})),supportsFiveDayPrediction:vi.fn(()=>false)}))
vi.mock('../../stores/watchlist',()=>({useWatchlistStore:()=>({items:[],load:vi.fn(async()=>{})})}))

it('uses the applied search query for the return link and loads it again',async()=>{
 const id='SH600030'
 vi.mocked(searchInstruments).mockResolvedValue({items:[{instrumentId:id,ticker:id,displayName:'中信证券',
  summary:{state:'NO_DATA'}} as Instrument],page:1,size:20,total:1})
 const analysis={setup(){const r=useRoute();return {back:listReturn(r.query.from)}},template:'<router-link :to="back">返回搜索</router-link>'}
 const router=createRouter({history:createMemoryHistory(),routes:[
  {path:'/',component:MarketHomePage},{path:'/market/:id',component:analysis},{path:'/watchlist',component:{template:'<div/>'}},
  {path:'/diagnostics',component:{template:'<div/>'}},
 ]})
 await router.push('/');await router.isReady()
 const wrapper=mount({template:'<router-view/>'},{global:{plugins:[router,createPinia(),ElementPlus],stubs:{
  MarketOverview:true,WatchlistCards:true,RecentPredictions:true,RecentMessages:true,
 }}})
 try{
  await flushPromises()
  expect(wrapper.get('section[aria-labelledby="home-watchlist-heading"] a[href="#stock-query"]').text()).toBe('去搜索股票')
  expect(wrapper.get('div[aria-label="近期动态"]').find('recent-predictions-stub').exists()).toBe(true)
  expect(wrapper.get('div[aria-label="近期动态"]').find('recent-messages-stub').exists()).toBe(true)
  const input=wrapper.get('input#stock-query')
  await input.setValue('中信')
  await input.trigger('keydown',{key:'Enter'})
  await flushPromises()
  expect(router.currentRoute.value.query.q).toBe('中信')
  await wrapper.findAll('a').find(a=>a.text()==='查看分析')!.trigger('click')
  await flushPromises()
  await wrapper.findAll('a').find(a=>a.text()==='返回搜索')!.trigger('click')
  await flushPromises()
  expect((wrapper.get('input#stock-query').element as HTMLInputElement).value).toBe('中信')
  expect(searchInstruments).toHaveBeenLastCalledWith('中信')
 }finally{wrapper.unmount()}
})

it('selects a real suggestion with the keyboard and preserves the query on return',async()=>{
 vi.mocked(searchInstruments).mockReset().mockImplementation(async value=>({items:value?[{
  instrumentId:'SH600519',ticker:'SH600519',displayName:'贵州茅台',summary:{state:'NO_DATA'}
 } as Instrument]:[],page:1,size:20,total:value?1:0}))
 const analysis={setup(){return {back:listReturn(useRoute().query.from)}},template:'<router-link :to="back">返回搜索</router-link>'}
 const router=createRouter({history:createMemoryHistory(),routes:[
  {path:'/',component:MarketHomePage},{path:'/market/:id',component:analysis},
  ...['/watchlist','/diagnostics'].map(path=>({path,component:{template:'<div/>'}})),
 ]})
 await router.push('/');await router.isReady()
 const wrapper=mount({template:'<router-view/>'},{global:{plugins:[router,createPinia(),ElementPlus],stubs:{
  MarketOverview:true,WatchlistCards:true,RecentPredictions:true,RecentMessages:true,
 }}})
 try{
  await flushPromises()
  const input=wrapper.get('#stock-query')
  await input.trigger('focus')
  await input.trigger('compositionstart')
  const element=input.element as HTMLInputElement
  element.value='mao'
  await input.trigger('input')
  await input.trigger('keydown',{key:'Enter',isComposing:true})
  await flushPromises()
  expect(searchInstruments).toHaveBeenCalledTimes(1)
  element.value='茅台'
  await input.trigger('compositionend')
  await input.trigger('keydown',{key:'Enter'})
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/')
  expect(vi.mocked(searchInstruments).mock.calls.filter(([q])=>q==='茅台')).toHaveLength(1)
  expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  await input.trigger('focus')
  await input.trigger('keydown',{key:'Escape'})
  expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  await input.trigger('keydown',{key:'ArrowDown'})
  const suggestion=wrapper.get('[role="option"]')
  expect(suggestion.text()).toBe('贵州茅台 · 600519 · 沪市')
  await input.trigger('keydown',{key:'ArrowDown'})
  expect(input.attributes('aria-activedescendant')).toBe(suggestion.attributes('id'))
  await input.trigger('keydown',{key:'Enter'})
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/market/SH600519')
  await wrapper.get('a').trigger('click')
  await flushPromises()
  expect((wrapper.get('#stock-query').element as HTMLInputElement).value).toBe('茅台')

  const currentInput=wrapper.get('#stock-query')
  let finishOld!:(payload:Awaited<ReturnType<typeof searchInstruments>>)=>void
  const oldPage=new Promise<Awaited<ReturnType<typeof searchInstruments>>>(resolve=>{finishOld=resolve})
  const newResults=Array.from({length:7},(_,index)=>({
   instrumentId:'SZ00000'+index,ticker:'SZ00000'+index,displayName:'新结果'+index,summary:{state:'NO_DATA'},
  } as Instrument))
  vi.mocked(searchInstruments).mockImplementation(async value=>{
   if(value==='旧词')return oldPage
   return {items:newResults,page:1,size:20,total:newResults.length}
  })
  await currentInput.trigger('focus')
  await currentInput.setValue('旧词')
  await currentInput.trigger('keydown',{key:'Enter'})
  await flushPromises()
  await currentInput.setValue('新词')
  expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
  await vi.waitFor(()=>expect(wrapper.findAll('[role="option"]')).toHaveLength(6))
  finishOld({items:[{...newResults[0]!,displayName:'过期结果'}],page:1,size:20,total:1})
  await flushPromises()
  expect(wrapper.findAll('[role="option"]')).toHaveLength(6)
  expect(wrapper.text()).not.toContain('过期结果')
  expect(vi.mocked(searchInstruments).mock.calls.filter(([q])=>q==='新词')).toHaveLength(1)
  await wrapper.findAll('[role="option"]')[1]!.trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/market/SZ000001')
 }finally{wrapper.unmount()}
})

it('distinguishes quote-only, daily and unavailable instruments in search results',async()=>{
 const item=(instrumentId:string,displayName:string,summary:Partial<Instrument['summary']>, forecastReason=''):Instrument=>({
  instrumentId,ticker:instrumentId.slice(2),displayName,displayNameVerified:true,listingStatus:'ACTIVE',
  forecastCapability:forecastReason?'UNAVAILABLE':'NOT_EVALUATED',forecastReason,
  summary:{instrumentId,state:'NO_DATA',versionId:null,provider:null,priceBasis:'RAW',lastTradeDate:null,
   close:null,freshness:'UNKNOWN',lastUpdateError:null,...summary},
 })
 vi.mocked(searchInstruments).mockResolvedValueOnce({items:[
  item('HK00700','腾讯控股',{},'港股试点暂不支持五日预测'),
  item('SH600030','中信证券',{state:'AVAILABLE',freshness:'FRESH',lastTradeDate:'2026-11-20'}),
  item('SH600036','招商银行',{}),
 ],page:1,size:20,total:3})
 const router=createRouter({history:createMemoryHistory(),routes:[
  {path:'/',component:MarketHomePage},
  {path:'/market/:id',component:{template:'<div/>'}},
  {path:'/watchlist',component:{template:'<div/>'}},
  {path:'/diagnostics',component:{template:'<div/>'}},
 ]})
 await router.push('/')
 const w=mount(MarketHomePage,{global:{plugins:[router,createPinia(),ElementPlus],stubs:{
  MarketOverview:true,WatchlistCards:true,RecentPredictions:true,RecentMessages:true,
 }}})
 try{
  await flushPromises()
  const [hk,daily,missing]=w.findAll('.result-row')
  expect(hk!.text()).toContain('盘中报价试点')
  expect(hk!.get('a').text()).toBe('查看盘中报价')
  expect(hk!.text()).toContain('暂不支持五日预测')
  expect(daily!.text()).toContain('可看日线')
  expect(missing!.text()).toContain('暂缺已发布日线')
 }finally{w.unmount()}
})
