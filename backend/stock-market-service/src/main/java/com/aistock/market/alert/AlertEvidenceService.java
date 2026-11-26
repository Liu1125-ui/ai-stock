package com.aistock.market.alert;
import static com.aistock.market.alert.AlertEvidenceRecords.*;
import com.aistock.common.BusinessException;
import com.aistock.market.calendar.*;import com.aistock.market.quotation.*;import com.aistock.market.version.MarketVersionRepository;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;import java.util.*;import java.security.MessageDigest;
import org.springframework.stereotype.Service;import org.springframework.beans.factory.annotation.Value;import org.springframework.http.HttpStatus;
@Service public class AlertEvidenceService {
 private final AlertLocalUseOwner uses;private final MarketVersionRepository market;private final CalendarService calendars;private final AlertEvidenceCollector collector;private final Clock clock;private final int hour;
 private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 private final AlertEvidenceStore store;private final AlertEvidenceValidator validator;
 public AlertEvidenceService(MarketVersionRepository market,CalendarService calendars,AlertEvidenceCollector collector,Clock clock,@Value("${aistock.market.update-hour:20}")int hour){this(market,calendars,collector,clock,hour,null);}
 @org.springframework.beans.factory.annotation.Autowired public AlertEvidenceService(MarketVersionRepository market,CalendarService calendars,AlertEvidenceCollector collector,Clock clock,@Value("${aistock.market.update-hour:20}")int hour,AlertLocalUseOwner uses){this.uses=uses;if(hour<0||hour>23)throw new IllegalArgumentException("Invalid cutoff");this.market=market;this.calendars=calendars;this.collector=collector;this.clock=clock;this.hour=hour;this.store=new AlertEvidenceStore(market.jdbc(),clock,mapper);this.validator=new AlertEvidenceValidator(clock);}
 public Receipt prepare(Request request){
  validateRequest(request);var replay=com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->store.begin(request,hash(request)));if(replay!=null)return replay;
  try(var scope=uses==null?com.aistock.market.marketlifecycle.MarketEvidenceGate.passthrough():uses.acquire(request)){
   if(!clock.instant().isBefore(request.leaseUntil()))return store.operation(request.operationId());
   var prepared=com.aistock.market.marketlifecycle.MarketEvidenceGate.local(()->prepareLocal(request));
   if(prepared.receipt()!=null)return prepared.receipt();
   var bars=prepared.bars();var days=prepared.days();var calendarId=prepared.calendarId();
   var expected=prepared.expected();var previous=prepared.previous();var current=prepared.current();
   String priceState=current==null?"MISSING":current.tradeStatus()==0?"SUSPENDED":"VERIFIED";
   String changeState;String previousValue=null,currentValue=null,sourceHash=null;
   if(current==null)changeState="MISSING_CURRENT_RAW";
   else if(current.tradeStatus()!=1)changeState="SUSPENDED";
   else if(previous==null)changeState="MISSING_PREVIOUS_CALENDAR";
   else{
    var prior=bars.stream().filter(b->b.date().equals(previous)).findFirst().orElse(null);
    if(prior==null)changeState="MISSING_PREVIOUS_RAW";
    else if(prior.tradeStatus()!=1)changeState="SUSPENDED";
    else{
     com.aistock.market.marketlifecycle.MarketEvidenceGate.checkpoint();
     var live=store.operation(request.operationId());
     if(!"RUNNING".equals(live.state()))return live;
     Source source;try{source=collector.collect(request,previous);}catch(Exception error){source=null;}
     if(source==null||!"AVAILABLE".equals(source.state()))changeState=source!=null&&"SOURCE_INVALID".equals(source.state())?"SOURCE_INVALID":"SOURCE_UNAVAILABLE";
     else try{var pair=validator.pair(request.operationId().toString(),request.instrumentId(),request.rawVersionId(),previous,expected,bars,days,source.candidate());previousValue=pair.previousBackwardClose();currentValue=pair.currentBackwardClose();sourceHash=source.candidate().sourcePayloadHash();changeState="VERIFIED";}catch(BusinessException invalid){changeState="SOURCE_INVALID";}
    }
   }
   var calendarHash=CalendarService.hash(days.stream().map(d->new CalendarRecords.Day(d.date(),d.open())).toList());
   var id=UUID.randomUUID();var rawClose="VERIFIED".equals(priceState)?AlertEvidenceValidator.decimal(current.close()):null;
   var e=new Evidence(id,request.instrumentId(),"baostock",DEFINITION,expected,previous,request.rawVersionId(),prepared.rawHash(),calendarId,calendarHash,days,priceState,rawClose,changeState,previousValue,currentValue,sourceHash,clock.instant(),null);
   ObjectNode tree=mapper.valueToTree(e);tree.remove(List.of("id","contentHash"));var contentHash=hash(tree);
   return store.publish(request,new Evidence(id,e.instrumentId(),e.provider(),e.definition(),e.tradeDate(),e.previousTradeDate(),e.rawVersionId(),e.rawContentHash(),e.calendarVersionId(),e.calendarHash(),e.calendarDays(),e.priceState(),e.rawClose(),e.changeState(),e.previousBackwardClose(),e.currentBackwardClose(),e.sourcePayloadHash(),e.capturedAt(),contentHash));
  }catch(BusinessException error){return store.finish(request.operationId(),"FAILED",error.code());}catch(Exception error){return store.finish(request.operationId(),"FAILED","ALERT_EVIDENCE_UNAVAILABLE");}
  finally{if(uses!=null)uses.notifyCompleted(request.operationId());}
 }
 private record Prepared(Receipt receipt,String rawHash,List<RawBar> bars,List<TradingDay> days,UUID calendarId,LocalDate expected,LocalDate previous,RawBar current) {}
 private Prepared failed(Receipt receipt){return new Prepared(receipt,null,null,null,null,null,null,null);}
 private Prepared prepareLocal(Request request){
   com.aistock.market.marketlifecycle.MarketEvidenceGate.raw(request.rawVersionId().toString());var pinned=com.aistock.market.marketlifecycle.MarketEvidenceGate.pinned(request.instrumentId());var heads=pinned==null?market.jdbc().queryForList("select v.content_hash,v.version_id from instrument_data_head h join market_data_version v on h.version_id=v.version_id join instrument i on i.instrument_id=h.instrument_id where h.instrument_id=? and h.provider='baostock' and h.price_basis='RAW' and v.instrument_id=h.instrument_id and v.provider=h.provider and v.price_basis=h.price_basis and v.status='AVAILABLE' and i.listing_status='ACTIVE' and i.demo_only=false",request.instrumentId()):market.jdbc().queryForList("select v.content_hash,v.version_id from market_data_version v join instrument i on i.instrument_id=v.instrument_id where v.version_id=? and v.instrument_id=? and v.provider='baostock' and v.price_basis='RAW' and v.status='AVAILABLE' and i.listing_status='ACTIVE' and i.demo_only=false",pinned,request.instrumentId());
   if(heads.size()!=1||!request.rawVersionId().toString().equals(heads.get(0).get("version_id")))return failed(store.finish(request.operationId(),"FAILED","ALERT_RAW_CHANGED_OR_UNAVAILABLE"));
   var bars=market.bars(request.rawVersionId().toString());if(bars.isEmpty())return failed(store.finish(request.operationId(),"FAILED","ALERT_RAW_UNAVAILABLE"));
   var today=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();var last=bars.get(bars.size()-1).date();if(last.isAfter(today))return failed(store.finish(request.operationId(),"FAILED","ALERT_RAW_INVALID"));
   var cutoff=cutoff();var independent=calendars.covering(last,cutoff);
   UUID calendarId=independent.map(CalendarRecords.Snapshot::versionId).orElse(null);
   var days=independent.map(s->s.candidate().days().stream().map(d->new TradingDay(d.date(),d.open())).toList()).orElseGet(()->market.calendar(request.rawVersionId().toString()));
   var indexed=completeCalendar(days,cutoff);var expected=indexed.entrySet().stream().filter(e->e.getValue()&&!e.getKey().isAfter(cutoff)).map(Map.Entry::getKey).reduce((a,b)->b).orElseThrow(()->unavailable("ALERT_CALENDAR_UNAVAILABLE"));
   if(!request.tradeDate().equals(expected))return failed(store.finish(request.operationId(),"SKIPPED_STALE","ALERT_DATE_NOT_LATEST"));
   if(independent.isPresent()&&indexed.headMap(expected,false).entrySet().stream().noneMatch(Map.Entry::getValue)){
    try{
     var rawDays=market.calendar(request.rawVersionId().toString());var rawIndexed=completeCalendar(rawDays,cutoff);
     var rawExpected=rawIndexed.entrySet().stream().filter(e->e.getValue()&&!e.getKey().isAfter(cutoff)).map(Map.Entry::getKey).reduce((a,b)->b).orElse(null);
     var independentIndexed=indexed;boolean consistent=rawIndexed.entrySet().stream().filter(e->independentIndexed.containsKey(e.getKey())&&!e.getKey().isAfter(cutoff)).allMatch(e->Objects.equals(e.getValue(),independentIndexed.get(e.getKey())));
     if(expected.equals(rawExpected)&&consistent&&rawIndexed.headMap(expected,false).entrySet().stream().anyMatch(Map.Entry::getValue)){days=rawDays;indexed=rawIndexed;calendarId=null;}
    }catch(BusinessException unavailableRawCalendar){/* Insufficient RAW calendar cannot invent a previous session. */}
   }
   var previous=indexed.headMap(expected,false).entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).reduce((a,b)->b).orElse(null);
   var current=bars.stream().filter(b->b.date().equals(expected)).findFirst().orElse(null);
   return new Prepared(null,(String)heads.get(0).get("content_hash"),List.copyOf(bars),List.copyOf(days),calendarId,expected,previous,current);
 }
 public Receipt operation(UUID id){return store.operation(id);}
 public Evidence version(UUID id){return store.version(id);}
 private LocalDate cutoff(){var time=clock.instant().atZone(ZoneId.of("Asia/Shanghai"));return time.toLocalDate().minusDays(time.getHour()<hour?1:0);}
 private void validateRequest(Request r){if(r==null||r.operationId()==null||r.instrumentId()==null||!r.instrumentId().matches("(SH|SZ)[0-9]{6}")||r.rawVersionId()==null||r.tradeDate()==null||r.tradeDate().isBefore(LocalDate.of(1990,1,1))||r.tradeDate().isAfter(cutoff())||r.leaseUntil()==null||r.leaseUntil().isAfter(clock.instant().plusSeconds(120)))throw unavailable("ALERT_EVIDENCE_REQUEST_INVALID");}
 private TreeMap<LocalDate,Boolean> completeCalendar(List<TradingDay> days,LocalDate cutoff){var result=new TreeMap<LocalDate,Boolean>();for(var d:days){if(d==null||d.date()==null||result.put(d.date(),d.open())!=null)throw unavailable("ALERT_CALENDAR_UNAVAILABLE");}if(result.isEmpty()||result.firstKey().isAfter(cutoff)||result.lastKey().isBefore(cutoff))throw unavailable("ALERT_CALENDAR_UNAVAILABLE");for(var date=result.firstKey();!date.isAfter(cutoff);date=date.plusDays(1))if(!result.containsKey(date))throw unavailable("ALERT_CALENDAR_UNAVAILABLE");return result;}
 private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}catch(Exception error){throw unavailable("ALERT_EVIDENCE_REQUEST_INVALID");}}
 private BusinessException unavailable(String code){return AlertEvidenceStore.error(code,HttpStatus.UNPROCESSABLE_ENTITY);}
}
