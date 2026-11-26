package com.aistock.notification.alertmail;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class AlertMailTickerTest {
 @Test void disabledSendingStillRunsMaintenanceTicker(){
  var worker=mock(AlertMailWorker.class);var clock=Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"),ZoneOffset.UTC);
  var policy=new AlertMailConfiguration.Policy(false,3,Duration.ofSeconds(120),Duration.ofDays(1),500,60,300);
  var ticker=new AlertMailConfiguration.Ticker(worker,clock,policy);
  try{ticker.start();verify(worker,timeout(2500).atLeastOnce()).runOnce(clock.instant());}finally{ticker.stop();}
 }
}
