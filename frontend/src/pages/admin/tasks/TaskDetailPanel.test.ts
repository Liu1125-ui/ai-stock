import {afterEach,beforeEach,it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import TaskDetailPanel from './TaskDetailPanel.vue'
import * as api from '../../../api/adminTasks'
import {auth,summary,detail,deferred} from './taskFixtures'
import {ApiError} from '../../../api/auth'
vi.mock('../../../stores/auth',()=>({useAuthStore:()=>auth}))
vi.mock('../../../api/adminTasks',()=>({getAdminTask:vi.fn(),listAdminTaskAttempts:vi.fn()}))
let w:ReturnType<typeof mount>|undefined
beforeEach(()=>{auth.profile.role='ADMIN';vi.mocked(api.getAdminTask).mockResolvedValue(detail())})
afterEach(()=>{w?.unmount();vi.resetAllMocks()})
function panel(){w=mount(TaskDetailPanel,{props:{taskId:summary().taskId},global:{stubs:{TaskAttemptsPanel:true}}});return w}
it('separates cumulative and current-cycle attempts and uses generic result reference',async()=>{const x=panel();await flushPromises();expect(x.text()).toContain('累计尝试');expect(x.text()).toContain('本轮尝试');expect(x.text()).toContain('110');expect(x.text()).not.toContain('重试任务')})
it('late response from an older task cannot replace the current task',async()=>{const d=deferred<ReturnType<typeof detail>>();vi.mocked(api.getAdminTask).mockReturnValueOnce(d.promise).mockResolvedValueOnce(detail(2));const x=panel();await flushPromises();await x.setProps({taskId:summary(2).taskId});await flushPromises();d.resolve(detail(1));await flushPromises();expect(x.text()).toContain(summary(2).taskId);expect(x.text()).not.toContain(summary(1).taskId)})
it('role loss blocks an old pending administrator response',async()=>{const d=deferred<ReturnType<typeof detail>>();vi.mocked(api.getAdminTask).mockReturnValueOnce(d.promise);const x=panel();await flushPromises();auth.profile.role='USER';await flushPromises();d.resolve(detail());await flushPromises();expect(x.text()).not.toContain(summary().taskId)})
it('404 clears prior selected detail and emits missing',async()=>{const x=panel();await flushPromises();vi.mocked(api.getAdminTask).mockRejectedValueOnce(new ApiError('TASK_NOT_FOUND','任务不存在',404));await x.get('[data-test="task-detail-refresh"]').trigger('click');await flushPromises();expect(x.text()).not.toContain(summary().taskId);expect(x.emitted('missing')).toBeTruthy()})

it('replaces an in-flight detail read after an applied operation and ignores the old result',async()=>{
 const x=panel();await flushPromises();const old=deferred<ReturnType<typeof detail>>()
 vi.mocked(api.getAdminTask).mockReturnValueOnce(old.promise).mockResolvedValueOnce({...detail(),task:{...detail().task,summary:{...summary(),state:'CANCELLED'}}})
 await x.get('[data-test="task-detail-refresh"]').trigger('click');await flushPromises()
 x.findComponent({name:'TaskActionsPanel'}).vm.$emit('changed');await flushPromises()
 expect(api.getAdminTask).toHaveBeenCalledTimes(3)
 old.resolve(detail());await flushPromises();expect(x.text()).toContain('已取消');expect(x.text()).not.toContain('读取中')
})
