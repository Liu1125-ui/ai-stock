import {expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import {createPinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import {createRouter,createMemoryHistory} from 'vue-router'
import ElementPlus,{ElPagination} from 'element-plus'
import WatchlistPage from './WatchlistPage.vue'
import {summaries,type Summary} from '../../api/market'
import {useWatchlistStore} from '../../stores/watchlist'
vi.mock('../../api/predictions',async importOriginal=>({...await importOriginal<typeof import('../../api/predictions')>(),publishedModels:vi.fn(async()=>({items:[],headRevision:0}))}))
vi.mock('../../api/market',()=>({summaries:vi.fn()}))
vi.mock('../../stores/watchlist',()=>({useWatchlistStore:vi.fn()}))
it('keeps the newer page quotes when an older quote request finishes later',async()=>{
 const a='SH600030',b='SZ000002'
 const store=reactive({items:[] as {instrumentId:string;createdAt:string}[],total:21,page:1,
  load:vi.fn(async(page=1)=>{store.page=page;store.items=[{instrumentId:page===1?a:b,createdAt:'2026-09-24T00:00:00Z'}]}),
  remove:vi.fn()})
 vi.mocked(useWatchlistStore).mockReturnValue(store as unknown as ReturnType<typeof useWatchlistStore>)
 let resolveOld!:(value:Summary[])=>void
 const old=new Promise<Summary[]>(resolve=>{resolveOld=resolve})
 const make=(instrumentId:string,close:string):Summary=>({instrumentId,state:'AVAILABLE',versionId:'v',provider:'baostock',priceBasis:'RAW',lastTradeDate:'2026-09-24',close,freshness:'UNKNOWN',lastUpdateError:null})
 vi.mocked(summaries).mockReturnValueOnce(old).mockResolvedValueOnce([make(b,'22.000000')])
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:{template:'<div/>'}},{path:'/watchlist',component:WatchlistPage},{path:'/market/:id',component:{template:'<div/>'}}]})
 await router.push('/watchlist');await router.isReady()
 const wrapper=mount(WatchlistPage,{global:{plugins:[router,ElementPlus,createPinia()]}})
 try{
  await flushPromises()
  expect(wrapper.find('a[href*=comparisons]').attributes('href')).toBe('/comparisons?source=watchlist')
  wrapper.findComponent(ElPagination).vm.$emit('current-change',2)
  await flushPromises()
  resolveOld([make(a,'11.000000')]);await flushPromises()
  expect(wrapper.text()).toContain('22.00')
  expect(wrapper.text()).not.toContain('11.00')
 }finally{wrapper.unmount()}
})

it('returns to the same watchlist page and restores scroll after the list loads',async()=>{
 const id='SH600030'
 const store=reactive({items:[{instrumentId:id,createdAt:'2026-09-24T00:00:00Z'}],total:60,page:1,
  load:vi.fn(async(page=1)=>{store.page=page}),remove:vi.fn()})
 vi.mocked(useWatchlistStore).mockReturnValue(store as unknown as ReturnType<typeof useWatchlistStore>)
 vi.mocked(summaries).mockResolvedValue([])
 const pinia=createPinia()
 const auth=useAuthStore(pinia)
 auth.profile={userId:'return-test',nickname:'测试'} as typeof auth.profile
 const router=createRouter({history:createMemoryHistory(),routes:[
  {path:'/watchlist',component:WatchlistPage},{path:'/market/:id',component:{template:'<div>分析</div>'}},
  {path:'/',component:{template:'<div/>'}},{path:'/alerts',component:{template:'<div/>'}}
 ]})
 await router.push('/watchlist?page=3')
 await router.isReady()
 const scroll=vi.spyOn(window,'scrollTo').mockImplementation(()=>{})
 vi.stubGlobal('scrollY',240)
 const wrapper=mount({template:'<router-view/>'},{global:{plugins:[router,pinia,ElementPlus]}})
 try{
  await flushPromises()
  const analysis=wrapper.findAll('a').find(link=>link.text()==='查看分析')!
  await analysis.trigger('click')
  await flushPromises()
  expect(router.currentRoute.value.query.from).toBe('/watchlist?page=3')
  await router.push(String(router.currentRoute.value.query.from))
  await flushPromises()
  expect(store.load).toHaveBeenLastCalledWith(3)
  expect(scroll).toHaveBeenLastCalledWith(0,240)
 }finally{wrapper.unmount();scroll.mockRestore();vi.unstubAllGlobals()}
})
