package com.aistock.market.comparison;
import com.aistock.common.BusinessException;
import com.aistock.market.calendar.*;
import com.aistock.market.quotation.*;
import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.*;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.aistock.market.comparison.ComparisonUpdateRecords.*;
/** Read-only readiness and immutable RAW lookup. No collector is invoked here. */
@Service
public class ComparisonUpdateQuery {
 private final MarketVersionRepository market;private final CalendarService calendars;private final Clock clock;
 private final JdbcTemplate jdbc;private final TransactionTemplate read;private final ComparisonEvidenceRepository evidence;
 public ComparisonUpdateQuery(MarketVersionRepository market,CalendarService calendars,Clock clock){
  this.market=market;this.calendars=calendars;this.clock=clock;this.jdbc=market.jdbc();
  read=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
  read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);read.setTimeout(5);
  evidence=new ComparisonEvidenceRepository(market,new ObjectMapper().findAndRegisterModules()
   .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),clock);
 }
 static void validateIds(List<String> ids){
  if(ids==null||ids.isEmpty()||ids.size()>50||ids.stream().anyMatch(id->id==null||!id.matches("(SH|SZ)[0-9]{6}"))
    ||new HashSet<>(ids).size()!=ids.size())throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
 }
 public Options options(List<String> instruments){
  validateIds(instruments);var ids=List.copyOf(instruments);var asOf=clock.instant().truncatedTo(ChronoUnit.MILLIS);
  return read.execute(tx->new Options(asOf,ComparisonEvidenceValidator.DEFINITION,ids.stream().map(id->item(id,asOf)).toList()));
 }
 private Item item(String id,Instant asOf){
  var rows=jdbc.queryForList("SELECT v.* FROM instrument_data_head h JOIN market_data_version v ON v.version_id=h.version_id JOIN instrument i ON i.instrument_id=h.instrument_id WHERE h.instrument_id=? AND h.provider='baostock' AND h.price_basis='RAW' AND i.demo_only=false AND i.listing_status='ACTIVE'",id);
  if(rows.isEmpty())return state(id,"NO_RAW_VERSION",null,null,null,null,null);
  var row=rows.get(0);var raw=UUID.fromString((String)row.get("version_id"));var hash=(String)row.get("content_hash");
  if(!"AVAILABLE".equals(row.get("status"))||!id.equals(row.get("instrument_id"))||!"baostock".equals(row.get("provider"))||!"RAW".equals(row.get("price_basis")))
   return state(id,"SOURCE_INCOMPATIBLE",raw,hash,null,null,null);
  var bars=market.bars(raw.toString());var rawDays=market.calendar(raw.toString());
  if(bars.isEmpty())return state(id,"RAW_LAGGING",raw,hash,null,null,null);
  LocalDate target;
  try{target=target(bars.get(bars.size()-1).date(),asOf,rawDays);}
  catch(BusinessException e){return state(id,"CALENDAR_UNAVAILABLE",raw,hash,null,null,null);}
  var from=target.minusYears(1);
  if(bars.stream().noneMatch(b->b.date().equals(target)))return state(id,"RAW_LAGGING",raw,hash,from,target,null);
  try{ComparisonUpdatePolicy.calendar(rawDays,from,target);}
  catch(BusinessException e){return state(id,"INSUFFICIENT_HISTORY",raw,hash,from,target,null);}
  ComparisonEvidenceRecords.Evidence latest=null;
  for(var period:List.of("1Y","6M","3M")){
   var windowStart=ComparisonEvidenceOptions.start(target,period);
   var e=evidence.latestCovering(id,windowStart,target);if(latest==null)latest=e;
   if(e==null||!"baostock".equals(e.provider())||!"BACKWARD".equals(e.basis())||!target.equals(e.to())
     ||e.days().stream().noneMatch(d->target.equals(d.date())&&!"MISSING".equals(d.priceState())))
    return state(id,"READY",raw,hash,from,target,latest);
   var bound=jdbc.queryForList("SELECT content_hash,status,provider,price_basis FROM market_data_version WHERE version_id=?",e.rawVersionId().toString());
   if(bound.isEmpty()||!e.rawContentHash().equals(bound.get(0).get("content_hash"))||!"AVAILABLE".equals(bound.get(0).get("status"))
     ||!"baostock".equals(bound.get(0).get("provider"))||!"RAW".equals(bound.get(0).get("price_basis"))
     ||!ComparisonUpdatePolicy.sameWindow(bars,rawDays,market.bars(e.rawVersionId().toString()),market.calendar(e.rawVersionId().toString()),windowStart,target))
    return state(id,"READY",raw,hash,from,target,latest);
  }
  return state(id,"CURRENT",raw,hash,from,target,latest);
 }
 private Item state(String id,String state,UUID raw,String hash,LocalDate from,LocalDate target,ComparisonEvidenceRecords.Evidence e){
  return new Item(id,state,Set.of("READY","CURRENT").contains(state)?null:state,raw,hash,from,target,e==null?null:e.evidenceId(),e==null?null:e.to());
 }
 private LocalDate target(LocalDate last,Instant asOf,List<TradingDay> rawDays){
  var local=asOf.atZone(ZoneId.of("Asia/Shanghai"));
  var cutoff=local.getHour()<20?local.toLocalDate().minusDays(1):local.toLocalDate();
  // covering enforces the configured TTL; retain that single source of configuration.
  var snapshot=calendars.covering(last,cutoff).orElseThrow(()->ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE"));
  var c=snapshot.candidate();
  if(c==null||!"baostock".equals(c.provider())||c.start()==null||c.end()==null||c.fetchedAt()==null
   ||c.fetchedAt().isAfter(asOf.plusSeconds(5))||c.days()==null||c.days().isEmpty()
   ||c.start().isAfter(last)||c.start().isAfter(cutoff)||c.end().isBefore(last)||c.end().isBefore(cutoff)
   ||!CalendarService.hash(c.days()).equals(c.sourcePayloadHash()))throw ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE");
  var days=new ArrayList<TradingDay>();
  for(var d:c.days()){
   if(d==null||d.date()==null||d.open()==null)throw ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE");
   days.add(new TradingDay(d.date(),d.open()));
  }
  var independent=ComparisonUpdatePolicy.calendar(days,c.start(),c.end());
  for(var d:rawDays){
   if(independent.containsKey(d.date())&&independent.get(d.date())!=d.open())throw ComparisonEvidenceValidator.invalid("COMPARISON_CALENDAR_UNAVAILABLE");
  }
  return ComparisonUpdatePolicy.target(days,asOf,20);
 }
 public ComparisonEvidenceService.RawWindow fixedWindow(Request r){return com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->{
  if(r==null||r.operationId()==null||r.rawVersionId()==null||r.instrumentId()==null||!r.instrumentId().matches("(SH|SZ)[0-9]{6}")
   ||r.rawContentHash()==null||!r.rawContentHash().matches("[a-f0-9]{64}")||!ComparisonEvidenceValidator.DEFINITION.equals(r.definition())
   ||r.start()==null||r.end()==null||!r.start().equals(r.end().minusYears(1))||r.start().isBefore(LocalDate.of(1990,1,1))
   ||r.leaseUntil()==null)throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
  return read.execute(tx->{
   var rows=jdbc.queryForList("SELECT v.* FROM market_data_version v JOIN instrument i ON i.instrument_id=v.instrument_id WHERE v.version_id=? AND i.demo_only=false AND i.listing_status='ACTIVE'",r.rawVersionId().toString());
   if(rows.size()!=1)throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INCOMPATIBLE");
   var row=rows.get(0);
   if(!r.instrumentId().equals(row.get("instrument_id"))||!r.rawContentHash().equals(row.get("content_hash"))||!"AVAILABLE".equals(row.get("status"))
     ||!"baostock".equals(row.get("provider"))||!"RAW".equals(row.get("price_basis")))throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_INCOMPATIBLE");
   com.aistock.market.marketlifecycle.MarketEvidenceGate.raw(r.rawVersionId().toString());var all=market.bars(r.rawVersionId().toString());var rawDays=market.calendar(r.rawVersionId().toString());
   if(all.isEmpty()||r.end().isAfter(target(all.get(all.size()-1).date(),clock.instant(),rawDays)))throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
   ComparisonUpdatePolicy.calendar(rawDays,r.start(),r.end());
   var bars=all.stream().filter(b->!b.date().isBefore(r.start())&&!b.date().isAfter(r.end())).toList();
   if(bars.stream().noneMatch(b->b.date().equals(r.end())))throw ComparisonEvidenceValidator.invalid("COMPARISON_RAW_UNAVAILABLE");
   var days=rawDays.stream().filter(d->!d.date().isBefore(r.start())&&!d.date().isAfter(r.end())).toList();
   return new ComparisonEvidenceService.RawWindow(r.instrumentId(),r.rawVersionId(),r.rawContentHash(),"baostock","RAW",r.start(),r.end(),
    bars.stream().map(b->new ComparisonEvidenceService.RawPoint(b.date(),number(b.open()),number(b.high()),number(b.low()),number(b.close()),
     number(b.volumeShares()),number(b.amountYuan()),b.tradeStatus())).toList(),days);
  });
 });}
 private static String number(java.math.BigDecimal n){return n==null?null:n.stripTrailingZeros().toPlainString();}
}
