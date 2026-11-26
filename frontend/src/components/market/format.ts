/** Display-only decimal rounding; never changes the API's original string. */
export function formatDecimal(raw:string|null,places:number):string{
 if(raw===null)return '—'
 if(!/^-?\d+(\.\d+)?$/.test(raw)||places<0||places>12)return raw
 const negative=raw.startsWith('-'),unsigned=negative?raw.slice(1):raw
 const [whole,fraction='']=unsigned.split('.')
 const padded=fraction.padEnd(places+1,'0')
 let shifted=BigInt((whole||'0')+padded.slice(0,places))
 if(padded[places]>='5')shifted+=1n
 const digits=shifted.toString().padStart(places+1,'0')
 const value=places?digits.slice(0,-places)+'.'+digits.slice(-places):digits
 return (negative&&shifted!==0n?'-':'')+value
}
