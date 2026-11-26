package com.aistock.prediction.portfolioanalysis;
import java.time.*;import java.util.*;import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
final class PortfolioTestEvidence {
 static final UUID OWNER=UUID.fromString("11111111-1111-4111-8111-111111111111");
 static final LocalDate DATE=LocalDate.parse("2026-09-25");static final Instant NOW=Instant.parse("2026-09-28T13:00:00Z");
 static PositionItem position(String id,String quantity,String cost){return new PositionItem(UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)),id,quantity,cost,"1");}
 static PositionSnapshot snapshot(PositionItem...items){return new PositionSnapshot(OWNER,"stock-user-service","2",List.of(items));}
 static Quote quote(String id,String price,LocalDate date){return new Quote(id,"AVAILABLE",null,price,date,UUID.nameUUIDFromBytes((id+price).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"a".repeat(64),"baostock","RAW","UNKNOWN",null,null,null,false);}
 static Quote missing(String id){return new Quote(id,"UNAVAILABLE","NO_DATA",null,null,null,null,"baostock","RAW","UNKNOWN",null,null,null,false);}
 static QuoteBatch batch(Quote...items){return new QuoteBatch(DATE,NOW,List.of(items));}
 static PositionSnapshot normal(){return snapshot(position("SH600030","100","20"),position("SH600276","200","10"));}
 static QuoteBatch prices(){return batch(quote("SH600030","25",DATE),quote("SH600276","12.5",DATE));}
 static Calculation calculation(){return new PortfolioCalculator().calculate(normal(),prices(),new java.math.BigDecimal("50"));}
}
