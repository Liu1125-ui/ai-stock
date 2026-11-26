type Target = {path: string; query?: Record<string,string>}

const stockCode = /^(SH|SZ)[0-9]{6}$/
export function validStock(value: unknown) {
  return typeof value === 'string' && stockCode.test(value) ? value : ''
}
export function searchQuery(value: unknown) {
  if (typeof value !== 'string') return ''
  return Array.from(value.replace(/[\u0000-\u001f\u007f]/g,'').trim()).slice(0,64).join('')
}
export function watchlistPage(value: unknown) {
  if (typeof value !== 'string' || !/^(?:[1-9][0-9]{0,3}|10000)$/.test(value)) return 1
  return Number(value)
}
function parts(value: unknown) {
  if (typeof value !== 'string' || value.length > 3200 || !value.startsWith('/')
      || value.startsWith('//') || /[\\#\u0000-\u001f\u007f]/.test(value)) return null
  const delimiter = value.indexOf('?')
  const path = delimiter < 0 ? value : value.slice(0,delimiter)
  const query = new URLSearchParams(delimiter < 0 ? '' : value.slice(delimiter+1))
  return {path,query}
}
function onlyKeys(query: URLSearchParams, allowed: string[]) {
  const keys: string[] = []
  query.forEach((_value,key) => keys.push(key))
  return keys.every(key => allowed.includes(key)) && new Set(keys).size === keys.length
}
export function listReturn(value: unknown): Target|null {
  const route = parts(value)
  if (!route) return null
  const {path,query} = route
  if (path === '/' && onlyKeys(query,['q'])) {
    const q = searchQuery(query.get('q'))
    return q ? {path,query:{q}} : {path}
  }
  if (path === '/watchlist' && onlyKeys(query,['page'])) {
    const input = query.get('page')
    if (input !== null && String(watchlistPage(input)) !== input) return null
    const page = watchlistPage(input)
    return page > 1 ? {path,query:{page:String(page)}} : {path}
  }
  return null
}
export function analysisReturn(value: unknown, stock: unknown): Target|null {
  const id = validStock(stock), route = parts(value)
  if (!id || !route || route.path !== '/market/'+id || !onlyKeys(route.query,['from','period'])) return null
  const query:Record<string,string> = {}
  const from = route.query.get('from')
  const list = listReturn(from)
  if (list) query.from = contextPath(list)
  const period = route.query.get('period')
  if (period === '6m' || period === '1y') query.period = period
  return Object.keys(query).length ? {path:route.path,query} : {path:route.path}
}
export function contextPath(target: Target) {
  const query = new URLSearchParams(target.query)
  return target.path+(query.size?'?'+query.toString():'')
}
export function stockRoute(path:string, from?:string, query:Record<string,string> = {}) {
  const parameters = from ? {...query,from} : query
  return Object.keys(parameters).length ? {path,query:parameters} : path
}
