import {expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import ElementPlus from 'element-plus'
import LoginPage from './LoginPage.vue'
import {useAuthStore} from '../../stores/auth'
vi.mock('../../stores/auth',()=>({useAuthStore:vi.fn()}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn().mockRejectedValue(new Error('Failed to fetch dynamically imported module: /assets/old.js'))}),useRoute:()=>({query:{unavailable:'1'}})}))
it('does not present page module failure as raw code or a continuing account outage',async()=>{const auth=reactive({profile:null as unknown,signIn:vi.fn(async()=>{auth.profile={userId:'one'}})});vi.mocked(useAuthStore).mockReturnValue(auth as never);const w=mount(LoginPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true}}});await w.get('form').trigger('submit');await flushPromises();expect(w.text()).not.toContain('账号服务暂不可用');expect(w.text()).not.toContain('Failed to fetch dynamically');expect(w.text()).toContain('页面资源');w.unmount()})

it('hides the old unavailable query after a healthy anonymous session check',()=>{const auth=reactive({profile:null,loaded:true,signIn:vi.fn()});vi.mocked(useAuthStore).mockReturnValue(auth as never);const w=mount(LoginPage,{global:{plugins:[ElementPlus],stubs:{RouterLink:true}}});expect(w.text()).not.toContain('账号服务暂不可用');w.unmount()})
