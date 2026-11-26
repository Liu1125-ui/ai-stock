import {afterEach,beforeEach,it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import TasksPage from './TasksPage.vue'
import * as api from '../../../api/adminTasks'
import {auth,summary,page,deferred,detail,attempt} from './taskFixtures'
import {ApiError} from '../../../api/auth'
vi.mock('../../../stores/auth',()=>({useAuthStore:()=>auth}))
vi.mock('../../../api/adminTasks',()=>({listAdminTasks:vi.fn(),getAdminTask:vi.fn(),listAdminTaskAttempts:vi.fn()}))
let w:ReturnType<typeof mount>|undefined
beforeEach(()=>{auth.profile.role='ADMIN';auth.profile.userId='admin-test';vi.mocked(api.listAdminTasks).mockResolvedValue(page([summary()]))})
afterEach(()=>{w?.unmount();vi.resetAllMocks()})
function mountPage(){w=mount(TasksPage,{global:{stubs:{TaskDetailPanel:true}}});return w}
it('shows scope, state and history count without write buttons',async()=>{const x=mountPage();await flushPromises();expect(x.text()).toContain('行情更新、预测验证与股票比较更新');expect(x.text()).toContain('110');expect(x.text()).not.toContain('重新执行');expect(x.find('[data-test="task-open"]').attributes('type')).toBe('button')})
it('applies edited filters only on query and resets pagination',async()=>{const x=mountPage();await flushPromises();await x.get('[data-test="task-type"]').setValue('PREDICTION_VERIFY');expect(api.listAdminTasks).toHaveBeenCalledTimes(1);await x.get('form').trigger('submit');await flushPromises();expect(api.listAdminTasks).toHaveBeenLastCalledWith(expect.objectContaining({type:'PREDICTION_VERIFY'}),undefined)})
it('next previous and refresh use cursors without total pages',async()=>{vi.mocked(api.listAdminTasks).mockResolvedValueOnce(page([summary(1)],'next')).mockResolvedValueOnce(page([summary(2)])).mockResolvedValue(page([summary(1)],'next'));const x=mountPage();await flushPromises();await x.get('[data-test="task-next"]').trigger('click');await flushPromises();expect(api.listAdminTasks).toHaveBeenLastCalledWith(expect.anything(),'next');await x.get('[data-test="task-prev"]').trigger('click');await flushPromises();expect(api.listAdminTasks).toHaveBeenLastCalledWith(expect.anything(),undefined);await x.get('[data-test="task-refresh"]').trigger('click');await flushPromises();expect(api.listAdminTasks).toHaveBeenLastCalledWith(expect.anything(),undefined)})
it('clears admin data on 403 and ignores late data after role loss',async()=>{const d=deferred<ReturnType<typeof page<ReturnType<typeof summary>>>>();vi.mocked(api.listAdminTasks).mockReturnValueOnce(d.promise);const x=mountPage();await flushPromises();auth.profile.role='USER';await flushPromises();d.resolve(page([summary()]));await flushPromises();expect(x.text()).not.toContain(summary().taskId);expect(x.text()).toContain('仅管理员');auth.profile.role='ADMIN';await flushPromises();vi.mocked(api.listAdminTasks).mockRejectedValueOnce(new ApiError('ACCESS_DENIED','无权限',403));await x.get('[data-test="task-refresh"]').trigger('click');await flushPromises();expect(x.text()).not.toContain(summary().taskId)})
it('preserves same-query records on 503 but marks stale, not an account failure',async()=>{const x=mountPage();await flushPromises();vi.mocked(api.listAdminTasks).mockRejectedValueOnce(new ApiError('SCHEDULER_SERVICE_UNAVAILABLE','任务服务暂不可用',503));await x.get('[data-test="task-refresh"]').trigger('click');await flushPromises();expect(x.text()).toContain('尚未刷新');expect(x.text()).toContain('110');expect(x.text()).not.toContain('账号服务')})
it('shows empty results and blocks a duplicate in-flight query',async()=>{const d=deferred<ReturnType<typeof page<ReturnType<typeof summary>>>>();vi.mocked(api.listAdminTasks).mockReturnValueOnce(d.promise);const x=mountPage();await flushPromises();expect(x.get('[data-test="task-query"]').attributes('disabled')).toBeDefined();d.resolve(page([]));await flushPromises();expect(x.text()).toContain('当前条件下暂无任务')})

it('failed refresh on a third page preserves the prior cursor history',async()=>{vi.mocked(api.listAdminTasks).mockResolvedValueOnce(page([summary(1)],'c1')).mockResolvedValueOnce(page([summary(2)],'c2')).mockResolvedValueOnce(page([summary(3)],'c3')).mockRejectedValueOnce(new ApiError('SCHEDULER_SERVICE_UNAVAILABLE','不可用',503)).mockResolvedValue(page([summary(2)],'c2'));const x=mountPage();await flushPromises();await x.get('[data-test="task-next"]').trigger('click');await flushPromises();await x.get('[data-test="task-next"]').trigger('click');await flushPromises();await x.get('[data-test="task-refresh"]').trigger('click');await flushPromises();await x.get('[data-test="task-prev"]').trigger('click');await flushPromises();expect(api.listAdminTasks).toHaveBeenLastCalledWith(expect.anything(),'c1')})

it('keeps main native controls focusable without a pointer',async()=>{w=mount(TasksPage,{attachTo:document.body,global:{stubs:{TaskDetailPanel:true}}});await flushPromises();const button=w.get<HTMLButtonElement>('[data-test="task-query"]');button.element.focus();expect(document.activeElement).toBe(button.element);expect(button.attributes('disabled')).toBeUndefined();expect(w.get('input[type="date"]').element.closest('label')).not.toBeNull()})

it('a server 403 in attempts clears the whole nested workspace and rejects a late detail success',async()=>{
 auth.refresh.mockImplementationOnce(async()=>{auth.profile={...auth.profile}})
 vi.mocked(api.getAdminTask).mockResolvedValue(detail())
 vi.mocked(api.listAdminTaskAttempts).mockResolvedValue(page([attempt()]))
 w=mount(TasksPage);await flushPromises()
 await w.get('[data-test="task-open"]').trigger('click');await flushPromises()
 const pending=deferred<ReturnType<typeof detail>>()
 vi.mocked(api.getAdminTask).mockReturnValueOnce(pending.promise)
 await w.get('[data-test="task-detail-refresh"]').trigger('click');await flushPromises()
 vi.mocked(api.listAdminTaskAttempts).mockRejectedValueOnce(new ApiError('ACCESS_DENIED','管理员权限已撤销',403))
 await w.get('[data-test="attempt-refresh"]').trigger('click');await flushPromises()
 expect(auth.profile.role).toBe('ADMIN')
 expect(w.text()).not.toContain(summary().taskId)
 expect(w.text()).toContain('访问已失效')
 pending.resolve(detail());await flushPromises()
 expect(w.text()).not.toContain(summary().taskId)
 expect(w.find('[data-test="task-refresh"]').exists()).toBe(false)
 expect(auth.refresh).toHaveBeenCalledTimes(1)
})
it('a server 403 in detail clears list data even while an older attempts request is pending',async()=>{
 auth.refresh.mockResolvedValue(undefined)
 vi.mocked(api.getAdminTask).mockResolvedValue(detail())
 const pending=deferred<ReturnType<typeof page<ReturnType<typeof attempt>>>>()
 vi.mocked(api.listAdminTaskAttempts).mockReturnValueOnce(pending.promise)
 w=mount(TasksPage);await flushPromises()
 await w.get('[data-test="task-open"]').trigger('click');await flushPromises()
 vi.mocked(api.getAdminTask).mockRejectedValueOnce(new ApiError('ACCESS_DENIED','无权限',403))
 await w.get('[data-test="task-detail-refresh"]').trigger('click');await flushPromises()
 pending.resolve(page([attempt(99)]));await flushPromises()
 expect(w.text()).not.toContain(summary().taskId)
 expect(w.find('table').exists()).toBe(false)
 expect(w.text()).toContain('访问已失效')
})

it('refreshes the applied list after an operation while keeping the selected detail',async()=>{
 const x=mountPage();await flushPromises();await x.get('[data-test="task-open"]').trigger('click');await flushPromises()
 await x.get('[data-test="task-type"]').setValue('PREDICTION_VERIFY')
 x.findComponent({name:'TaskDetailPanel'}).vm.$emit('changed');await flushPromises()
 expect(api.listAdminTasks).toHaveBeenLastCalledWith(expect.not.objectContaining({type:'PREDICTION_VERIFY'}),undefined)
 expect(x.findComponent({name:'TaskDetailPanel'}).exists()).toBe(true)
})

it('replaces pending pagination on operation completion and ignores its late response',async()=>{
 vi.mocked(api.listAdminTasks).mockResolvedValueOnce(page([summary()],'next'))
 const x=mountPage();await flushPromises();await x.get('[data-test="task-open"]').trigger('click');await flushPromises()
 const old=deferred<ReturnType<typeof page<ReturnType<typeof summary>>>>()
 vi.mocked(api.listAdminTasks).mockReturnValueOnce(old.promise).mockResolvedValueOnce(page([{...summary(),state:'CANCELLED'}]))
 await x.get('[data-test="task-next"]').trigger('click');await flushPromises()
 x.findComponent({name:'TaskDetailPanel'}).vm.$emit('changed');await flushPromises()
 expect(api.listAdminTasks).toHaveBeenCalledTimes(3)
 old.resolve(page([summary(2)]));await flushPromises()
 expect(x.text()).toContain('已取消');expect(x.text()).toContain('第 1 页');expect(x.findComponent({name:'TaskDetailPanel'}).exists()).toBe(true)
})
