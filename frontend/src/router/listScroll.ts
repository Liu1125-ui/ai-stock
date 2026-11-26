import {nextTick, onBeforeUnmount} from 'vue'
import {onBeforeRouteLeave,useRoute} from 'vue-router'
import {useAuthStore} from '../stores/auth'
import {contextPath,listReturn} from './listContext'

const positions = new Map<string,number>()

export function useListScroll() {
  const route = useRoute()
  const auth = useAuthStore()
  let active = true
  function key(path: string) {
    const target = listReturn(path)
    const owner = auth.profile?.userId
    return owner && target ? owner+'\n'+contextPath(target) : null
  }
  onBeforeRouteLeave((_to,from) => {
    const position = key(from.fullPath)
    if (!position) return
    positions.delete(position)
    positions.set(position,window.scrollY)
    if (positions.size>32) positions.delete(positions.keys().next().value!)
  })
  onBeforeUnmount(() => {active=false})

  return async function restore(path: string) {
    const position = key(path)
    if (!position || !positions.has(position)) return
    await nextTick()
    if (active && key(route.fullPath)===position) window.scrollTo(0,positions.get(position)!)
  }
}
