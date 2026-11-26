import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createRouter,createMemoryHistory} from 'vue-router'
import ElementPlus,{ElSelect} from 'element-plus'
import ComparisonPage from './ComparisonPage.vue'
import * as api from '../../api/comparisons'
import {options,result} from './fixtures'
import {listWatchlist} from '../../api/watchlist'
import {listPositions} from '../../api/positions'
vi.mock('../../api/comparisons',()=>({comparisonOptions:vi.fn(),createComparison:vi.fn(),comparisonOperation:vi.fn(),comparisonResult:vi.fn(),comparisonHistory:vi.fn()}))
vi.mock('../../api/market',()=>({searchInstruments:vi.fn().mockResolvedValue({items:[{instrumentId:'SH600030',displayName:'中信证券'},{instrumentId:'SH600276',displayName:'恒瑞医药'}]})}))
vi.mock('../../stores/auth',()=>({useAuthStore:()=>({profile:{userId:'owner'}})}))
vi.mock('../../api/watchlist',()=>({listWatchlist:vi.fn()}))
vi.mock('../../api/positions',()=>({listPositions:vi.fn()}))
beforeEach(()=>{sessionStorage.clear();vi.mocked(api.comparisonOptions).mockResolvedValue(options);vi.mocked(api.comparisonResult).mockResolvedValue(result)})
async function page(url='/comparisons?ids=SH600030,SH600276'){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/comparisons',component:ComparisonPage}]});await router.push(url);await router.isReady();return {router,w:mount(ComparisonPage,{global:{plugins:[router,ElementPlus],stubs:{ComparisonResult:true,ComparisonHistory:true,ComparisonAiPanel:true}}})}}
it('defaults to three months and prevents duplicate creation while preserving timed-out input',async()=>{let fail!:(e:Error)=>void;vi.mocked(api.createComparison).mockImplementationOnce(()=>new Promise((_,reject)=>{fail=reject}));const {w}=await page();await flushPromises();expect(api.comparisonOptions).toHaveBeenCalledWith(['SH600030','SH600276'],'3M');const button=w.get('[data-test="compare-start"]');await button.trigger('click');await button.trigger('click');expect(api.createComparison).toHaveBeenCalledTimes(1);fail(new Error('连接失败'));await flushPromises();expect(w.text()).toContain('连接失败');expect(sessionStorage.length).toBe(1);expect(w.text()).toContain('查询原请求状态');w.unmount()})
it('reloads fixed saved result without creating or changing sources',async()=>{vi.mocked(api.createComparison).mockClear();const {w}=await page('/comparisons?comparisonId='+result.comparisonId);await flushPromises();expect(api.comparisonResult).toHaveBeenCalledWith(result.comparisonId);expect(api.createComparison).not.toHaveBeenCalled();w.unmount()})

it('ignores late source responses after selection changes',async()=>{let old!:(v:typeof options)=>void;vi.mocked(api.comparisonOptions).mockReturnValueOnce(new Promise(resolve=>{old=resolve}));const {w}=await page();await flushPromises();vi.mocked(api.comparisonOptions).mockResolvedValueOnce({...options,from:'2026-07-08',to:'2026-10-08'});w.findAllComponents(ElSelect)[0]!.vm.$emit('update:modelValue',['SH600030','SZ000002']);await flushPromises();old(options);await flushPromises();expect(w.text()).toContain('拟比较区间：2026-07-08');expect(w.text()).not.toContain('拟比较区间：2026-07-09');w.unmount()})
it('keeps selections and disables creation when a stock has insufficient coverage',async()=>{vi.mocked(api.comparisonOptions).mockResolvedValueOnce({...options,available:false,stocks:options.stocks.map((s,i)=>({...s,reason:i===1?'INSUFFICIENT_COVERAGE':null}))});const {w}=await page();await flushPromises();expect(w.text()).toContain('数据未覆盖所选周期');expect(w.get('[data-test="compare-start"]').attributes('disabled')).toBeDefined();expect(w.text()).toContain('SH600276');w.unmount()})
it.each(['missing','running','expired','failed'] as const)('refresh restores %s operation with no automatic post',async state=>{const original={requestId:'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',instrumentIds:['SH600030','SH600276'],from:'2026-07-09',to:'2026-10-09',evidenceIds:options.stocks.map(s=>s.evidenceId!)};sessionStorage.setItem('aistock:comparison-pending:owner',JSON.stringify(original));vi.mocked(api.createComparison).mockClear();if(state==='missing')vi.mocked(api.comparisonOperation).mockRejectedValueOnce(Object.assign(new Error('not found'),{status:404}));else vi.mocked(api.comparisonOperation).mockResolvedValueOnce({requestId:original.requestId,state:state==='failed'?'FAILED':'RUNNING',comparisonId:null,errorCode:state==='failed'?'COMPARISON_MARKET_UNAVAILABLE':null,leaseUntil:'2026-10-13T00:00:00Z',expired:state==='expired'});const {w}=await page('/comparisons');await flushPromises();expect(api.comparisonOperation).toHaveBeenCalledWith(original.requestId);expect(api.createComparison).not.toHaveBeenCalled();if(state==='missing')expect(w.text()).toContain('重试原请求');if(state==='expired')expect(w.text()).toContain('恢复原请求');if(state==='failed')expect(w.text()).toContain('准备重新计算');if(state==='running')expect(w.text()).not.toContain('恢复原请求');w.unmount()})

it('ignores historical detail arriving after a new result completes',async()=>{let old!:(v:typeof result)=>void;vi.mocked(api.comparisonResult).mockReturnValueOnce(new Promise(resolve=>{old=resolve}));const fresh={...result,comparisonId:'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'};vi.mocked(api.comparisonResult).mockResolvedValueOnce(fresh);vi.mocked(api.createComparison).mockResolvedValueOnce({requestId:'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',state:'SUCCEEDED',comparisonId:fresh.comparisonId,errorCode:null,leaseUntil:null,expired:false});const {w,router}=await page('/comparisons?ids=SH600030,SH600276&comparisonId='+result.comparisonId);await flushPromises();await w.get('[data-test="compare-start"]').trigger('click');await flushPromises();old(result);await flushPromises();expect(router.currentRoute.value.query.comparisonId).toBe(fresh.comparisonId);expect(w.findComponent({name:'ComparisonResult'}).props('result').comparisonId).toBe(fresh.comparisonId);w.unmount()})

it('prepares a fresh comparison from saved stocks without submitting or replacing history',async()=>{
 const saved={...result,stocks:[...result.stocks,{...result.stocks[0]!,instrumentId:'SH600276'}]}
 vi.mocked(api.comparisonResult).mockResolvedValueOnce(saved)
 vi.mocked(api.comparisonOptions).mockClear()
 vi.mocked(api.createComparison).mockClear()
 const {w,router}=await page('/comparisons?comparisonId='+result.comparisonId)
 await flushPromises()
 await w.get('[data-test="comparison-prepare-new"]').trigger('click')
 await flushPromises()
 expect(api.comparisonOptions).toHaveBeenCalledWith(saved.stocks.map(s=>s.instrumentId),'3M')
 expect(api.createComparison).not.toHaveBeenCalled()
 expect(router.currentRoute.value.query.comparisonId).toBe(result.comparisonId)
 expect(w.findComponent({name:'ComparisonResult'}).props('result').comparisonId).toBe(result.comparisonId)
 w.unmount()
})

it('allows choosing across owned sources without automatically creating a comparison',async()=>{
 vi.mocked(api.createComparison).mockClear()
 vi.mocked(listWatchlist).mockResolvedValue({items:[{instrumentId:'SH600030',createdAt:''},{instrumentId:'HK00700',createdAt:''}],page:1,size:20,total:2})
 vi.mocked(listPositions).mockResolvedValue({bookRevision:'1',items:[{instrumentId:'SH600276'} as Awaited<ReturnType<typeof listPositions>>['items'][number]]})
 const {w}=await page('/comparisons?source=watchlist')
 try {
  await flushPromises()
  expect(w.find('[data-source-stock="HK00700"]').exists()).toBe(false)
  await w.get('[data-source-stock="SH600030"]').trigger('click')
  await w.get('[data-source="portfolio"]').trigger('click')
  await flushPromises()
  await w.get('[data-source-stock="SH600276"]').trigger('click')
  await flushPromises()
  expect(api.comparisonOptions).toHaveBeenLastCalledWith(['SH600030','SH600276'],'3M')
  expect(api.createComparison).not.toHaveBeenCalled()
 }finally{w.unmount()}
})
