import {afterEach,expect,it,vi} from 'vitest'
import {mount,flushPromises} from '@vue/test-utils'
import ElementPlus,{ElMessageBox} from 'element-plus'
import UsersPage from './UsersPage.vue'
import * as api from '../../api/userAdmin'
import {canManageUser} from './policy'
vi.mock('../../api/userAdmin',()=>({listUsers:vi.fn(),setExperimentGrant:vi.fn(),setUserStatus:vi.fn()}))
vi.mock('../../stores/auth',()=>({useAuthStore:()=>({profile:{role:'ADMIN'}})}))
afterEach(()=>vi.restoreAllMocks())
it('only offers controls for ordinary accounts to an administrator',()=>{
 expect(canManageUser('USER','USER')).toBe(false)
 expect(canManageUser('ADMIN','ADMIN')).toBe(false)
 expect(canManageUser('ADMIN','USER')).toBe(true)
})
it('reloads current user revision after a conflicting administrator update',async()=>{
 const user={userId:'one',email:'member@example.com',nickname:'测试',role:'USER' as const,status:'ENABLED' as const,emailVerified:true,experimentAllowed:false,revision:0}
 vi.mocked(api.listUsers).mockResolvedValueOnce({items:[user],total:1,page:1,size:20})
  .mockResolvedValueOnce({items:[{...user,experimentAllowed:true,revision:1}],total:1,page:1,size:20})
 vi.mocked(api.setExperimentGrant).mockRejectedValue({status:409,message:'资料已更新'})
 // Element Plus declares an intersection across confirm/prompt overloads; confirm resolves an Action.
 vi.spyOn(ElMessageBox,'confirm').mockResolvedValue('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
 const wrapper=mount(UsersPage,{global:{plugins:[ElementPlus]}})
 try{await flushPromises();await wrapper.findAll('button').find(b=>b.text()==='授予实验权限')!.trigger('click');await flushPromises()
  expect(wrapper.text()).toContain('撤销实验权限');expect(wrapper.text()).toContain('资料已更新');expect(api.listUsers).toHaveBeenCalledTimes(2)
 }finally{wrapper.unmount()}
})
