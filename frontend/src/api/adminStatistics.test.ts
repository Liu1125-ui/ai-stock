import {it,expect,vi,afterEach} from 'vitest'
import * as api from './adminStatistics'
import {users,window} from '../pages/admin/statistics/statisticsFixtures'
afterEach(()=>vi.unstubAllGlobals())
function response(data:unknown){return new Response(JSON.stringify({code:'OK',data}),{status:200,headers:{'Content-Type':'application/json'}})}
it('sends only GET with explicit dates and keeps large exact count strings',async()=>{const data=users();data.totalUsers='9007199254740992';data.enabledUsers=data.totalUsers;data.roleCounts=[{key:'USER',count:data.totalUsers}];const fetch=vi.fn().mockResolvedValue(response(data));vi.stubGlobal('fetch',fetch);expect((await api.getUsers(window)).totalUsers).toBe('9007199254740992');expect(fetch.mock.calls[0]?.[1]).toMatchObject({method:'GET',credentials:'include',cache:'no-store'});expect(fetch.mock.calls[0]?.[0]).toContain('from=2026-10-16')})
it('does not turn malformed or mismatched metadata into a zero report',async()=>{const data=users();data.meta.window.from='2026-01-01';vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response(data)));await expect(api.getUsers(window)).rejects.toMatchObject({code:'INVALID_RESPONSE'})})
