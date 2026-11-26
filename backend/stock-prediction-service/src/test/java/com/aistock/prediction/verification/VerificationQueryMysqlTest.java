package com.aistock.prediction.verification;
import java.time.*;import java.util.*;import com.aistock.common.BusinessException;
import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql") class VerificationQueryMysqlTest {
 final VerificationMysqlTest data=new VerificationMysqlTest();
 VerificationQueryRepository query(){return new VerificationQueryRepository(data.f.jdbc,VerificationMysqlTest.MAPPER,VerificationMysqlTest.NOW,20);}
 UUID owner(UUID id){return UUID.fromString(data.f.jdbc.queryForObject("select user_id from forecast_request where prediction_id=?",String.class,id.toString()));}
 @Test void hiddenOwnedRecordAndActualCurrentPointerAreReadable(){var id=data.forecast("SUCCEEDED",true);var s=data.service((r,p)->data.evidence(r.dueThrough(),UUID.randomUUID().toString(),"102"));var current=s.verify(id,data.request(VerificationMysqlTest.TARGETS.get(1)));s.verify(id,data.request(VerificationMysqlTest.TARGETS.get(0)));var q=query();var page=q.list(owner(id),id,1);assertEquals(2,page.path("total").asInt());assertEquals(current.versionId(),page.path("currentVersionId").asText());assertEquals(2,page.path("current").path("result").path("verifiedCount").asInt());assertEquals("102.000000",q.detail(owner(id),id,UUID.fromString(current.versionId())).path("result").path("points").get(0).path("actualPrice").asText());assertThrows(BusinessException.class,()->q.list(UUID.randomUUID(),id,1));}
 @Test void absentEvidenceShowsPendingOrWaitingNeverInventsActual(){var id=data.forecast("SUCCEEDED",false);var result=query().list(owner(id),id,1);assertTrue(result.path("current").isNull());assertEquals("PENDING",result.path("expectedPoints").get(0).path("state").asText());assertEquals("WAITING",result.path("expectedPoints").get(2).path("state").asText());assertFalse(result.path("expectedPoints").get(0).has("actualPrice"));assertEquals(0,data.f.jdbc.queryForObject("select count(*) from forecast_verification_version",Integer.class));}
 @Test void wrongVersionCannotLeakAndFailedUpdateRetainsResultAndSnapshot(){var id=data.forecast("SUCCEEDED",false);String snapshot=data.f.jdbc.queryForObject("select snapshot_json from forecast_request where prediction_id=?",String.class,id.toString());var s=data.service((r,p)->data.evidence(r.dueThrough(),UUID.randomUUID().toString(),"102"));var good=s.verify(id,data.request(VerificationMysqlTest.TARGETS.get(0)));data.service((r,p)->{throw new IllegalStateException("offline");}).verify(id,data.request(VerificationMysqlTest.TARGETS.get(1)));var page=query().list(owner(id),id,1);assertEquals("FAILED",page.path("latestAttempt").path("state").asText());assertEquals(good.versionId(),page.path("currentVersionId").asText());var other=data.forecast("SUCCEEDED",false);assertThrows(BusinessException.class,()->query().detail(owner(other),other,UUID.fromString(good.versionId())));assertEquals(snapshot,data.f.jdbc.queryForObject("select snapshot_json from forecast_request where prediction_id=?",String.class,id.toString()));}
 @Test void paginationAndExpiredAttemptRemainReadonly(){var id=data.forecast("SUCCEEDED",false);var who=owner(id);var repo=new VerificationRepository(data.f.jdbc,VerificationMysqlTest.MAPPER,VerificationMysqlTest.NOW);var r=data.request(VerificationMysqlTest.TARGETS.get(0));repo.begin(id,r,"e".repeat(64));var later=new VerificationQueryRepository(data.f.jdbc,VerificationMysqlTest.MAPPER,Clock.fixed(VerificationMysqlTest.NOW.instant().plusSeconds(121),ZoneOffset.UTC),20);assertTrue(later.list(who,id,1).path("latestAttempt").path("expired").asBoolean());assertEquals("RUNNING",data.f.jdbc.queryForObject("select state from forecast_verification_operation where operation_id=?",String.class,r.operationId().toString()));assertThrows(BusinessException.class,()->query().list(who,id,0));assertThrows(BusinessException.class,()->query().list(who,id,10001));assertTrue(query().list(who,id,2).path("items").isEmpty());}

 @Test void fusionReadsOnlyTheSameCurrentEvidenceForEveryOwnedSource(){
  var first=data.forecast("SUCCEEDED",false);
  var second=data.forecast("SUCCEEDED",false);
  var who=owner(first);
  data.f.jdbc.update("update forecast_request set user_id=? where prediction_id=?",who.toString(),second.toString());
  var ids=List.of(first,second);
  var source=UUID.randomUUID().toString();
  var due=VerificationMysqlTest.TARGETS.get(0);
  var service=data.service((request,snapshot)->data.evidence(request.dueThrough(),source,"102"));
  service.verify(first,data.request(due));
  assertTrue(query().sharedFusionEvidence(who,ids).isEmpty());
  service.verify(second,data.request(due));
  var evidence=query().sharedFusionEvidence(who,ids).orElseThrow();
  assertEquals(source,evidence.versionId());
  assertEquals(1,evidence.observations().size());
  assertTrue(query().sharedFusionEvidence(UUID.randomUUID(),ids).isEmpty());
  data.service((request,snapshot)->data.evidence(request.dueThrough(),UUID.randomUUID().toString(),"104"))
      .verify(second,data.request(VerificationMysqlTest.TARGETS.get(1)));
  assertTrue(query().sharedFusionEvidence(who,ids).isEmpty());
 }
}
