package com.aistock.market.realization;
import com.aistock.common.BusinessException;import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import java.util.*;import java.security.MessageDigest;
import static com.aistock.market.realization.RealizationRecords.*;
public final class RealizationService {
 private final RealizationRepository repo;private final RealizationCollector collector;private final RealizationValidator validator;private final ObjectMapper mapper;private final Clock clock;
 public RealizationService(RealizationRepository repo,RealizationCollector collector,RealizationValidator validator,ObjectMapper mapper,Clock clock){this.repo=repo;this.collector=collector;this.validator=validator;this.mapper=mapper;this.clock=clock;}
 public Receipt prepare(Request r){validator.request(r);var replay=repo.begin(r,hash(r));if(replay!=null)return replay;try{
  if(!clock.instant().isBefore(r.leaseUntil()))throw RealizationValidator.error("ATTEMPT_EXPIRED");
  if(r.targetDates().get(0).isAfter(r.dueThrough()))return repo.finish(r.operationId(),"SKIPPED_NOT_DUE",null);
  var requiredThrough=r.targetDates().stream()
      .filter(day->!day.isAfter(r.dueThrough())).max(java.time.LocalDate::compareTo).orElseThrow();
  var head=repo.head(r.instrumentId(),requiredThrough);com.aistock.market.marketlifecycle.MarketEvidenceGate.checkpoint();
  var live=repo.operation(r.operationId());
  if(!"RUNNING".equals(live.state()))return live;
  var source=collector.collect(r,head);
  if(source==null||!"AVAILABLE".equals(source.state()))throw RealizationValidator.error(source!=null&&Set.of("SOURCE_TIMEOUT","SOURCE_BUSY","SOURCE_UNAVAILABLE","SOURCE_INVALID").contains(source.state())?source.state():"REALIZATION_SOURCE_INVALID");
  var e=validator.evidence(r,head,source.candidate());String calendarHash=hash(source.candidate().calendar());
  String contentHash=hash(List.of(e.instrumentId(),e.definition(),e.originDate(),e.targetDates(),e.dueThrough(),e.rawVersionId(),e.rawContentHash(),e.sourcePayloadHash(),calendarHash,e.originRawClose(),e.originBackwardClose(),e.observations()));
  var completed=new Evidence(null,e.instrumentId(),e.provider(),e.definition(),e.originDate(),e.targetDates(),e.dueThrough(),e.rawVersionId(),e.rawContentHash(),e.sourcePayloadHash(),calendarHash,contentHash,e.capturedAt(),e.originRawClose(),e.originBackwardClose(),e.observations(),e.calendar());return repo.publish(r,completed);
 }catch(BusinessException e){return repo.finish(r.operationId(),"FAILED",e.code());}catch(Exception e){return repo.finish(r.operationId(),"FAILED","REALIZATION_SOURCE_INVALID");}}
 public Receipt operation(UUID id){return repo.operation(id);}public Evidence version(String id){try{return repo.version(UUID.fromString(id));}catch(IllegalArgumentException e){throw RealizationValidator.error("REALIZATION_REQUEST_INVALID");}}
 private String hash(Object obj){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(obj)));}catch(Exception e){throw RealizationValidator.error("REALIZATION_SOURCE_INVALID");}}
}
