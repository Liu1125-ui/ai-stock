import {beforeEach,afterEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import TaskOperationHistoryPanel from './TaskOperationHistoryPanel.vue'
import * as api from '../../../api/adminTaskActions'
import {auth,summary} from './taskFixtures'
import {ApiError} from '../../../api/auth'
vi.mock('../../../stores/auth',()=>({useAuthStore:()=>auth}))
vi.mock('../../../api/adminTaskActions',()=>({listTaskOperations:vi.fn()}))
let w:ReturnType<typeof mount>|undefined
beforeEach(()=>{auth.profile.role='ADMIN';vi.mocked(api.listTaskOperations).mockResolvedValue({items:[],nextCursor:null,hasMore:false,queriedAt:'2026-10-20T12:00:00Z'})})
afterEach(()=>{w?.unmount();vi.resetAllMocks()})
it('uses read-only cursor pages and retains old cursor history after refresh failure',async()=>{
 const page={items:[],nextCursor:'c1',hasMore:true,queriedAt:'2026-10-20T12:00:00Z'}
 vi.mocked(api.listTaskOperations).mockResolvedValueOnce(page).mockResolvedValueOnce({...page,nextCursor:null,hasMore:false}).mockRejectedValueOnce(new ApiError('SCHEDULER_SERVICE_UNAVAILABLE','暂不可用',503)).mockResolvedValue(page)
 w=mount(TaskOperationHistoryPanel,{props:{taskId:summary().taskId,refreshKey:0}});await flushPromises();await w.get('[data-test="operation-next"]').trigger('click');await flushPromises();await w.get('[data-test="operation-refresh"]').trigger('click');await flushPromises();expect(w.text()).toContain('尚未刷新');await w.get('[data-test="operation-prev"]').trigger('click');await flushPromises();expect(api.listTaskOperations).toHaveBeenLastCalledWith(summary().taskId,20,undefined)
})
