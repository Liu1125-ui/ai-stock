import {beforeEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import ElementPlus from 'element-plus'
import NotificationPreferences from './NotificationPreferences.vue'
import * as api from '../../api/alerts'
import {useAuthStore} from '../../stores/auth'
vi.mock('../../api/alerts',()=>({getPreferences:vi.fn(),patchPreferences:vi.fn()}))
vi.mock('../../stores/auth',()=>({useAuthStore:vi.fn()}))
const auth=reactive({profile:{userId:'a'}})
beforeEach(()=>{vi.clearAllMocks();auth.profile={userId:'a'};vi.mocked(useAuthStore).mockReturnValue(auth as unknown as ReturnType<typeof useAuthStore>);vi.mocked(api.getPreferences).mockResolvedValue({emailEnabled:false,revision:0,emailVerified:true,deliveryAvailability:'NOT_ENABLED'})})
it('default email preference is off and save does not promise delivery',async()=>{vi.mocked(api.patchPreferences).mockResolvedValue({emailEnabled:true,revision:1,emailVerified:true,deliveryAvailability:'NOT_ENABLED'});const w=mount(NotificationPreferences,{global:{plugins:[ElementPlus]}});await flushPromises();expect(w.text()).toContain('当前关闭');await w.get('button[data-testid="toggle-email"]').trigger('click');await flushPromises();expect(api.patchPreferences).toHaveBeenCalledWith(0,true);expect(w.text()).toContain('投递尚未启用');w.unmount()})
it('late owner preference cannot overwrite the next owner',async()=>{let release!:(v:api.NotificationPreference)=>void;vi.mocked(api.getPreferences).mockReturnValueOnce(new Promise(r=>{release=r})).mockResolvedValue({emailEnabled:false,revision:0,emailVerified:false,deliveryAvailability:'NOT_ENABLED'});const w=mount(NotificationPreferences,{global:{plugins:[ElementPlus]}});await flushPromises();auth.profile={userId:'b'};await flushPromises();release({emailEnabled:true,revision:1,emailVerified:true,deliveryAvailability:'NOT_ENABLED'});await flushPromises();expect(w.text()).toContain('邮箱未验证');expect(w.text()).not.toContain('当前已开启');w.unmount()})
it('shows service readiness independently of personal preference',async()=>{vi.mocked(api.getPreferences).mockResolvedValue({emailEnabled:false,revision:0,emailVerified:true,deliveryAvailability:'ENABLED'});const w=mount(NotificationPreferences,{global:{plugins:[ElementPlus]}});await flushPromises();expect(w.text()).toContain('邮件服务可用');expect(w.text()).toContain('当前关闭');w.unmount()})
