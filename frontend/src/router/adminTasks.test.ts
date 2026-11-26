import {it,expect,vi} from 'vitest'
import {auth} from '../pages/admin/tasks/taskFixtures'
vi.mock('../stores/auth',()=>({useAuthStore:()=>auth}))
import {router} from './index'
it('task route is administrator-only including direct navigation',async()=>{auth.profile.role='ADMIN';auth.refresh.mockResolvedValue(undefined);await router.push('/admin/tasks');expect(router.currentRoute.value.meta).toMatchObject({requiresAuth:true,admin:true});auth.profile.role='USER';await router.push('/profile');await router.push('/admin/tasks');expect(router.currentRoute.value.path).toBe('/profile')})
