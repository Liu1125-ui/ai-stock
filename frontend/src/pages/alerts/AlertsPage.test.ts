import {beforeEach,expect,it,vi} from 'vitest'
import {flushPromises,mount} from '@vue/test-utils'
import {reactive} from 'vue'
import {createMemoryHistory,createRouter} from 'vue-router'
import ElementPlus from 'element-plus'
import AlertsPage from './AlertsPage.vue'
import * as api from '../../api/alerts'
import {useAuthStore} from '../../stores/auth'
vi.mock('../../api/alerts',()=>({listAlerts:vi.fn(),createAlert:vi.fn(),patchAlert:vi.fn()}))
vi.mock('../../api/messages',()=>({listAlertTriggers:vi.fn().mockResolvedValue({items:[],page:1,size:20,total:0})}))
vi.mock('../../stores/auth',()=>({useAuthStore:vi.fn()}))
const auth=reactive({profile:{userId:'owner-a'}})
const rule:api.AlertRule={id:'rule-a',instrumentId:'SH600030',condition:'PRICE_ABOVE',threshold:'30',enabled:true,revision:0,effectiveAfterTradeDate:'2026-10-09',boundaryRawVersionId:'raw',createdAt:'2026-10-10T00:00:00Z',updatedAt:'2026-10-10T00:00:00Z',evaluationAvailability:'NOT_ENABLED'}
beforeEach(()=>{vi.clearAllMocks();auth.profile={userId:'owner-a'};vi.mocked(useAuthStore).mockReturnValue(auth as unknown as ReturnType<typeof useAuthStore>);vi.mocked(api.listAlerts).mockResolvedValue({items:[rule],page:1,size:20,total:1});vi.mocked(api.createAlert).mockResolvedValue(rule);vi.mocked(api.patchAlert).mockResolvedValue({...rule,enabled:false,revision:1})})
async function setup(){const router=createRouter({history:createMemoryHistory(),routes:[{path:'/alerts',component:AlertsPage},{path:'/market/:id',component:{template:'<div/>'}}]});await router.push('/alerts?instrumentId=SH600030');await router.isReady();return mount(AlertsPage,{global:{plugins:[router,ElementPlus]}})}
it('shows daily boundary and unavailable automatic delivery honestly',async()=>{const w=await setup();await flushPromises();expect(w.text()).toContain('2026-10-09');expect(w.text()).toContain('自动检查尚未启用');w.unmount()})
it('retries unknown create result with the same requestId and decimal string',async()=>{vi.mocked(api.createAlert).mockRejectedValueOnce(new Error('连接失败'));const w=await setup();await flushPromises();await w.get('input[data-testid="threshold"]').setValue('30.123456');await w.get('[data-testid="create-alert"]').trigger('click');await flushPromises();await w.get('[data-testid="create-alert"]').trigger('click');await flushPromises();expect(api.createAlert).toHaveBeenCalledTimes(2);expect(vi.mocked(api.createAlert).mock.calls[0][0]).toEqual(vi.mocked(api.createAlert).mock.calls[1][0]);expect(vi.mocked(api.createAlert).mock.calls[0][0].threshold).toBe('30.123456');w.unmount()})
it('rejects invalid precision locally without submitting',async()=>{const w=await setup();await flushPromises();await w.get('input[data-testid="threshold"]').setValue('1.1234567');await w.get('[data-testid="create-alert"]').trigger('click');await flushPromises();expect(api.createAlert).not.toHaveBeenCalled();expect(w.text()).toContain('阈值');w.unmount()})
it('clears former owner rows and ignores their late response',async()=>{let resolve!:(v:api.AlertPage)=>void;vi.mocked(api.listAlerts).mockReturnValueOnce(new Promise(r=>{resolve=r})).mockResolvedValue({items:[],page:1,size:20,total:0});const w=await setup();await flushPromises();auth.profile={userId:'owner-b'};await flushPromises();resolve({items:[rule],page:1,size:20,total:1});await flushPromises();expect(w.text()).not.toContain('2026-10-09');w.unmount()})
it('disables with revision and retains rows when update fails',async()=>{vi.mocked(api.patchAlert).mockRejectedValue(new Error('修改失败'));const w=await setup();await flushPromises();await w.get('[data-testid="toggle-rule"]').trigger('click');expect(api.patchAlert).not.toHaveBeenCalled();await w.get('[data-testid="confirm-stop-rule"]').trigger('click');await flushPromises();expect(api.patchAlert).toHaveBeenCalledWith('rule-a',{revision:0,enabled:false});expect(w.text()).toContain('修改失败');expect(w.text()).toContain('2026-10-09');w.unmount()})

