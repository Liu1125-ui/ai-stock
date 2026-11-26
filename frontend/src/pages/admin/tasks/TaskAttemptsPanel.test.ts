import {afterEach,beforeEach,it,expect,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import TaskAttemptsPanel from './TaskAttemptsPanel.vue'
import * as api from '../../../api/adminTasks'
import {auth,summary,attempt,page,deferred} from './taskFixtures'
vi.mock('../../../stores/auth',()=>({useAuthStore:()=>auth}))
vi.mock('../../../api/adminTasks',()=>({listAdminTaskAttempts:vi.fn()}))
let w:ReturnType<typeof mount>|undefined
beforeEach(()=>{auth.profile.role='ADMIN';vi.mocked(api.listAdminTaskAttempts).mockResolvedValue(page([attempt()]))})
afterEach(()=>{w?.unmount();vi.resetAllMocks()})
function panel(){w=mount(TaskAttemptsPanel,{props:{taskId:summary().taskId}});return w}
it('paginates beyond old 100 limit using original ordinal and explicit unknown state',async()=>{vi.mocked(api.listAdminTaskAttempts).mockResolvedValueOnce(page([attempt(100)],'after100')).mockResolvedValueOnce(page([attempt(101)]));const x=panel();await flushPromises();expect(x.text()).toContain('结果待确认');expect(x.text()).toContain('尚未结束');await x.get('[data-test="attempt-next"]').trigger('click');await flushPromises();expect(x.text()).toContain('101');expect(api.listAdminTaskAttempts).toHaveBeenLastCalledWith(summary().taskId,20,'after100')})
it('changing task or losing role cannot display an old attempt page',async()=>{const d=deferred<ReturnType<typeof page<ReturnType<typeof attempt>>>>();vi.mocked(api.listAdminTaskAttempts).mockReturnValueOnce(d.promise).mockResolvedValueOnce(page([]));const x=panel();await flushPromises();await x.setProps({taskId:summary(2).taskId});await flushPromises();d.resolve(page([attempt(99)]));await flushPromises();expect(x.text()).not.toContain('99');auth.profile.role='USER';await flushPromises();expect(x.find('table').exists()).toBe(false)})

it('failed refresh retains usable previous attempt-page cursors',async()=>{vi.mocked(api.listAdminTaskAttempts).mockResolvedValueOnce(page([attempt(1)],'c1')).mockResolvedValueOnce(page([attempt(21)],'c2')).mockResolvedValueOnce(page([attempt(41)],'c3')).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(page([attempt(21)],'c2'));const x=panel();await flushPromises();await x.get('[data-test="attempt-next"]').trigger('click');await flushPromises();await x.get('[data-test="attempt-next"]').trigger('click');await flushPromises();await x.get('[data-test="attempt-refresh"]').trigger('click');await flushPromises();await x.get('[data-test="attempt-prev"]').trigger('click');await flushPromises();expect(api.listAdminTaskAttempts).toHaveBeenLastCalledWith(summary().taskId,20,'c1')})
