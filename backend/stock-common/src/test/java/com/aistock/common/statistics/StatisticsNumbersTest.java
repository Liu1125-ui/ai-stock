package com.aistock.common.statistics;
import java.math.BigDecimal;import com.fasterxml.jackson.databind.ObjectMapper;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class StatisticsNumbersTest {
 @Test void zeroDenominatorIsNull(){assertNull(StatisticsNumbers.ratio(0,0));assertNull(StatisticsNumbers.meanMillis(BigDecimal.ZERO,0));}
 @Test void largeCountsRemainStrings()throws Exception{var json=new ObjectMapper().writeValueAsString(StatisticsNumbers.count(999999999999999999L));assertEquals("\"999999999999999999\"",json);}
 @Test void percentAndMillisecondsRoundWithoutBinaryFloat(){assertEquals("33.33",StatisticsNumbers.ratio(1,3));assertEquals("100.00",StatisticsNumbers.ratio(3,3));assertEquals("133.333",StatisticsNumbers.meanMillis(new BigDecimal("400"),3));assertEquals("0.000",StatisticsNumbers.meanMillis(BigDecimal.ZERO,1));}
 @Test void negativeMeasuresFailInsteadOfRenderingInvalidNumbers(){assertThrows(IllegalArgumentException.class,()->StatisticsNumbers.count(-1));assertThrows(IllegalArgumentException.class,()->StatisticsNumbers.ratio(-1,2));assertThrows(IllegalArgumentException.class,()->StatisticsNumbers.meanMillis(new BigDecimal("-1"),1));}
}
