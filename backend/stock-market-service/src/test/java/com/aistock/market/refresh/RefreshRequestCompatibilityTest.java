package com.aistock.market.refresh;
import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Constraint;
import java.sql.Timestamp;import java.time.*;import java.util.List;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
class RefreshRequestCompatibilityTest {
 static final String HASH="f03d846198521487ee0ea93e7f99f9d3882ea20c7bb3545d0d35fc74994fc5ee";
 RefreshRequest request(){return new RefreshRequest("legacy-fixed","SH600030",LocalDate.parse("2026-10-24"),Instant.parse("2026-10-25T12:01:30Z"));}
 @Test void retainsLiteralOldFourFieldRequestHash(){assertEquals(HASH,RefreshRequestHasher.hash(request()));}
 @Test void constraintHashIncludesEveryFrozenFieldAndOriginalField(){
  var r=request();String id="00000000-0000-0000-0000-000000000001";var c=new Constraint(id,0,LocalDate.parse("2026-10-23"),"admin-market-refresh-v1");
  var constrained=new RefreshRequest(r.operationId(),r.instrumentId(),r.targetDate(),r.leaseUntil(),c);String hash=RefreshRequestHasher.hash(constrained);assertNotEquals(HASH,hash);
  for(var other:List.of(new RefreshRequest(r.operationId(),r.instrumentId(),r.targetDate().plusDays(1),r.leaseUntil(),c),new RefreshRequest(r.operationId(),r.instrumentId(),r.targetDate(),r.leaseUntil().plusSeconds(1),c),new RefreshRequest(r.operationId(),r.instrumentId(),r.targetDate(),r.leaseUntil(),new Constraint(id,1,c.coverageStart(),c.policyVersion())),new RefreshRequest(r.operationId(),r.instrumentId(),r.targetDate(),r.leaseUntil(),new Constraint(id,0,c.coverageStart().minusDays(1),c.policyVersion())),new RefreshRequest(r.operationId(),r.instrumentId(),r.targetDate(),r.leaseUntil(),new Constraint("00000000-0000-0000-0000-000000000002",0,c.coverageStart(),c.policyVersion()))))assertNotEquals(hash,RefreshRequestHasher.hash(other));
 }
 @Test @Tag("mysql") void upgradedRepositoryReplaysStoredLegacyReceipt(){
  var f=new AdminRefreshFixture();var r=request();f.jdbc.update("insert into market_refresh_receipt(operation_id,request_hash,instrument_id,target_date,lease_until,state,created_at,error_code,finished_at) values(?,?,?,?,?,'FAILED',?,'SOURCE_TIMEOUT',?)",r.operationId(),HASH,r.instrumentId(),r.targetDate(),Timestamp.from(r.leaseUntil()),Timestamp.from(r.leaseUntil().minusSeconds(90)),Timestamp.from(r.leaseUntil()));
  var result=new RefreshRepository(f.jdbc).begin(r,Clock.fixed(Instant.parse("2026-10-25T12:02:00Z"),ZoneOffset.UTC));assertEquals("FAILED",result.state());assertEquals("SOURCE_TIMEOUT",result.errorCode());
 }
}