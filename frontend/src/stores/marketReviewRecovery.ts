/** Clear only the prior account's review commands; normal navigation preserves recovery. */
export function clearMarketReviewOwner(owner:string){
 const prefix='aistock:market-review:'+owner+':'
 try{const keys:string[]=[];for(let i=0;i<sessionStorage.length;i++){const key=sessionStorage.key(i);if(key?.startsWith(prefix))keys.push(key)}for(const key of keys)sessionStorage.removeItem(key)}catch{/* Storage may be disabled; server identity remains authoritative. */}
}
