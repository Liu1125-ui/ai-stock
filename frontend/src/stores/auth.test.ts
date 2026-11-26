import {beforeEach,expect,it,vi} from 'vitest'
import {createPinia,setActivePinia} from 'pinia'
import {useAuthStore} from './auth'
import * as api from '../api/auth'
vi.mock('../api/auth',()=>({me:vi.fn(),login:vi.fn(),logout:vi.fn(),invalidateAuthenticatedRequests:vi.fn()}))
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks()})
it('uses server identity and clears profile after logout',async()=>{
 vi.mocked(api.me).mockResolvedValue({userId:'one',email:'member@example.com',nickname:'测试',role:'USER',status:'ENABLED',emailVerified:true,experimentAllowed:false,revision:0})
 const store=useAuthStore();await store.refresh();expect(store.isAdmin).toBe(false);expect(store.profile?.email).toBe('member@example.com')
 await store.signOut();expect(store.profile).toBeNull()
})
it('does not mistake service failure for successful anonymous initialization',async()=>{
 vi.mocked(api.me).mockRejectedValue({status:503,message:'暂不可用'})
 const store=useAuthStore();await expect(store.refresh()).rejects.toMatchObject({status:503});expect(store.loaded).toBe(false)
})

it('shares concurrent identity refresh between startup and route guard',async()=>{let resolve!:(v:api.Profile)=>void;vi.mocked(api.me).mockReturnValue(new Promise(r=>resolve=r));const store=useAuthStore();const startup=store.refresh(),guard=store.refresh();expect(api.me).toHaveBeenCalledTimes(1);resolve({userId:'one',email:'test@example.com',nickname:'测试',role:'USER',status:'ENABLED',emailVerified:true,experimentAllowed:false,revision:0});await Promise.all([startup,guard]);expect(store.profile?.userId).toBe('one');expect(store.loaded).toBe(true)})
it('cannot restore an old identity after it has been forgotten',async()=>{let resolve!:(v:api.Profile)=>void;vi.mocked(api.me).mockReturnValue(new Promise(r=>resolve=r));const store=useAuthStore();const pending=store.refresh();store.forget();resolve({userId:'one',email:'test@example.com',nickname:'测试',role:'USER',status:'ENABLED',emailVerified:true,experimentAllowed:false,revision:0});await pending;expect(store.profile).toBeNull();expect(store.loaded).toBe(false)})

it('preserves initial same-user pending but removes it on identity change and forget',async()=>{sessionStorage.clear();const {savePending,readPending}=await import('./questionRecovery');const input={operationId:'11111111-1111-4111-8111-111111111111',analysisId:'22222222-2222-4222-8222-222222222222',question:'风险？'};savePending('one',input);const store=useAuthStore();store.profile={userId:'one'} as never;expect(readPending('one',input.analysisId)).toEqual(input);store.profile={userId:'two'} as never;expect(readPending('one',input.analysisId)).toBeNull();savePending('two',input);store.forget();expect(readPending('two',input.analysisId)).toBeNull()})

it('clears administrator task recovery on downgrade even when the task page is unmounted',async()=>{
 sessionStorage.clear();const {savePending,loadPending}=await import('../pages/admin/tasks/taskActionPending')
 const taskId='00000000-0000-0000-0000-000000000001'
 const marker={taskId,operationId:'00000000-0000-0000-0000-000000000002',action:'CANCEL' as const,expectedRevision:'0',reasonCode:'CANCEL_NOT_NEEDED',createdAt:'2026-10-20T12:00:00Z'}
 const store=useAuthStore();store.profile={userId:'one',role:'ADMIN'} as never;savePending('one',marker)
 store.profile={userId:'one',role:'USER'} as never;expect(loadPending('one',taskId)).toBeNull()
})

it('purges all review commands on account switch, forget and ADMIN downgrade while keeping initial recovery',()=>{sessionStorage.clear();const prefix='aistock:market-review:';const own=prefix+'one:SH600030:version-a',other=prefix+'one:SH600276:version-b',foreign=prefix+'other:SH600030:version-c';sessionStorage.setItem(own,'private note');const store=useAuthStore();store.profile={userId:'one',role:'ADMIN'} as never;expect(sessionStorage.getItem(own)).not.toBeNull();sessionStorage.setItem(other,'another private note');sessionStorage.setItem(foreign,'another owner');store.profile={userId:'one',role:'USER'} as never;expect(sessionStorage.getItem(own)).toBeNull();expect(sessionStorage.getItem(other)).toBeNull();expect(sessionStorage.getItem(foreign)).not.toBeNull();store.profile={userId:'one',role:'ADMIN'} as never;sessionStorage.setItem(own,'pending');store.profile={userId:'two',role:'ADMIN'} as never;expect(sessionStorage.getItem(own)).toBeNull();const two=prefix+'two:SH600030:version-a';sessionStorage.setItem(two,'pending2');store.forget();expect(sessionStorage.getItem(two)).toBeNull()})
