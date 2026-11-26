package com.aistock.prediction.verification;
import com.fasterxml.jackson.databind.*;import java.time.*;import java.math.BigDecimal;import java.util.*;import java.security.MessageDigest;
import static com.aistock.prediction.verification.VerificationRecords.*;
final class VerificationEvidence {
 static String hash(ObjectMapper mapper,Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}catch(Exception e){throw VerificationValidator.invalid();}}
 static Evidence read(ObjectMapper mapper,JsonNode body,VerificationRequest request,Clock clock){try{
  for(var key:List.of("realizationVersionId","rawVersionId"))if(!body.path(key).isTextual()||!body.path(key).textValue().matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw VerificationValidator.invalid();
  for(var key:List.of("rawContentHash","sourcePayloadHash","calendarHash","contentHash"))if(!body.path(key).isTextual()||!body.path(key).textValue().matches("[a-f0-9]{64}"))throw VerificationValidator.invalid();
  if(!request.dueThrough().toString().equals(body.path("dueThrough").asText())||!body.path("capturedAt").isTextual()||Instant.parse(body.path("capturedAt").asText()).isAfter(clock.instant().plusSeconds(300)))throw VerificationValidator.invalid();
  var calendar=body.path("calendar");if(!calendar.isArray()||calendar.isEmpty()||calendar.size()>96||!hash(mapper,calendar).equals(body.path("calendarHash").asText()))throw VerificationValidator.invalid();
  var next=LocalDate.parse(body.path("originDate").asText());for(var d:calendar){if(!d.path("date").isTextual()||!d.path("open").isBoolean()||!next.toString().equals(d.path("date").asText()))throw VerificationValidator.invalid();next=next.plusDays(1);}if(!next.isAfter(request.dueThrough()))throw VerificationValidator.invalid();
  var origin=LocalDate.parse(body.path("originDate").asText());var targets=new ArrayList<LocalDate>();for(var d:body.path("targetDates"))targets.add(LocalDate.parse(d.textValue()));
  String expected=hash(mapper,List.of(body.path("instrumentId").asText(),body.path("definition").asText(),origin,targets,request.dueThrough(),body.path("rawVersionId").asText(),body.path("rawContentHash").asText(),body.path("sourcePayloadHash").asText(),body.path("calendarHash").asText(),body.path("originRawClose").asText(),body.path("originBackwardClose").asText(),body.path("observations")));
  if(!expected.equals(body.path("contentHash").asText()))throw VerificationValidator.invalid();
  var days=new ArrayList<ObservedDay>();for(var d:body.path("observations")){var state=PointState.valueOf(d.path("state").asText());if(!d.path("rawClose").isNull()&&!d.path("rawClose").isTextual()||!d.path("backwardClose").isNull()&&!d.path("backwardClose").isTextual())throw VerificationValidator.invalid();days.add(new ObservedDay(LocalDate.parse(d.path("date").asText()),state,d.path("rawClose").isNull()?null:new BigDecimal(d.path("rawClose").asText()),d.path("backwardClose").isNull()?null:new BigDecimal(d.path("backwardClose").asText())));}
  if(!body.path("originRawClose").isTextual()||!body.path("originBackwardClose").isTextual())throw VerificationValidator.invalid();
  return new Evidence(body.path("realizationVersionId").asText(),body.path("instrumentId").asText(),body.path("provider").asText(),body.path("definition").asText(),origin,targets,request.dueThrough(),new BigDecimal(body.path("originRawClose").asText()),new BigDecimal(body.path("originBackwardClose").asText()),days);
 }catch(com.aistock.common.BusinessException e){throw e;}catch(Exception e){throw VerificationValidator.invalid();}}
}
