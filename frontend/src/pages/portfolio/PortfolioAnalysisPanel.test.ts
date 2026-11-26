import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from '../../stores/auth'
import {ApiError} from '../../api/auth'
import * as api from '../../api/portfolioAnalyses'
import Panel from './PortfolioAnalysisPanel.vue'
vi.mock('../../api/portfolioAnalyses',()=>({createPortfolioAnalysis:vi.fn(),portfolioAnalysis:vi.fn(),portfolioAnalysisOperation:vi.fn(),listPortfolioAnalyses:vi.fn()}))
const id='33333333-3333-4333-8333-333333333333',request='44444444-4444-4444-8444-444444444444'
const saved={analysisId:id,createdAt:'2026-09-28T13:00:00Z',formulaVersion:'portfolio-valuation-v1',bookRevision:'2',asOfDate:'2026-09-28',capturedAt:'2026-09-28T13:00:00Z',state:'COMPLETE',freshness:'UNKNOWN',mixedDates:false,positionCount:1,valuedCount:1,concentrationThresholdPercent:'50',sourceHash:'a'.repeat(64),subtotal:{costAmount:'2000.00',marketValue:'2500.00',floatingProfit:'500.00',returnPercent:'25.00'},totals:{costAmount:'2000.00',marketValue:'2500.00',floatingProfit:'500.00',returnPercent:'25.00'},lines:[{positionId:id,instrumentId:'SH600030',quantity:'100',averageCost:'20.000000',positionRevision:'1',quote:{instrumentId:'SH600030',status:'AVAILABLE',reason:null,close:'25.000000',priceDate:'2026-09-25',versionId:id,contentHash:'b'.repeat(64),provider:'baostock',priceBasis:'RAW',freshness:'UNKNOWN',calendarVersionId:null,calendarHash:null,checkedThrough:null,retainedPrice:false},costAmount:'2000.00',marketValue:'2500.00',floatingProfit:'500.00',returnPercent:'25.00',weightPercent:'100.00',concentrationTriggered:true}]} as api.PortfolioAnalysis
const operation={requestId:request,state:'SUCCEEDED',analysisId:id,errorCode:null,acceptedAt:'2026-09-28T13:00:00Z',expiresAt:'2026-09-28T13:00:30Z'} as api.PortfolioAnalysisOperation
const key='aistock:portfolio-analysis:owner'
function panel(revision:string|null='2'){return mount(Panel,{props:{currentBookRevision:revision},global:{stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'}}}})}
beforeEach(()=>{vi.resetAllMocks();sessionStorage.clear();setActivePinia(createPinia());useAuthStore().profile={userId:'owner',role:'USER'} as never;vi.mocked(api.listPortfolioAnalyses).mockResolvedValue({items:[saved],upper:'1',nextBefore:null});vi.mocked(api.portfolioAnalysis).mockResolvedValue(saved);vi.mocked(api.portfolioAnalysisOperation).mockImplementation(async rid=>({...operation,requestId:rid}));vi.mocked(api.createPortfolioAnalysis).mockImplementation(async rid=>({...operation,requestId:rid}))})
it('initial load and refresh read saved inputs without automatically creating analysis',async()=>{const w=panel();await flushPromises();expect(w.text()).toContain('2500.00');expect(w.text()).toContain('20.000000');expect(api.createPortfolioAnalysis).not.toHaveBeenCalled();w.unmount()})
it('explicit generation stores only the owner scoped request ID and refresh restores it',async()=>{const w=panel();await flushPromises();await w.get('[data-test="portfolio-generate"]').trigger('click');await flushPromises();expect(api.createPortfolioAnalysis).toHaveBeenCalledTimes(1);const rid=vi.mocked(api.createPortfolioAnalysis).mock.calls[0][0];expect(sessionStorage.getItem(key)).toBe(rid);expect(sessionStorage.length).toBe(1);expect(sessionStorage.getItem(key)).not.toContain('20.000000');w.unmount();vi.mocked(api.portfolioAnalysisOperation).mockResolvedValue({...operation,requestId:rid});const again=panel();await flushPromises();expect(api.portfolioAnalysisOperation).toHaveBeenCalledWith(rid);expect(api.createPortfolioAnalysis).toHaveBeenCalledTimes(1);again.unmount()})
it('unknown submission queries the same request instead of silently resubmitting',async()=>{vi.mocked(api.createPortfolioAnalysis).mockRejectedValue(new ApiError('NETWORK_UNAVAILABLE','连接失败',0));vi.mocked(api.portfolioAnalysisOperation).mockImplementation(async rid=>({...operation,requestId:rid,state:'RUNNING',analysisId:null}));const w=panel();await flushPromises();await w.get('[data-test="portfolio-generate"]').trigger('click');await flushPromises();expect(api.createPortfolioAnalysis).toHaveBeenCalledTimes(1);expect(api.portfolioAnalysisOperation).toHaveBeenCalledWith(vi.mocked(api.createPortfolioAnalysis).mock.calls[0][0]);expect(w.get('[data-test="portfolio-generate"]').attributes('disabled')).toBeDefined();expect(w.text()).toContain('2500.00');w.unmount()})
it('failed new generation preserves the selected historical result',async()=>{vi.mocked(api.createPortfolioAnalysis).mockImplementation(async rid=>({...operation,requestId:rid,state:'FAILED',analysisId:null,errorCode:'PORTFOLIO_CHANGED'}));const w=panel();await flushPromises();await w.get('[data-test="portfolio-generate"]').trigger('click');await flushPromises();expect(w.text()).toContain('2500.00');expect(w.text()).toContain('PORTFOLIO_CHANGED');w.unmount()})
it('current revision changes only the old snapshot notice and never changes its numbers',async()=>{const w=panel();await flushPromises();await w.setProps({currentBookRevision:'3'});expect(w.text()).toContain('持仓已修改');expect(w.text()).toContain('20.000000');await w.setProps({currentBookRevision:null});expect(w.text()).toContain('暂无法核对当前持仓版本');expect(api.createPortfolioAnalysis).not.toHaveBeenCalled();w.unmount()})
it('missing prices and mixed dates never display complete totals or invented zero',async()=>{vi.mocked(api.portfolioAnalysis).mockResolvedValue({...saved,state:'PARTIAL',valuedCount:0,totals:null,subtotal:{costAmount:null,marketValue:null,floatingProfit:null,returnPercent:null},lines:[{...saved.lines[0],marketValue:null,floatingProfit:null,returnPercent:null,weightPercent:null,concentrationTriggered:null,quote:{...saved.lines[0].quote,status:'UNAVAILABLE',reason:'NO_DATA',close:null}}]});const w=panel();await flushPromises();expect(w.text()).toContain('可估值部分合计');expect(w.text()).not.toContain('2500.00');expect(w.text()).not.toContain('100.00%');expect(w.get('[data-profit-contribution]').text()).toContain('无法计算');expect(w.find('[data-market-weight]').exists()).toBe(false);expect(w.text()).toContain('本行未计入小计');w.unmount()})
it('late result from another account is ignored',async()=>{let resolve!:(v:api.PortfolioAnalysis)=>void;vi.mocked(api.portfolioAnalysis).mockReturnValueOnce(new Promise(done=>{resolve=done}));const w=panel();await flushPromises();vi.mocked(api.listPortfolioAnalyses).mockResolvedValue({items:[],upper:null,nextBefore:null});useAuthStore().profile={userId:'other',role:'ADMIN'} as never;await flushPromises();resolve(saved);await flushPromises();expect(w.text()).not.toContain('20.000000');expect(w.text()).not.toContain('2500.00');w.unmount()})
it('history pagination retains the original upper bound',async()=>{vi.mocked(api.listPortfolioAnalyses).mockResolvedValueOnce({items:[saved],upper:'40',nextBefore:'20'}).mockResolvedValueOnce({items:[],upper:'40',nextBefore:null});const w=panel();await flushPromises();await w.get('[data-test="portfolio-more-history"]').trigger('click');await flushPromises();expect(api.listPortfolioAnalyses).toHaveBeenLastCalledWith('20','40');w.unmount()})

it('pauses new generation while another saved result is being read',async()=>{const w=panel();await flushPromises();vi.mocked(api.portfolioAnalysis).mockReturnValueOnce(new Promise(()=>{}));await w.get('.analysis-history ul button').trigger('click');await flushPromises();expect(w.get('[data-test="portfolio-generate"]').attributes('disabled')).toBeDefined();w.unmount()})
it('clears the uncertain-send warning after original success is recovered',async()=>{vi.mocked(api.createPortfolioAnalysis).mockRejectedValue(new ApiError('NETWORK_UNAVAILABLE','连接失败',0));const w=panel();await flushPromises();await w.get('[data-test="portfolio-generate"]').trigger('click');await flushPromises();expect(w.text()).toContain('2500.00');expect(w.text()).not.toContain('正在核对原请求。');w.unmount()})

it('labels mixed dates without showing complete portfolio weights',async()=>{const second={...saved.lines[0],positionId:request,instrumentId:'SH600276',quote:{...saved.lines[0].quote,instrumentId:'SH600276',priceDate:'2026-09-24'},weightPercent:null,concentrationTriggered:null};vi.mocked(api.portfolioAnalysis).mockResolvedValue({...saved,state:'MIXED_DATES',mixedDates:true,positionCount:2,valuedCount:2,totals:null,lines:[{...saved.lines[0],weightPercent:null,concentrationTriggered:null},second]});const w=panel();await flushPromises();expect(w.text()).toContain('可估值部分合计');expect(w.text()).toContain('价格日期不一致');expect(w.text()).not.toContain('市值占比 100.00%');w.unmount()})
it('shows an explicit empty snapshot with zero subtotal and no invented return',async()=>{vi.mocked(api.portfolioAnalysis).mockResolvedValue({...saved,state:'EMPTY',positionCount:0,valuedCount:0,totals:null,subtotal:{costAmount:'0.00',marketValue:'0.00',floatingProfit:'0.00',returnPercent:null},lines:[]});const w=panel();await flushPromises();expect(w.text()).toContain('空持仓快照');expect(w.text()).toContain('未读取报价');expect(w.text()).toContain('无法计算');expect(w.text()).not.toContain('25.00%');expect(api.createPortfolioAnalysis).not.toHaveBeenCalled();w.unmount()})

it('highlights signed profit contribution and complete-portfolio market weights',async()=>{
 const profitable=saved.lines[0]!
 const loss={...profitable,positionId:request,instrumentId:'SH600276',floatingProfit:'-200.00',marketValue:'500.00',weightPercent:'16.67',concentrationTriggered:false}
 const result={...saved,positionCount:2,valuedCount:2,lines:[{...profitable,weightPercent:'83.33'},loss]}
 vi.mocked(api.portfolioAnalysis).mockResolvedValue(result)
 const w=panel('3')
 try{
  await flushPromises()
  const lines=w.findAll('.valuation-line')
  expect(lines[0]?.get('[data-profit-contribution]').text()).toContain('+500.00 元')
  expect(lines[1]?.get('[data-profit-contribution]').text()).toContain('-200.00 元')
  expect(lines[1]?.get('[data-market-weight]').text()).toContain('16.67%')
  expect(w.get('details[aria-label="估值计算口径"]').text()).toContain('浮动盈亏＝市值－成本')
  expect(w.text()).toContain('持仓已修改')
 }finally{w.unmount()}
})

it('links a saved holding to current quotes without replacing the valuation',async()=>{
 const w=panel('3')
 try{
  await flushPromises()
  expect(w.get('[data-test="valuation-latest-market"]').attributes('href')).toBe('/market/SH600030')
  expect(w.text()).toContain('收盘价日期 2026-09-25')
  expect(w.text()).toContain('2500.00')
  expect(api.createPortfolioAnalysis).not.toHaveBeenCalled()
 }finally{w.unmount()}
})
