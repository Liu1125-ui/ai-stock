const priceBases:Record<string,string>={
 RAW:'未复权收盘价',
 BACKWARD:'后复权收盘价',
 ORIGIN_SHARE_BASIS:'原点股本口径',
}
export function priceBasisLabel(value:string){
 return priceBases[value]??value
}
