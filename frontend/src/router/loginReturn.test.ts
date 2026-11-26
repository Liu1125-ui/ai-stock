import { createMemoryHistory, createRouter } from 'vue-router'
import { expect, it } from 'vitest'
import { loginReturn } from './loginReturn'

const view = { template: '<p/>' }
const router = createRouter({ history: createMemoryHistory(), routes: [
  {path: '/predictions', component: view, meta: {requiresAuth: true}},
  {path: '/admin/tasks', component: view, meta: {requiresAuth: true, admin: true}},
  {path: '/login', component: view},
  {path: '/:pathMatch(.*)*', redirect: '/login'},
] })

it('retains saved record links and rejects external, public and unknown destinations', () => {
  expect(loginReturn(router, '/predictions?predictionId=record#result')).toBe('/predictions?predictionId=record#result')
  expect(loginReturn(router, '/admin/tasks')).toBe('/admin/tasks')
  for (const target of ['//example.com','https://example.com','/\\example.com','/login','/unknown',['/predictions']]) {
    expect(loginReturn(router, target)).toBeUndefined()
  }
})
