package com.aistock.market.forecast;
import com.aistock.common.BusinessException;
import com.aistock.market.quotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ForecastInputValidatorTest {
 @Test void extremeRawExponentsAreRejectedBeforeHashExpansion(){
  var now=Instant.parse("2026-10-02T14:00:00Z");var validator=new ForecastInputValidator(Clock.fixed(now,ZoneOffset.UTC));
  var start=LocalDate.of(2026,7,1);var calendar=new ArrayList<TradingDay>();for(int i=0;i<100;i++)calendar.add(new TradingDay(start.plusDays(i),true));
  for(var value:List.of("1E+100000000","1E-100000000","1E+2147483647"))for(int field=0;field<7;field++){
   var rows=new ArrayList<ForecastCandidate.PairedBar>();
   for(int i=0;i<60;i++){var numbers=new BigDecimal[]{BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,new BigDecimal("100"),new BigDecimal("1000"),new BigDecimal("20")};if(i==0)numbers[field]=new BigDecimal(value);
    rows.add(new ForecastCandidate.PairedBar(new RawBar(start.plusDays(i),numbers[0],numbers[1],numbers[2],numbers[3],numbers[4],numbers[5],1,null),numbers[6]));}
   var c=new ForecastCandidate("exponent","SH600030","11111111-1111-1111-1111-111111111111","baostock",ForecastInputValidator.DEFINITION,now,"a".repeat(64),rows,calendar);
   assertEquals("FORECAST_INPUT_INVALID",assertThrows(BusinessException.class,()->validator.validate(c),value+" field "+field).code());
  }
 }
}
