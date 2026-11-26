import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import {createMemoryHistory,createRouter} from 'vue-router'
import ElementPlus from 'element-plus'
import RecentMessages from './RecentMessages.vue'
import * as api from '../../api/messages'
import {useAuthStore} from '../../stores/auth'
vi.mock('../../api/messages',()=>({listMessages:vi.fn()}));vi.mock('../../stores/auth',()=>({useAuthStore:vi.fn()}))
const auth=reactive({profile:{userId:'a'}})
beforeEach(()=>{vi.resetAllMocks();auth.profile={userId:'a'};vi.mocked(useAuthStore).mockReturnValue(auth as never);vi.mocked(api.listMessages).mockResolvedValue({items:[],page:1,size:3,total:0,unreadCount:0})})
it('uses existing bounded list endpoint and offers message center',async()=>{const router=createRouter({history:createMemoryHistory(),routes:[{path:'/',component:RecentMessages},{path:'/messages',component:{template:'<div/>'}}]});await router.push('/');const w=mount(RecentMessages,{global:{plugins:[router,ElementPlus]}});await flushPromises();expect(api.listMessages).toHaveBeenCalledWith(1,3,false);expect(w.get('a').attributes('href')).toBe('/messages');expect(w.text()).toContain('暂无触发消息');w.unmount()})
