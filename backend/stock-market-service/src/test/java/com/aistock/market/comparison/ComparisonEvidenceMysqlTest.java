package com.aistock.market.comparison;

import com.aistock.common.BusinessException;
import com.aistock.market.ingestion.*;
import com.aistock.market.quotation.*;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;

@Tag("mysql")
class ComparisonEvidenceMysqlTest {
    private static final Clock NOW=Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"),ZoneOffset.UTC);
    private static final LocalDate DAY=LocalDate.of(2026,10,8);
    private BigDecimal n(String s){return new BigDecimal(s);}
    private List<RawBar> bars(String price) {
        return List.of(new RawBar(DAY,n(price),n(price),n(price),n(price),n("100"),n(price).multiply(n("100")),1,null),
            new RawBar(DAY.plusDays(1),n(price),n(price),n(price),n(price),n("100"),n(price).multiply(n("100")),1,null));
    }
    private List<TradingDay> calendar(){return List.of(new TradingDay(DAY,true),new TradingDay(DAY.plusDays(1),true));}
    private UUID raw(MarketDatabaseFixture f,String op,String price,long revision){
        return UUID.fromString(new PublicationService(new MarketVersionRepository(f.jdbc()),new CandidateValidator()).publish(
            new MarketCandidate(op,"SH600030","SH600030","中信证券",true,"ACTIVE","baostock","RAW",
                NOW.instant(),null,"a".repeat(64),bars(price),calendar(),revision)).versionId());
    }
    private ComparisonEvidenceService service(MarketDatabaseFixture f){
        return new ComparisonEvidenceService(new MarketVersionRepository(f.jdbc()),NOW);
    }
    private Candidate candidate(String op,UUID id,String adjusted){
        return new Candidate(op,"SH600030",id,DAY,DAY.plusDays(1),"baostock",
            ComparisonEvidenceValidator.DEFINITION,NOW.instant(),"b".repeat(64),
            bars("10").stream().map(b->new Point(b.date(),b.close(),n(adjusted),b.volumeShares(),b.tradeStatus())).toList(),calendar());
    }
    @Test void rawWindowPinsHeadAndRequiresCompleteCalendar() {
        var f=new MarketDatabaseFixture();var id=raw(f,"raw","10",0);var s=service(f);
        var window=s.rawWindow("SH600030",DAY,DAY.plusDays(1));
        assertEquals(id,window.rawVersionId());assertEquals(2,window.bars().size());
        assertEquals(2,window.calendar().size());
        assertTrue(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().valueToTree(window).get("bars").get(0).get("close").isTextual());
        assertThrows(BusinessException.class,()->s.rawWindow("SH600030",DAY.minusDays(1),DAY));
        assertThrows(BusinessException.class,()->s.rawWindow("SH600030",DAY,DAY.plusYears(2)));
    }
    @Test void immutableReplayAndConflict() {
        var f=new MarketDatabaseFixture();var id=raw(f,"raw","10",0);var s=service(f);
        var first=s.publish(candidate("same",id,"20"));
        assertEquals(first.evidenceId(),s.publish(candidate("same",id,"20")).evidenceId());
        assertTrue(s.publish(candidate("same",id,"20")).replayed());
        var failure=assertThrows(BusinessException.class,()->s.publish(candidate("same",id,"21")));
        assertEquals("COMPARISON_OPERATION_CONFLICT",failure.code());
        assertEquals("20",s.version(first.evidenceId()).days().get(0).backwardClose());
        assertEquals(1,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
        assertEquals(id.toString(),f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
        assertEquals(0,f.jdbc().queryForObject("select count(*) from forecast_input_version",Integer.class));
    }
    @Test void concurrentSameOperationOnlyPublishesOnce() throws Exception {
        var f=new MarketDatabaseFixture();var id=raw(f,"raw","10",0);var s=service(f);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->s.publish(candidate("concurrent",id,"20")));
            var b=pool.submit(()->s.publish(candidate("concurrent",id,"20")));
            assertEquals(a.get(10,TimeUnit.SECONDS).evidenceId(),b.get(10,TimeUnit.SECONDS).evidenceId());
            assertEquals(1,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test void fixedOldVersionSurvivesHeadMovement() {
        var f=new MarketDatabaseFixture();var old=raw(f,"raw","10",0);var s=service(f);
        var next=raw(f,"new","11",1);
        var result=s.publish(candidate("old",old,"20"));
        assertEquals(old,s.version(result.evidenceId()).rawVersionId());
        assertEquals(next.toString(),f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
    }
    @Test void badCandidateRollsBackOperationAndUnknownIs404() {
        var f=new MarketDatabaseFixture();var id=raw(f,"raw","11",0);var s=service(f);
        assertThrows(BusinessException.class,()->s.publish(candidate("bad",id,"20")));
        assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_operation",Integer.class));
        assertEquals(0,f.jdbc().queryForObject("select count(*) from comparison_evidence_version",Integer.class));
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(BusinessException.class,()->s.version(UUID.randomUUID())).code());
    }
    @Test void equalContentDifferentOperationsDeduplicatesEvidence() {
        var f=new MarketDatabaseFixture();var id=raw(f,"raw","10",0);var s=service(f);
        var a=s.publish(candidate("a",id,"20"));var b=s.publish(candidate("b",id,"20"));
        assertEquals(a.evidenceId(),b.evidenceId());
        assertEquals(2,f.jdbc().queryForObject("select count(*) from comparison_evidence_operation",Integer.class));
    }
}
