import { createMemoryHistory, createRouter } from 'vue-router'
import { expect, it, vi } from 'vitest'
import { installAccountGuards } from './guards'

const auth = vi.hoisted(() => ({ refresh: vi.fn(), profile: null as object | null, isAdmin: false }))
vi.mock('../stores/auth', () => ({ useAuthStore: () => auth }))

it('preserves a deep link while logged out and checks role on return', async () => {
  const view = { template: '<p/>' }
  const router = createRouter({ history: createMemoryHistory(), routes: [
    {path: '/admin/tasks', component: view, meta: {requiresAuth: true, admin: true}},
    {path: '/login', component: view},
    {path: '/profile', component: view, meta: {requiresAuth: true}}
  ]})
  installAccountGuards(router)
  const original = '/admin/tasks?type=DATA#detail'

  await router.push(original)
  expect(router.currentRoute.value.path).toBe('/login')
  expect(router.currentRoute.value.query.returnTo).toBe(original)

  auth.profile = {role: 'USER'}
  await router.push(original)
  expect(router.currentRoute.value.path).toBe('/profile')
})
