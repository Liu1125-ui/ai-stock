import {beforeEach,expect,it} from 'vitest'
import {savePending,loadPending,clearPending,clearTaskActionPendingOwner} from './taskActionPending'
const taskId='00000000-0000-0000-0000-000000000001'
const marker={taskId,operationId:'00000000-0000-0000-0000-000000000002',action:'CANCEL' as const,expectedRevision:'9007199254740993',reasonCode:'CANCEL_NOT_NEEDED',createdAt:'2026-10-20T12:00:00Z'}
beforeEach(()=>sessionStorage.clear())
it('stores the complete fixed request per owner without credentials or free text',()=>{
 savePending('admin-a',marker);expect(loadPending('admin-a',taskId)).toEqual(marker);expect(loadPending('admin-b',taskId)).toBeNull()
 expect(sessionStorage.getItem(sessionStorage.key(0)!)).not.toContain('note')
 clearPending('admin-a',taskId);expect(loadPending('admin-a',taskId)).toBeNull()
})
it('rejects corrupted or extended markers and only clears this owner',()=>{
 savePending('admin-a',marker);savePending('admin-b',marker)
 const key=Array.from({length:sessionStorage.length},(_,i)=>sessionStorage.key(i)!).find(k=>k.includes('admin-a'))!
 sessionStorage.setItem(key,JSON.stringify({...marker,expectedRevision:0,note:'do not store'}))
 expect(()=>loadPending('admin-a',taskId)).toThrow();clearTaskActionPendingOwner('admin-a');expect(loadPending('admin-b',taskId)).toEqual(marker)
})

it('rejects a numeric creation date instead of coercing it to a year',()=>{
 savePending('admin-a',marker);const key=sessionStorage.key(0)!
 sessionStorage.setItem(key,JSON.stringify({...marker,createdAt:2026}))
 expect(()=>loadPending('admin-a',taskId)).toThrow()
})
