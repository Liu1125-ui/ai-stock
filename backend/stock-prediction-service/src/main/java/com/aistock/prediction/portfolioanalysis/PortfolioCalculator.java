package com.aistock.prediction.portfolioanalysis;
import java.math.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.*;
import com.fasterxml.jackson.databind.*;
import static com.aistock.prediction.portfolioanalysis.PortfolioRecords.*;
public final class PortfolioCalculator {
 private static final BigDecimal HUNDRED=new BigDecimal("100");
 private final PortfolioSourceVerifier verifier=new PortfolioSourceVerifier();
 public Calculation calculate(PositionSnapshot s,QuoteBatch batch,BigDecimal threshold){
  verifier.verify(s,batch,s==null?null:s.ownerId(),batch==null?null:batch.asOfDate());
  if(threshold==null||threshold.signum()<=0||threshold.compareTo(HUNDRED)>0||threshold.scale()>6)throw PortfolioErrors.invalid();
  var positions=s.items().stream().sorted(Comparator.comparing(PositionItem::instrumentId)).toList();var quotes=new HashMap<String,Quote>();batch.items().forEach(q->quotes.put(q.instrumentId(),q));
  var dates=new HashSet<java.time.LocalDate>();int valued=0;BigDecimal totalCost=BigDecimal.ZERO,totalMarket=BigDecimal.ZERO;
  var preliminary=new ArrayList<MoneyLine>();String freshness=positions.isEmpty()?"UNKNOWN":"FRESH";
  for(var p:positions){var q=quotes.get(p.instrumentId());var qty=new BigDecimal(p.quantity());var cost=qty.multiply(new BigDecimal(p.averageCost())).setScale(2,RoundingMode.HALF_UP);BigDecimal market=null,profit=null;
   if("AVAILABLE".equals(q.status())){market=qty.multiply(new BigDecimal(q.close())).setScale(2,RoundingMode.HALF_UP);profit=market.subtract(cost);dates.add(q.priceDate());valued++;totalCost=totalCost.add(cost);totalMarket=totalMarket.add(market);}
   if("UNKNOWN".equals(q.freshness()))freshness="UNKNOWN";else if("STALE".equals(q.freshness())&&!"UNKNOWN".equals(freshness))freshness="STALE";
   preliminary.add(new MoneyLine(p.positionId(),p.instrumentId(),p.quantity(),p.averageCost(),p.revision(),q,money(cost),money(market),money(profit),profit==null?null:percent(profit,cost),null,null));
  }
  boolean mixed=dates.size()>1;String state=positions.isEmpty()?"EMPTY":valued<positions.size()?"PARTIAL":mixed?"MIXED_DATES":"COMPLETE";
  Amounts subtotal=valued==0&&!positions.isEmpty()?new Amounts(null,null,null,null):amounts(totalCost,totalMarket);
  Amounts totals="COMPLETE".equals(state)?subtotal:null;boolean weights=totals!=null&&totalMarket.signum()>0;
  var lines=new ArrayList<MoneyLine>();for(var line:preliminary){var market=line.marketValue()==null?null:new BigDecimal(line.marketValue());lines.add(new MoneyLine(line.positionId(),line.instrumentId(),line.quantity(),line.averageCost(),line.positionRevision(),line.quote(),line.costAmount(),line.marketValue(),line.floatingProfit(),line.returnPercent(),weights?percent(market,totalMarket):null,weights?market.multiply(HUNDRED).compareTo(totalMarket.multiply(threshold))>0:null));}
  String canonicalThreshold=threshold.stripTrailingZeros().toPlainString();String hash=hash(s,batch,positions,canonicalThreshold);
  return new Calculation(FORMULA,s.bookRevision(),batch.asOfDate(),batch.capturedAt(),state,freshness,mixed,positions.size(),valued,List.copyOf(lines),subtotal,totals,canonicalThreshold,hash);
 }
 private Amounts amounts(BigDecimal cost,BigDecimal market){var profit=market.subtract(cost);return new Amounts(money(cost),money(market),money(profit),percent(profit,cost));}
 private String money(BigDecimal value){return value==null?null:value.setScale(2,RoundingMode.HALF_UP).toPlainString();}
 private String percent(BigDecimal value,BigDecimal denominator){return denominator.signum()==0?null:value.multiply(HUNDRED).divide(denominator,2,RoundingMode.HALF_UP).toPlainString();}
 private String hash(PositionSnapshot s,QuoteBatch b,List<PositionItem> positions,String threshold){try{
  var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  var canonical=List.of(FORMULA,s.ownerId(),s.bookRevision(),b.asOfDate(),b.capturedAt(),threshold,positions,b.items().stream().sorted(Comparator.comparing(Quote::instrumentId)).toList());
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical)));
 }catch(Exception e){throw PortfolioErrors.invalid();}}
}
