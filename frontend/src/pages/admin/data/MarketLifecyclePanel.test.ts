import {flushPromises,mount} from '@vue/test-utils'
import {afterEach,expect,it,vi} from 'vitest'
import {ApiError} from '../../../api/auth'
import {getLifecycleHistory,getLifecycleStatus,getLifecycleOperation,submitLifecycleAction,type LifecycleStatus} from '../../../api/marketVersionLifecycle'
import Panel from './MarketLifecyclePanel.vue'

vi.mock('../../../api/marketVersionLifecycle',()=>({getLifecycleHistory:vi.fn(),getLifecycleStatus:vi.fn(),getLifecycleOperation:vi.fn(),submitLifecycleAction:vi.fn()}))
const stock='SH600030'
const version='00000000-0000-0000-0000-000000000001'
const operation='00000000-0000-0000-0000-000000000002'
const state:LifecycleStatus={instrumentId:stock,versionId:version,useState:'DRAINING',revision:'2',activeUses:1,currentHead:false,actionsReady:false}
const entry={operationId:operation,instrumentId:stock,versionId:version,action:'DISABLE' as const,expectedRevision:'1',state:'WAITING' as const,reason:'校验旧版本',createdAt:'2026-11-10T00:00:00Z',errorCode:null}

afterEach(()=>{vi.resetAllMocks();sessionStorage.clear()})

it('shows draining and preserves the current page if earlier history fails',async()=>{
  vi.mocked(getLifecycleStatus).mockResolvedValue(state)
  vi.mocked(getLifecycleHistory).mockResolvedValueOnce({items:[entry],nextCursor:operation})
    .mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({items:[],nextCursor:null})
  const wrapper=mount(Panel,{props:{instrumentId:stock,versionId:version}})
  try {
    expect(getLifecycleStatus).not.toHaveBeenCalled()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('等待在途任务完成')
    expect(wrapper.text()).toContain('暂未开放')
    await wrapper.findAll('button')[1]!.trigger('click')
    await flushPromises()
    expect(getLifecycleHistory).toHaveBeenLastCalledWith(stock,version,operation)
    expect(wrapper.text()).toContain('仍显示已读记录')
    expect(wrapper.text()).toContain('校验旧版本')
    await wrapper.findAll('button')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('校验旧版本')
    expect(wrapper.findAll('button')).toHaveLength(1)
  } finally {wrapper.unmount()}
})

it('clears private records and notifies the page when admin access is revoked',async()=>{
  vi.mocked(getLifecycleStatus).mockResolvedValueOnce(state)
    .mockRejectedValueOnce(new ApiError('ACCESS_DENIED','权限已撤销',403))
  vi.mocked(getLifecycleHistory).mockResolvedValue({items:[entry],nextCursor:null})
  const wrapper=mount(Panel,{props:{instrumentId:stock,versionId:version}})
  try {
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('校验旧版本')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('accessDenied')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('校验旧版本')
    expect(wrapper.find('dl').exists()).toBe(false)
  } finally {wrapper.unmount()}
})

it('ignores late authorization failures from a version that has been closed',async()=>{
  let reject!:(reason:unknown)=>void
  vi.mocked(getLifecycleStatus).mockReturnValue(new Promise((_resolve,fail)=>{reject=fail}))
  vi.mocked(getLifecycleHistory).mockResolvedValue({items:[],nextCursor:null})
  const wrapper=mount(Panel,{props:{instrumentId:stock,versionId:version}})
  await wrapper.get('button').trigger('click')
  wrapper.unmount()
  reject(new ApiError('ACCESS_DENIED','权限已撤销',403))
  await flushPromises()
  expect(wrapper.emitted('accessDenied')).toBeUndefined()
})

it('confirms an action once and recovers a lost reply by its original number',async()=>{
  vi.mocked(getLifecycleStatus).mockResolvedValue({...state,useState:'ENABLED',revision:'0',actionsReady:true})
  vi.mocked(getLifecycleHistory).mockResolvedValue({items:[],nextCursor:null})
  vi.mocked(submitLifecycleAction).mockRejectedValue(new ApiError('MARKET_ADMIN_UNAVAILABLE','暂不可用',503))
  const wrapper=mount(Panel,{props:{instrumentId:stock,versionId:version,actorUserId:'admin-fixture'}})
  try {
    await wrapper.get('button').trigger('click')
    await flushPromises()
    await wrapper.findAll('button').find(button=>button.text()==='停用此版本')!.trigger('click')
    await wrapper.get('input').setValue('  旧行情核验  ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    const command=vi.mocked(submitLifecycleAction).mock.calls[0]![2]
    expect(command).toMatchObject({action:'DISABLE',expectedRevision:'0',reason:'旧行情核验'})
    expect(wrapper.text()).toContain(command.operationId)
    vi.mocked(getLifecycleOperation).mockResolvedValueOnce({...entry,...command,reason:'不一致的回执'})
    await wrapper.findAll('button').find(button=>button.text()==='查询原操作')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('回执与原请求不一致')
    expect(wrapper.text()).toContain(command.operationId)
    vi.mocked(getLifecycleOperation).mockResolvedValue({...entry,...command})
    vi.mocked(getLifecycleStatus).mockResolvedValue({...state,actionsReady:true})
    await wrapper.findAll('button').find(button=>button.text()==='查询原操作')!.trigger('click')
    await flushPromises()
    expect(getLifecycleOperation).toHaveBeenCalledWith(stock,version,command.operationId)
    expect(submitLifecycleAction).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('等待在途任务完成')
    expect(wrapper.text()).toContain('恢复使用')
  } finally {wrapper.unmount()}
})
