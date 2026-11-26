import {expect,it} from 'vitest'
import {comparisonCsv,toCsv} from './savedResultExport'
import {result} from '../pages/comparison/fixtures'

it('quotes CSV fields, preserves signed decimals and neutralizes spreadsheet expressions',()=>{
 const file=toCsv([['名称','问号,引号"','-12.3400',' =2+2','@SUM(A1)','\tbad',null]])
 expect(file).toBe(`\uFEFF"名称","问号,引号""","-12.3400","' =2+2","'@SUM(A1)","'\tbad",""\r\n`)
})
it('exports the selected comparison with fixed dates, observation count and missing reasons',()=>{
 const file=toCsv(comparisonCsv(result))
 expect(file).toContain(result.comparisonId)
 expect(file).toContain(result.requestedRange.from)
 expect(file).toContain(result.actualRange!.from)
 expect(file).toContain('VOLUME_GAP_OR_SUSPENSION')
 expect(file).toContain('日均成交量","",')
})
