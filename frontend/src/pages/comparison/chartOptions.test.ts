import {expect,it} from 'vitest'
import {chartOptions} from './chartOptions'
it('uses server normalized values and leaves gaps disconnected',()=>{const data=[{instrumentId:'SH600030',chart:[{date:'2026-10-07',normalizedClose:'100.000000'},{date:'2026-10-08',normalizedClose:null},{date:'2026-10-09',normalizedClose:'99.000000'}]}];const result=chartOptions(data);expect(result.series[0].connectNulls).toBe(false);expect(result.series[0].data).toEqual([100,null,99]);expect(result.yAxis.name).toContain('100')})
