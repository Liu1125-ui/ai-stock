import {beforeEach,expect,it,vi} from 'vitest'
import {setActivePinia,createPinia} from 'pinia'
import {reactive} from 'vue'
import {useInboxStore} from './inbox'
import {useAuthStore} from './auth'
import {unreadCount} from '../api/messages'
vi.mock('../api/messages',()=>({unreadCount:vi.fn()}));vi.mock('./auth',()=>({useAuthStore:vi.fn()}))
const auth=reactive<{profile:{userId:string}|null}>({profile:{userId:'a'}})
beforeEach(()=>{setActivePinia(createPinia());vi.resetAllMocks();auth.profile={userId:'a'};vi.mocked(useAuthStore).mockReturnValue(auth as never)})
it('clears count immediately on switch and ignores late previous-owner count',async()=>{let resolve!:(v:{unreadCount:number})=>void;vi.mocked(unreadCount).mockReturnValueOnce(new Promise(r=>resolve=r)).mockResolvedValue({unreadCount:2});const store=useInboxStore();const old=store.refresh();auth.profile={userId:'b'};expect(store.count).toBeNull();await store.refresh();resolve({unreadCount:99});await old;expect(store.count).toBe(2);auth.profile=null;expect(store.count).toBeNull()})
it('shows unknown rather than zero when unread lookup fails',async()=>{vi.mocked(unreadCount).mockRejectedValue(new Error('down'));const store=useInboxStore();await store.refresh();expect(store.count).toBeNull()})
