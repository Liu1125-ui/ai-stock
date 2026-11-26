import type { Router } from 'vue-router'

export function loginReturn(router: Router, value: unknown): string | undefined {
  if (typeof value !== 'string' || value.length > 3200
      || !/^\/(?!\/)/.test(value) || /[\\\u0000-\u0020\u007f]/.test(value)) return
  const target = router.resolve(value)
  if (target.matched.some(record => record.meta.requiresAuth && !record.redirect)) return target.fullPath
}
