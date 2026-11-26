import {expect,it} from 'vitest'
import {formatDecimal} from './format'
it('formats raw decimal text with HALF_UP without changing value',()=>{expect(formatDecimal('26.650000000000',4)).toBe('26.6500');expect(formatDecimal('-0.078691820510',4)).toBe('-0.0787');expect(formatDecimal('38.243840648425',2)).toBe('38.24');expect(formatDecimal(null,4)).toBe('—')})
