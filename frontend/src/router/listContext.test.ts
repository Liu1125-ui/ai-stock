import {describe,expect,it} from 'vitest'
import {analysisReturn, listReturn, searchQuery, watchlistPage} from './listContext'

describe('workflow return context',()=>{
 it('preserves only supported search and watchlist parameters',()=>{
  expect(listReturn('/?q=%E4%B8%AD%E4%BF%A1')).toEqual({path:'/',query:{q:'中信'}})
  expect(listReturn('/watchlist?page=3')).toEqual({path:'/watchlist',query:{page:'3'}})
  expect(searchQuery(' 中信 ')).toBe('中信')
  expect(watchlistPage('003')).toBe(1)
  expect(watchlistPage('3')).toBe(3)
 })
 it('rejects external destinations, unknown query keys and other stocks',()=>{
  for(const unsafe of ['//example.com','https://example.com','/\\example.com','/login','/?q=a&q=b','/watchlist?page=0','/?ownerId=other','/watchlist#x'])
   expect(listReturn(unsafe)).toBeNull()
  expect(analysisReturn('/market/SZ000002','SH600030')).toBeNull()
 })
 it('preserves the original stock and the validated list route through analysis',()=>{
  expect(analysisReturn('/market/SH600030?from=%2Fwatchlist%3Fpage%3D3','SH600030'))
   .toEqual({path:'/market/SH600030',query:{from:'/watchlist?page=3'}})
 })
})
