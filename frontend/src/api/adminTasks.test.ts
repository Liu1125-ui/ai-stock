import {afterEach,it,expect,vi} from 'vitest'
import {listAdminTasks,getAdminTask,listAdminTaskAttempts} from './adminTasks'
import {summary,detail,page} from '../pages/admin/tasks/taskFixtures'
afterEach(()=>vi.unstubAllGlobals())
function respond(data:unknown){const fetch=vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'OK',data}),{status:200}));vi.stubGlobal('fetch',fetch);return fetch}
it('uses only GET with encoded query and existing credentials/no-store policy',async()=>{const fetch=respond(page([summary()]));await listAdminTasks({createdFrom:'2026-10-01',createdTo:'2026-10-20',limit:20},'a+b/=?');const [url,options]=fetch.mock.calls[0]!;expect(new URL('http://local'+url).searchParams.get('cursor')).toBe('a+b/=?');expect(options).toMatchObject({method:'GET',credentials:'include',cache:'no-store'});expect(url).not.toContain('type=')})
it('reads detail and attempts without writing or fetching other businesses',async()=>{const fetch=respond(detail());await getAdminTask(summary().taskId);expect(fetch.mock.calls[0]![0]).toContain('/admin/tasks/'+summary().taskId);respond(page([]));await listAdminTaskAttempts(summary().taskId,50,'next')})
it('rejects unsafe count values instead of rounding',async()=>{respond(page([{...summary(),totalAttempts:Number.MAX_SAFE_INTEGER+1}]));await expect(listAdminTasks({createdFrom:'2026-10-01',createdTo:'2026-10-20',limit:20})).rejects.toMatchObject({code:'INVALID_RESPONSE'})})
it('preserves access denial',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({code:'ACCESS_DENIED',message:'无权限',data:null}),{status:403})));await expect(getAdminTask(summary().taskId)).rejects.toMatchObject({status:403})})

it('rejects incomplete detail before the view dereferences its reason',async()=>{const value=detail();delete (value.task as Partial<typeof value.task>).reason;respond(value);await expect(getAdminTask(summary().taskId)).rejects.toMatchObject({code:'INVALID_RESPONSE'})})
