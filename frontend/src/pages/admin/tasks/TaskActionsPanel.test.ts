import {afterEach,beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import TaskActionsPanel from './TaskActionsPanel.vue'
import * as api from '../../../api/adminTaskActions'
import {ApiError} from '../../../api/auth'
import {loadPending} from './taskActionPending'
import {createAdminTaskAccess,taskAccessKey,taskIdentity} from './taskAccess'
const state=reactive({profile:{userId:'admin-test',role:'ADMIN'},refresh:vi.fn()})
vi.mock('../../../stores/auth',()=>({useAuthStore:()=>state}))
vi.mock('../../../api/adminTaskActions',()=>({getTaskActionOptions:vi.fn(),submitTaskAction:vi.fn(),getTaskActionReceipt:vi.fn()}))
const task='00000000-0000-0000-0000-000000000001'
const decision={allowed:true,reasonCode:'ALLOWED',message:'可以操作',retryAvailableAt:null}
const options={taskId:task,revision:'0',queriedAt:'2026-10-20T12:00:00Z',actionsEnabled:true,retry:decision,cancel:decision}
let w:ReturnType<typeof mount>|undefined
beforeEach(()=>{sessionStorage.clear();state.profile={userId:'admin-test',role:'ADMIN'};state.refresh.mockResolvedValue(undefined);vi.mocked(api.getTaskActionOptions).mockResolvedValue(options)})
afterEach(()=>{w?.unmount();vi.resetAllMocks()})
function panel(){const access=createAdminTaskAccess(()=>taskIdentity(state.profile),()=>state.refresh());w=mount(TaskActionsPanel,{props:{taskId:task},attachTo:document.body,global:{provide:{[taskAccessKey as symbol]:access}}});return {w,access}}
async function confirmRetry(x:ReturnType<typeof mount>){await x.get('[data-test="task-retry"]').trigger('click');await x.get('select[data-test="action-reason"]').setValue('RETRY_TRANSIENT_FAILURE');await x.get('form[data-test="action-confirm"]').trigger('submit');await flushPromises()}
it('confirmation cancellation sends no command and buttons have reasons',async()=>{const {w:x}=panel();await flushPromises();await x.get('[data-test="task-cancel"]').trigger('click');expect(x.text()).toContain('不关闭后续自动计划');await x.get('[data-test="action-dismiss"]').trigger('click');expect(api.submitTaskAction).not.toHaveBeenCalled()})
it('unknown 404 remains pending and manual recovery keeps the exact original command',async()=>{
 vi.mocked(api.submitTaskAction).mockRejectedValue(new ApiError('NETWORK_UNAVAILABLE','连接失败',0));vi.mocked(api.getTaskActionReceipt).mockRejectedValue(new ApiError('OPERATION_NOT_FOUND','未查到',404))
 const {w:x}=panel();await flushPromises();await confirmRetry(x);const first=vi.mocked(api.submitTaskAction).mock.calls[0]!
 expect(loadPending('admin-test',task)?.operationId).toBe(first[2].operationId)
 await x.get('[data-testid="query-task-operation"]').trigger('click');await flushPromises();expect(x.text()).toContain('操作结果尚未确认');expect(api.submitTaskAction).toHaveBeenCalledTimes(1)
 await x.get('[data-test="recover-task-operation"]').trigger('click');expect(x.text()).toContain('可能现在执行');await x.get('form[data-test="action-confirm"]').trigger('submit');await flushPromises()
 expect(vi.mocked(api.submitTaskAction).mock.calls[1]).toEqual(first)
})
it('reload queries an unresolved marker without automatically posting again',async()=>{
 vi.mocked(api.submitTaskAction).mockRejectedValue(new ApiError('NETWORK_UNAVAILABLE','失败',0));vi.mocked(api.getTaskActionReceipt).mockRejectedValue(new ApiError('OPERATION_NOT_FOUND','未查到',404))
 let x=panel().w;await flushPromises();await confirmRetry(x);x.unmount();x=panel().w;await flushPromises();expect(api.submitTaskAction).toHaveBeenCalledTimes(1);expect(api.getTaskActionReceipt).toHaveBeenCalled();expect(x.text()).toContain('操作结果尚未确认')
})
it('duplicate confirmations cannot create a second in-flight submission',async()=>{
 let resolve!:(r:api.ActionReceipt)=>void;vi.mocked(api.submitTaskAction).mockReturnValue(new Promise(r=>{resolve=r}))
 const {w:x}=panel();await flushPromises();await x.get('[data-test="task-retry"]').trigger('click');await x.get('[data-test="action-reason"]').setValue('RETRY_TRANSIENT_FAILURE');await x.get('form').trigger('submit');await x.get('form').trigger('submit');expect(api.submitTaskAction).toHaveBeenCalledTimes(1)
 const command=vi.mocked(api.submitTaskAction).mock.calls[0]![2];resolve({operationId:command.operationId,taskId:task,action:'RETRY',outcome:'APPLIED',resultCode:'TASK_RETRY_APPLIED',message:'已重新排队',beforeState:'FAILED',afterState:'QUEUED',beforeRevision:'0',afterRevision:'1',requestedAt:options.queriedAt,finishedAt:options.queriedAt});await flushPromises();expect(x.emitted('changed')).toBeTruthy();expect(loadPending('admin-test',task)).toBeNull()
})
it('server permission loss clears the shared boundary and suppresses late data',async()=>{
 const {w:x,access}=panel();await flushPromises();vi.mocked(api.submitTaskAction).mockRejectedValueOnce(new ApiError('ACCESS_DENIED','已撤权',403));await confirmRetry(x);expect(access.denied.value).toBe(true);expect(x.text()).not.toContain(task);expect(loadPending('admin-test',task)).toBeNull()
})
it('capability failure does not allow commands',async()=>{
 vi.mocked(api.getTaskActionOptions).mockRejectedValueOnce(new ApiError('SCHEDULER_SERVICE_UNAVAILABLE','暂不可用',503));const {w:x}=panel();await flushPromises();expect(x.get('[data-test="task-retry"]').attributes('disabled')).toBeDefined();expect(api.submitTaskAction).not.toHaveBeenCalled()
})

it('focuses an enabled control when the original reason is locked during recovery',async()=>{
 vi.mocked(api.submitTaskAction).mockRejectedValue(new ApiError('NETWORK_UNAVAILABLE','未知',0));vi.mocked(api.getTaskActionReceipt).mockRejectedValue(new ApiError('OPERATION_NOT_FOUND','未查到',404))
 const {w:x}=panel();await flushPromises();await confirmRetry(x);const recover=x.get<HTMLButtonElement>('[data-test="recover-task-operation"]');recover.element.focus();await recover.trigger('click');await flushPromises()
 expect(x.get('[role="dialog"]').element.contains(document.activeElement)).toBe(true)
})

it('a saved rejection clears pending state and refreshes instead of calling it success',async()=>{
 vi.mocked(api.submitTaskAction).mockImplementationOnce(async(taskId,action,c)=>{throw new ApiError('TASK_REVISION_CONFLICT','已变化',409,{operationId:c.operationId,taskId,action,outcome:'REJECTED',resultCode:'TASK_REVISION_CONFLICT',message:'任务状态已变化',beforeState:'RUNNING',afterState:'RUNNING',beforeRevision:'1',afterRevision:'1',requestedAt:options.queriedAt,finishedAt:options.queriedAt})})
 const {w:x}=panel();await flushPromises();await confirmRetry(x);expect(x.text()).toContain('未生效');expect(x.text()).not.toContain('操作结果尚未确认');expect(x.emitted('changed')).toBeTruthy();expect(loadPending('admin-test',task)).toBeNull()
})
it('ignores a late capability response after switching tasks',async()=>{
 let resolve!:(v:api.ActionOptions)=>void;vi.mocked(api.getTaskActionOptions).mockReturnValueOnce(new Promise(r=>{resolve=r}))
 const {w:x}=panel();await flushPromises();const other='00000000-0000-0000-0000-000000000009';vi.mocked(api.getTaskActionOptions).mockResolvedValue({...options,taskId:other,retry:{...decision,message:'当前任务条件'}});await x.setProps({taskId:other});await flushPromises();resolve({...options,retry:{...decision,message:'旧任务条件'}});await flushPromises();expect(x.text()).toContain('当前任务条件');expect(x.text()).not.toContain('旧任务条件')
})
it('returns keyboard focus to its trigger when confirmation is dismissed',async()=>{
 const {w:x}=panel();await flushPromises();const button=x.get<HTMLButtonElement>('[data-test="task-retry"]');button.element.focus();await button.trigger('click');await flushPromises();expect(x.get('[role="dialog"]').element.contains(document.activeElement)).toBe(true);await x.get('[data-test="action-dismiss"]').trigger('click');await flushPromises();expect(document.activeElement).toBe(button.element)
})
