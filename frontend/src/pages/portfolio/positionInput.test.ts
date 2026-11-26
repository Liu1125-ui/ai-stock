import {expect,it} from 'vitest'
import {positionInput} from './positionInput'
it('keeps six decimal costs and odd-lot quantities as strings',()=>{expect(positionInput({quantity:'1',averageCost:'123.456789'})).toEqual({quantity:'1',averageCost:'123.456789'});expect(positionInput({quantity:'1',averageCost:'0'}).averageCost).toBe('0')})
it('rejects non-canonical and floating quantity syntax',()=>{for(const quantity of ['0','-1','1.5','1e3','01'])expect(()=>positionInput({quantity,averageCost:'1'})).toThrow()})
it('rejects negative, exponential and overprecision cost input',()=>{for(const averageCost of ['-1','NaN','1e3','0.1234567'])expect(()=>positionInput({quantity:'1',averageCost})).toThrow()})
