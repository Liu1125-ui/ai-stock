export function positionInput(input:{quantity:string;averageCost:string}){
 if(!/^[1-9][0-9]{0,18}$/.test(input.quantity)||input.averageCost.length>30||!/^(0|[1-9][0-9]*)(\.[0-9]{1,6})?$/.test(input.averageCost))throw new Error('请输入正整数股数和非负平均成本，成本最多保留6位小数')
 return {quantity:input.quantity,averageCost:input.averageCost}
}
