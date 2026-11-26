import {expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import {reactive} from 'vue'
import ElementPlus from 'element-plus'
import App from './App.vue'
import {useAuthStore} from './stores/auth'
vi.mock('./stores/auth',()=>({useAuthStore:vi.fn()}))
vi.mock('./stores/watchlist',()=>({useWatchlistStore:()=>({clear:vi.fn()})}))
vi.mock('./stores/inbox',()=>({useInboxStore:()=>({count:null,refresh:vi.fn()})}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
it('clears a stale startup account warning when authenticated identity recovers',async()=>{const auth=reactive({profile:null as unknown,isAdmin:false,refresh:vi.fn().mockRejectedValue(new Error('offline'))});vi.mocked(useAuthStore).mockReturnValue(auth as never);const w=mount(App,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,RouterView:true}}});await flushPromises();expect(w.text()).toContain('账号服务暂时无法连接');auth.profile={userId:'one',nickname:'恢复账号'};await flushPromises();expect(w.text()).not.toContain('账号服务暂时无法连接');w.unmount()})

it('shows a manual page recovery hint without calling it an account failure',async()=>{const auth=reactive({profile:null,isAdmin:false,refresh:vi.fn().mockResolvedValue(undefined)});vi.mocked(useAuthStore).mockReturnValue(auth as never);const w=mount(App,{global:{plugins:[ElementPlus],stubs:{RouterLink:true,RouterView:true}}});await flushPromises();window.dispatchEvent(new Event('aistock:page-unavailable'));await flushPromises();expect(w.text()).toContain('页面资源加载失败');expect(w.text()).not.toContain('账号服务暂时无法连接');w.unmount()})

it('shows task management only for an administrator',async()=>{const auth=reactive({profile:{userId:'admin',nickname:'管理员'},isAdmin:true,refresh:vi.fn().mockResolvedValue(undefined)});vi.mocked(useAuthStore).mockReturnValue(auth as never);const w=mount(App,{global:{plugins:[ElementPlus],stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'},RouterView:true}}});await flushPromises();expect(w.find('a[href="/admin/tasks"]').exists()).toBe(true);auth.isAdmin=false;await flushPromises();expect(w.find('a[href="/admin/tasks"]').exists()).toBe(false);w.unmount()})

it('keeps all user destinations visible and groups admin destinations separately', async () => {
 const auth = reactive({profile:{userId:'admin',nickname:'管理员'},isAdmin:true,refresh:vi.fn().mockResolvedValue(undefined)})
 vi.mocked(useAuthStore).mockReturnValue(auth as never)
 const w = mount(App,{global:{plugins:[ElementPlus],stubs:{RouterLink:{props:['to'],template:'<a :href="to"><slot/></a>'},RouterView:true}}})
 try {
  await flushPromises()
  const userNav = w.get('nav[aria-label="主导航"]')
  expect(userNav.findAll('a').map(link=>link.attributes('href'))).toEqual(['/', '/watchlist', '/portfolio', '/comparisons', '/predictions', '/training', '/alerts', '/messages'])
  expect(w.get('a[href="/profile"]').text()).toContain('个人中心')
  const admin = w.get('details')
  expect(admin.get('summary').text()).toContain('管理后台')
  expect(admin.findAll('a').map(link=>link.attributes('href'))).toEqual(['/admin/users','/admin/models','/admin/tasks','/admin/data','/admin/statistics'])
  auth.isAdmin = false
  await flushPromises()
  expect(w.find('details').exists()).toBe(false)
 }finally{w.unmount()}
})
