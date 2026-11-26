import {request} from './auth'
export interface WatchlistItem{instrumentId:string;createdAt:string}
export interface WatchlistPage{items:WatchlistItem[];page:number;size:number;total:number}
export const listWatchlist=(page=1,size=20)=>request<WatchlistPage>(`/users/me/watchlist?page=${page}&size=${size}`)
export const addWatchlist=(id:string)=>request<WatchlistItem>('/users/me/watchlist','POST',{instrumentId:id})
export const removeWatchlist=(id:string)=>request<null>(`/users/me/watchlist/${encodeURIComponent(id)}`,'DELETE')
