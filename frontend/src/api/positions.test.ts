import {beforeEach,expect,it,vi} from 'vitest'
import {request} from './auth'
import {deletePosition,positionOperation,createPosition} from './positions'
vi.mock('./auth',()=>({request:vi.fn()}))
beforeEach(()=>vi.resetAllMocks())
it('forwards delete body and preserves string revision',async()=>{const input={requestId:'req',expectedRevision:'1'};await deletePosition('position',input);expect(request).toHaveBeenCalledWith('/users/me/positions/position','DELETE',input)})
it('queries original receipt without changing it',async()=>{await positionOperation('req');expect(request).toHaveBeenCalledWith('/users/me/positions/operations/req')})
it('never converts amount strings to floating numbers',async()=>{const input={requestId:'req',instrumentId:'SH600030',quantity:'1',averageCost:'123.456789'};await createPosition(input);expect(request).toHaveBeenCalledWith('/users/me/positions','POST',input)})
