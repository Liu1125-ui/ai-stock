import {it,expect,vi} from 'vitest'
import {auth} from '../pages/admin/tasks/taskFixtures'
vi.mock('../stores/auth',()=>({useAuthStore:()=>auth}))
vi.mock('../pages/admin/statistics/StatisticsPage.vue',()=>({default:{template:'<main>统计页面</main>'}}))
import {router} from './index'
it('statistics route requires administrator even on direct entry',async()=>{auth.profile.role='ADMIN';auth.refresh.mockResolvedValue(undefined);await router.push('/admin/statistics');expect(router.currentRoute.value.meta).toMatchObject({requiresAuth:true,admin:true});auth.profile.role='USER';await router.push('/profile');await router.push('/admin/statistics');expect(router.currentRoute.value.path).toBe('/profile')})