it('edits with the current revision and reloads when it conflicts',async()=>{const {ApiError}=await import('../../api/auth');vi.mocked(api.patchAlert).mockRejectedValue(new ApiError('ALERT_REVISION_CONFLICT','规则已变化',409));const w=await setup();await flushPromises();await w.findAll('button').find(x=>x.text()==='修改')!.trigger('click');await w.get('input[data-testid="threshold"]').setValue('31');await w.get('[data-testid="create-alert"]').trigger('click');await flushPromises();expect(api.patchAlert).toHaveBeenCalledWith('rule-a',{revision:0,condition:'PRICE_ABOVE',threshold:'31'});expect(api.listAlerts).toHaveBeenCalledTimes(2);expect(w.text()).toContain('规则已在其他页面修改');w.unmount()})

it('a conflicted edit never changes into a create on retry',async()=>{const {ApiError}=await import('../../api/auth');vi.mocked(api.patchAlert).mockRejectedValue(new ApiError('ALERT_REVISION_CONFLICT','规则已变化',409));const w=await setup();await flushPromises();await w.findAll('button').find(x=>x.text()==='修改')!.trigger('click');await w.get('input[data-testid="threshold"]').setValue('31');await w.get('[data-testid="create-alert"]').trigger('click');await flushPromises();expect(w.get('[data-testid="create-alert"]').text()).toContain('保存修改');expect(w.get('[data-testid="create-alert"]').attributes('disabled')).toBeDefined();await w.get('[data-testid="create-alert"]').trigger('click');await flushPromises();expect(api.createAlert).not.toHaveBeenCalled();expect(api.patchAlert).toHaveBeenCalledTimes(1);w.unmount()})

it('opens history and shows enabled evaluation independently of mail delivery',async()=>{vi.mocked(api.listAlerts).mockResolvedValue({items:[{...rule,evaluationAvailability:'ENABLED'}],page:1,size:20,total:1});const w=await setup();await flushPromises();await w.get('[data-testid="trigger-history"]').trigger('click');await flushPromises();expect(w.text()).toContain('自动检查已启用');expect(w.text()).toContain('邮件投递状态见对应消息');expect(w.text()).toContain('暂无触发记录');w.unmount()})

it('shows a recorded check separately from saved rule state and delivery',async()=>{
 vi.mocked(api.listAlerts).mockResolvedValue({
  items:[{...rule,evaluationAvailability:'ENABLED'}],page:1,size:20,total:1,
  recentChecks:{[rule.id]:{state:'NOT_MET',checkedAt:'2026-10-12T12:00:00Z',tradeDate:'2026-10-12'}},
 })
 const w=await setup()
 try{
  await flushPromises()
  const row=w.get('tbody tr')
  expect(row.text()).toContain('已检查，未触发')
  expect(row.text()).toContain('2026-10-12')
  expect(w.text()).toContain('邮件投递状态见对应消息')
 }finally{w.unmount()}
})

it('keeps loaded rules during a refresh failure and clears that error on recovery',async()=>{
 const w=await setup()
 try{
  await flushPromises()
  vi.mocked(api.listAlerts).mockRejectedValueOnce(new Error('预警列表连接失败'))
  const refresh=()=>w.findAll('button').find(button=>button.text()==='刷新列表')!.trigger('click')
  await refresh()
  await flushPromises()
  expect(w.text()).toContain('预警列表连接失败')
  expect(w.text()).toContain('2026-10-09')
  await refresh()
  await flushPromises()
  expect(w.text()).not.toContain('预警列表连接失败')
  expect(w.text()).toContain('2026-10-09')
 }finally{w.unmount()}
})
