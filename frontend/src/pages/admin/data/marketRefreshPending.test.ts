import {beforeEach,it,expect} from 'vitest'
import {savePending,loadPending,clearPending,clearOwner} from './marketRefreshPending'
import {owner,stock,command} from './marketRefreshFixtures'
beforeEach(()=>sessionStorage.clear())
it('isolates recovery per actor and stock and clears all of one owner',()=>{savePending(owner,{instrumentId:stock,command});savePending('other',{instrumentId:stock,command});expect(loadPending(owner,stock)).toEqual({instrumentId:stock,command});expect(loadPending(owner,'SH600276')).toBeNull();clearOwner(owner);expect(loadPending(owner,stock)).toBeNull();expect(loadPending('other',stock)).not.toBeNull();clearPending('other',stock);expect(loadPending('other',stock)).toBeNull()})
it('rejects corrupt or extra storage fields before any new submission',()=>{savePending(owner,{instrumentId:stock,command});const key=sessionStorage.key(0)!;sessionStorage.setItem(key,JSON.stringify({instrumentId:stock,command:{...command,password:'must not save'}}));expect(()=>loadPending(owner,stock)).toThrow()})
