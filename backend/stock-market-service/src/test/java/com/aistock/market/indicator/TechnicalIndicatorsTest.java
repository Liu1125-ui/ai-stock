package com.aistock.market.indicator;

import com.aistock.market.quotation.RawBar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechnicalIndicatorsTest {
    private final TechnicalIndicators calculator = new TechnicalIndicators();
    private List<RawBar> bars(int count, int step) {
        var rows = new ArrayList<RawBar>();
        for (int i=0;i<count;i++) {
            var close = BigDecimal.valueOf(100L + (long)i*step);
            rows.add(new RawBar(LocalDate.of(2025,1,2).plusDays(i),close,close,close,close,
                new BigDecimal("1000"),new BigDecimal("100000"),1,null));
        }
        return rows;
    }
    @Test void fifthPriceHasFirstMa5() {
        var result=calculator.calculate(bars(40,0));
        assertNull(result.get(3).ma5());
        assertEquals(0,result.get(4).ma5().compareTo(new BigDecimal("100")));
        assertNull(result.get(8).ma10());
        assertEquals(0,result.get(9).ma10().compareTo(new BigDecimal("100")));
        assertNull(result.get(18).ma20());
        assertEquals(0,result.get(19).ma20().compareTo(new BigDecimal("100")));
    }
    @Test void flatRsiBeginsWithFourteenChangesAndMacdNeedsNineDifValues() {
        var result=calculator.calculate(bars(40,0));
        assertNull(result.get(13).rsi14());
        assertEquals(0,result.get(14).rsi14().compareTo(new BigDecimal("50")));
        assertNull(result.get(24).dif());
        assertEquals(0,result.get(25).dif().compareTo(BigDecimal.ZERO));
        assertNull(result.get(32).dea());
        assertEquals(0,result.get(33).macd().compareTo(BigDecimal.ZERO));
    }
    @Test void linearPricesHaveHandComputedMaRsiAndMacd() {
        var rising=calculator.calculate(bars(40,1));
        var falling=calculator.calculate(bars(40,-1));
        assertEquals(0,rising.get(4).ma5().compareTo(new BigDecimal("102")));
        assertEquals(0,rising.get(14).rsi14().compareTo(new BigDecimal("100")));
        assertEquals(0,falling.get(14).rsi14().compareTo(BigDecimal.ZERO));
        assertEquals(0,rising.get(25).dif().compareTo(new BigDecimal("7")));
        assertEquals(0,rising.get(33).dea().compareTo(new BigDecimal("7")));
    }
    @Test void suspendedRowHasNullIndicatorsAndDoesNotAdvanceSamples() {
        var rows=bars(5,0);
        var frozen=rows.get(2);
        rows.set(2,new RawBar(frozen.date(),frozen.open(),frozen.high(),frozen.low(),frozen.close(),null,null,0,null));
        var result=calculator.calculate(rows);
        assertNull(result.get(2).ma5());
        assertNull(result.get(4).ma5());
        assertEquals(5,result.size());
        assertTrue(calculator.calculate(List.of()).isEmpty());
    }
}
