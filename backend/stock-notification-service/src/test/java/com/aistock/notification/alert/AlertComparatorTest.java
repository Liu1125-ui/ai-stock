package com.aistock.notification.alert;
import java.math.BigDecimal;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class AlertComparatorTest {
 BigDecimal d(String s){return new BigDecimal(s);}
 @Test void priceEqualityNeverTriggersAndTinyDifferencesRemainExact(){assertFalse(AlertComparator.matches("PRICE_ABOVE",d("26"),d("26"),null,null));assertFalse(AlertComparator.matches("PRICE_BELOW",d("26"),d("26"),null,null));assertTrue(AlertComparator.matches("PRICE_ABOVE",d("26"),d("26.000000000001"),null,null));assertTrue(AlertComparator.matches("PRICE_BELOW",d("26"),d("25.999999999999"),null,null));}
 @Test void rateUsesStrictCrossMultiplicationWithoutRounding(){assertFalse(AlertComparator.matches("DAILY_RISE",d("2"),null,d("100"),d("102")));assertTrue(AlertComparator.matches("DAILY_RISE",d("2"),null,d("100"),d("102.000000000001")));assertFalse(AlertComparator.matches("DAILY_FALL",d("2"),null,d("100"),d("98")));assertTrue(AlertComparator.matches("DAILY_FALL",d("2"),null,d("100"),d("97.999999999999")));}
 @Test void rawExRightsDropDoesNotBecomeAdjustedFall(){assertFalse(AlertComparator.matches("DAILY_FALL",d("2"),d("50"),d("100"),d("100")));}
 @Test void invalidInputsCannotSilentlyReturnFalse(){assertThrows(com.aistock.common.BusinessException.class,()->AlertComparator.matches("DAILY_RISE",d("2"),null,d("0"),d("100")));assertThrows(com.aistock.common.BusinessException.class,()->AlertComparator.matches("UNKNOWN",d("2"),d("100"),null,null));assertThrows(com.aistock.common.BusinessException.class,()->AlertComparator.matches("PRICE_ABOVE",d("-1"),d("100"),null,null));}
}
