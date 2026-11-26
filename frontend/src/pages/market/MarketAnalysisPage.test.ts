import {expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createRouter,createMemoryHistory} from 'vue-router'
import ElementPlus from 'element-plus'
import MarketAnalysisPage from './MarketAnalysisPage.vue'
import * as api from '../../api/market'
vi.mock('../../api/market',()=>({instrument:vi.fn(),daily:vi.fn()}))
vi.mock('../../stores/watchlist',()=>({useWatchlistStore:()=>({items:[],load:vi.fn().mockResolvedValue(undefined),add:vi.fn(),remove:vi.fn()})}))
it('does not show the prior stock name when a new route fails',async()=>{
 const summary={instrumentId:'SH600030',state:'NO_DATA' as const,versionId:null,provider:null,priceBasis:'RAW' as const,lastTradeDate:null,close:null,freshness:'UNKNOWN' as const,lastUpdateError:null}
 vi.mocked(api.instrument).mockImplementation(async id=>{
  if(id==='SH600030')return {instrumentId:id,ticker:id,displayName:'旧股票名称',displayNameVerified:true,listingStatus:'ACTIVE',forecastCapability:'UNAVAILABLE',forecastReason:'未接通',summary}
  throw new Error('行情暂不可用')
 })
 vi.mocked(api.daily).mockResolvedValue({instrumentId:'SH600030',state:'EMPTY',versionId:null,provider:null,priceBasis:'RAW',lastTradeDate:null,fetchedAt:null,checkedThrough:null,requestedRange:{from:'2026-07-01',to:'2026-09-30'},actualRange:null,quality:'UNKNOWN',freshness:'UNKNOWN',lastUpdateError:null,formulaVersion:'tech-v1',missingTradingDates:[],explanation:'无日线',bars:[]})
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div/>'}},{path:'/market/:id',component:MarketAnalysisPage}]})
 await router.push('/market/SH600030');await router.isReady()
 const wrapper=mount(MarketAnalysisPage,{global:{plugins:[router,ElementPlus],stubs:{MarketChart:true,StockAiAnalysisPanel:true}}})
 try{
  await flushPromises();expect(wrapper.find('h1').text()).toBe('旧股票名称');expect(wrapper.find('a[href*=comparisons]').attributes('href')).toContain('SH600030')
  await router.push('/market/SZ000002');await flushPromises()
  expect(wrapper.find('h1').text()).toBe('SZ000002')
  expect(wrapper.text()).not.toContain('旧股票名称')
 }finally{wrapper.unmount()}
})

it('shows availability before the chart and keeps the full basis after the analysis',async()=>{
 const instrumentId='SH600030'
 const versionId='11111111-1111-1111-1111-111111111111'
 const date='2026-09-30'
 vi.mocked(api.instrument).mockResolvedValue({instrumentId,ticker:instrumentId,displayName:'中信证券',displayNameVerified:true,listingStatus:'ACTIVE',forecastCapability:'NOT_EVALUATED',forecastReason:'',summary:{instrumentId,state:'AVAILABLE',versionId,provider:'Baostock',priceBasis:'RAW',lastTradeDate:date,close:'20.00',freshness:'STALE',lastUpdateError:null}})
 const bar={date,open:'20',high:'20.5',low:'19',close:'20',volumeShares:null,amountYuan:null,tradeStatus:1,ma5:'20',ma10:null,ma20:null,dif:null,dea:null,macd:null,rsi14:null}
 vi.mocked(api.daily).mockResolvedValue({instrumentId,versionId,state:'AVAILABLE',provider:'Baostock',priceBasis:'RAW',lastTradeDate:date,fetchedAt:null,checkedThrough:null,requestedRange:{from:'2026-07-01',to:date},actualRange:{from:date,to:date},quality:'COMPLETE',freshness:'STALE',lastUpdateError:null,formulaVersion:'tech-v1',missingTradingDates:['2026-09-29'],explanation:'已保留上次发布数据',bars:[bar]})
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div/>'}},{path:'/market/:id',component:MarketAnalysisPage}]})
 await router.push('/market/'+instrumentId)
 const wrapper=mount(MarketAnalysisPage,{global:{plugins:[router,ElementPlus],stubs:{MarketChart:true,StockValuationPanel:true,StockAiAnalysisPanel:{template:'<section><h2>AI股票分析</h2></section>'}}}})
 try{
  await flushPromises()
  expect(wrapper.findAll('h2').map(heading=>heading.text())).toEqual(['行情状态','日线与技术指标','AI股票分析','行情数据依据'])
  expect(wrapper.get('[aria-labelledby="market-status-heading"]').text()).toContain('缺少预期交易日')
  expect(wrapper.get('[aria-label="技术指标说明"]').text()).toContain('相对强弱指标')
  const basis=wrapper.get('[aria-labelledby="market-basis-heading"]')
  expect(basis.text()).toContain(versionId)
  expect(basis.text()).toContain('2026-09-29')
  expect(basis.text()).toContain('样本不足')
 }finally{wrapper.unmount()}
})
