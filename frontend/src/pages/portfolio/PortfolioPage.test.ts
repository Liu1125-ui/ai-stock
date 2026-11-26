import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {createPinia,setActivePinia} from 'pinia'
import ElementPlus from 'element-plus'
import {createMemoryHistory,createRouter} from 'vue-router'
import {useAuthStore} from '../../stores/auth'
import {ApiError} from '../../api/auth'
import * as api from '../../api/positions'
import {searchInstruments} from '../../api/market'
import PortfolioPage from './PortfolioPage.vue'
vi.mock('../../api/positions',()=>({listPositions:vi.fn(),createPosition:vi.fn(),updatePosition:vi.fn(),deletePosition:vi.fn(),positionOperation:vi.fn()}))
vi.mock('../../api/market',()=>({searchInstruments:vi.fn()}))
vi.mock('../../api/predictions',async original=>({...await original<typeof import('../../api/predictions')>(),publishedModels:vi.fn(async()=>({items:[],headRevision:0}))}))
const row={positionId:'33333333-3333-3333-3333-333333333333',instrumentId:'SH600030',quantity:'1',averageCost:'123.456789',revision:'1',createdAt:'2026-10-18T00:00:00Z',updatedAt:'2026-10-18T00:00:00Z'}
const receipt={requestId:'req',action:'CREATE',state:'CREATED',httpStatus:201,code:'OK',message:'操作已保存',positionId:row.positionId,bookRevision:'1',position:row}
const mountPage=()=>mount(PortfolioPage,{global:{plugins:[ElementPlus],stubs:{PortfolioAnalysisPanel:true,RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'}}}})
beforeEach(()=>{vi.resetAllMocks();setActivePinia(createPinia());useAuthStore().profile={userId:'owner',role:'USER'} as never;vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'0',items:[]});vi.mocked(searchInstruments).mockResolvedValue({items:[{instrumentId:'SH600030',displayName:'中信证券',displayNameVerified:true}],total:1,page:1,size:20} as never)})
async function fill(w:ReturnType<typeof mountPage>){await w.get('[data-test="position-stock"]').setValue('SH600030');await w.get('[data-test="position-quantity"]').setValue('1');await w.get('[data-test="position-cost"]').setValue('123.456789')}
it('loads empty holdings without writing or inventing valuation',async()=>{const w=mountPage();await flushPromises();try{expect(w.text()).toContain('还没有持仓记录');expect(w.findComponent({name:'PortfolioAnalysisPanel'}).exists()).toBe(true);expect(api.createPosition).not.toHaveBeenCalled();expect(api.updatePosition).not.toHaveBeenCalled();expect(api.deletePosition).not.toHaveBeenCalled()}finally{w.unmount()}})
it('creates exact input and reloads persisted holdings',async()=>{vi.mocked(api.createPosition).mockResolvedValue(receipt);const w=mountPage();await flushPromises();try{await fill(w);vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'1',items:[row]});await w.get('[data-test="position-save"]').trigger('submit');await flushPromises();expect(api.createPosition).toHaveBeenCalledWith(expect.objectContaining({quantity:'1',averageCost:'123.456789',instrumentId:'SH600030'}));expect(w.text()).toContain('123.456789')}finally{w.unmount()}})
it('requires confirmation and sends current revision on removal',async()=>{vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'1',items:[row]});vi.mocked(api.deletePosition).mockResolvedValue({...receipt,action:'DELETE',state:'DELETED',position:null,bookRevision:'2'});const w=mountPage();await flushPromises();try{await w.get('[data-test="position-remove"]').trigger('click');expect(api.deletePosition).not.toHaveBeenCalled();vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'2',items:[]});await w.get('[data-test="position-confirm-remove"]').trigger('click');await flushPromises();expect(api.deletePosition).toHaveBeenCalledWith(row.positionId,expect.objectContaining({expectedRevision:'1'}));expect(w.text()).toContain('还没有持仓记录')}finally{w.unmount()}})
it('preserves edit input after conflict and requires explicit reload',async()=>{vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'1',items:[row]});vi.mocked(api.updatePosition).mockRejectedValue(new ApiError('POSITION_REVISION_CONFLICT','持仓已被修改',409));const w=mountPage();await flushPromises();try{await w.get('[data-test="position-edit"]').trigger('click');await w.get('[data-test="position-quantity"]').setValue('2');await w.get('[data-test="position-save"]').trigger('submit');await flushPromises();expect(w.text()).toContain('持仓已被修改');expect((w.get('[data-test="position-quantity"]').element as HTMLInputElement).value).toBe('2');expect(w.get('[data-test="position-save"]').attributes('disabled')).toBeDefined()}finally{w.unmount()}})
it('queries uncertain operation and retries only the original payload',async()=>{vi.mocked(api.createPosition).mockRejectedValueOnce(new ApiError('NETWORK_UNAVAILABLE','连接失败',0)).mockResolvedValueOnce(receipt);vi.mocked(api.positionOperation).mockRejectedValueOnce(new ApiError('RESOURCE_NOT_FOUND','尚无回执',404));const w=mountPage();await flushPromises();try{await fill(w);await w.get('[data-test="position-save"]').trigger('submit');await flushPromises();const original=vi.mocked(api.createPosition).mock.calls[0][0];await w.get('[data-test="position-recover"]').trigger('click');await flushPromises();expect(api.positionOperation).toHaveBeenCalledWith(original.requestId);await w.get('[data-test="position-retry"]').trigger('click');await flushPromises();expect(api.createPosition).toHaveBeenNthCalledWith(2,original)}finally{w.unmount()}})
it('keeps current records when stock search is unavailable',async()=>{vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'1',items:[row]});vi.mocked(searchInstruments).mockRejectedValue(new Error('行情身份服务不可用'));const w=mountPage();await flushPromises();try{expect(w.text()).toContain('SH600030');expect(w.get('[data-test="position-edit"]').attributes('disabled')).toBeUndefined();expect(w.get('[data-test="position-remove"]').attributes('disabled')).toBeUndefined()}finally{w.unmount()}})
it('ignores old account response after account changes',async()=>{let resolve!:(v:api.PositionBook)=>void;vi.mocked(api.listPositions).mockReturnValueOnce(new Promise(done=>{resolve=done})).mockResolvedValueOnce({bookRevision:'0',items:[]});const w=mountPage();await flushPromises();try{useAuthStore().profile={userId:'other',role:'ADMIN'} as never;await flushPromises();resolve({bookRevision:'1',items:[row]});await flushPromises();expect(w.text()).not.toContain('123.456789');expect(w.text()).toContain('还没有持仓记录')}finally{w.unmount()}})

it('restores an accepted operation from its receipt without submitting twice',async()=>{
 vi.mocked(api.createPosition).mockRejectedValue(new ApiError('NETWORK_UNAVAILABLE','连接失败',0));vi.mocked(api.positionOperation).mockResolvedValue(receipt)
 const w=mountPage();await flushPromises();try{await fill(w);await w.get('[data-test="position-save"]').trigger('submit');await flushPromises();await w.get('[data-test="position-recover"]').trigger('click');await flushPromises();expect(api.createPosition).toHaveBeenCalledTimes(1);expect(w.find('[data-test="position-retry"]').exists()).toBe(false);expect(w.text()).toContain('持仓操作已保存')}finally{w.unmount()}
})
it('does not apply an old mutation response to a new account',async()=>{
 let resolve!:(v:api.PositionReceipt)=>void;vi.mocked(api.createPosition).mockReturnValue(new Promise(done=>{resolve=done}))
 const w=mountPage();await flushPromises();try{await fill(w);await w.get('[data-test="position-save"]').trigger('submit');await flushPromises();useAuthStore().profile={userId:'another',role:'USER'} as never;await flushPromises();resolve(receipt);await flushPromises();expect(w.text()).not.toContain('持仓操作已保存');expect(w.find('[data-test="position-recover"]').exists()).toBe(false);expect((w.get('[data-test="position-cost"]').element as HTMLInputElement).value).toBe('')}finally{w.unmount()}
})

it('passes only a successfully read book revision to saved valuation',async()=>{const w=mountPage();await flushPromises();expect(w.findComponent({name:'PortfolioAnalysisPanel'}).props('currentBookRevision')).toBe('0');vi.mocked(api.listPositions).mockRejectedValueOnce(new Error('持仓服务暂不可用'));await w.get('header button').trigger('click');await flushPromises();expect(w.findComponent({name:'PortfolioAnalysisPanel'}).props('currentBookRevision')).toBe(null);w.unmount()})

it('previews the saved and draft holding values before a modification',async()=>{
 vi.mocked(api.listPositions).mockResolvedValue({bookRevision:'1',items:[row]})
 const w=mountPage()
 try{
  await flushPromises()
  await w.get('[data-test="position-edit"]').trigger('click')
  await w.get('[data-test="position-quantity"]').setValue('8')
  const preview=w.get('[aria-label="待保存的持仓修改"]')
  expect(preview.text()).toContain('原股数 1')
  expect(preview.text()).toContain('新股数 8')
  expect(api.updatePosition).not.toHaveBeenCalled()
 }finally{w.unmount()}
})

it('keeps a dirty draft when route navigation is cancelled and releases the leave guard afterwards',async()=>{
 const router=createRouter({history:createMemoryHistory(),routes:[
  {path:'/portfolio',component:PortfolioPage},{path:'/',component:{template:'<p>首页</p>'}},
 ]})
 await router.push('/portfolio')
 const w=mount({template:'<router-view/>'},{global:{plugins:[router,ElementPlus],stubs:{PortfolioAnalysisPanel:true}}})
 const confirm=vi.spyOn(window,'confirm').mockReturnValue(false)
 try{
  await flushPromises()
  await w.get('[data-test="position-cost"]').setValue('12')
  const beforeUnload=new Event('beforeunload',{cancelable:true})
  window.dispatchEvent(beforeUnload)
  expect(beforeUnload.defaultPrevented).toBe(true)
  await router.push('/')
  expect(router.currentRoute.value.path).toBe('/portfolio')
  expect((w.get('[data-test="position-cost"]').element as HTMLInputElement).value).toBe('12')
  confirm.mockReturnValue(true)
  await router.push('/')
  await flushPromises()
  expect(router.currentRoute.value.path).toBe('/')
  const afterUnload=new Event('beforeunload',{cancelable:true})
  window.dispatchEvent(afterUnload)
  expect(afterUnload.defaultPrevented).toBe(false)
 }finally{confirm.mockRestore();w.unmount()}
})

it('labels Tencent as quote-only and keeps it out of the selectable holdings',async()=>{
 vi.mocked(searchInstruments).mockResolvedValue({items:[
  {instrumentId:'HK00700',displayName:'腾讯控股',displayNameVerified:true},
  {instrumentId:'SH600030',displayName:'中信证券',displayNameVerified:true}
 ],total:2,page:1,size:20} as never)
 const w=mountPage()
 try{
  await flushPromises()
  const tencent=w.get('option[value="HK00700"]')
  expect(tencent.attributes('disabled')).toBeDefined()
  expect(tencent.text()).toContain('暂不支持持仓')
  expect(w.get('option[value="SH600030"]').attributes('disabled')).toBeUndefined()
 }finally{w.unmount()}
})
