package com.aistock.market.ingestion;
import com.aistock.common.BusinessException;
import com.aistock.market.quotation.RawBar;
import com.aistock.market.support.MarketDatabaseFixture;
import com.aistock.market.version.MarketVersionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
@Tag("mysql")
class PublicationMysqlTest {
    private MarketCandidate candidate(String operation,String close,Long revision) {
        var price=new BigDecimal(close);
        return new MarketCandidate(operation,"SH600030","SH600030","SH600030",false,"UNKNOWN","baostock","RAW",
            Instant.parse("2026-09-27T08:00:00Z"),"original","a".repeat(64),
            List.of(new RawBar(LocalDate.of(2026,9,24),price,price,price,price,new BigDecimal("1000"),new BigDecimal("11000"),1,null)),List.of(),revision);
    }
    private PublicationService service(MarketDatabaseFixture fixture) {
        return new PublicationService(new MarketVersionRepository(fixture.jdbc()),new CandidateValidator());
    }
    @Test void retriesReuseResultAndIdenticalContentDoesNotDuplicateVersion() {
        var f=new MarketDatabaseFixture();var s=service(f);
        var first=s.publish(candidate("operation-1","10",0L));
        assertEquals(first.versionId(),s.publish(candidate("operation-1","10",0L)).versionId());
        var same=s.publish(candidate("operation-2","10",1L));
        assertEquals(first.versionId(),same.versionId());
        assertEquals(1,f.jdbc().queryForObject("select count(*) from market_data_version where status='AVAILABLE'",Integer.class));
        assertEquals(1,f.jdbc().queryForObject("select count(*) from market_daily_bar",Integer.class));
        var conflict=assertThrows(BusinessException.class,()->s.publish(candidate("operation-1","11",0L)));
        assertEquals("MARKET_OPERATION_CONFLICT",conflict.code());
    }
    @Test void rejectionPreservesCurrentHeadAndCannotChangeOldPrices() {
        var f=new MarketDatabaseFixture();var s=service(f);var old=s.publish(candidate("old","10",0L));
        assertThrows(BusinessException.class,()->s.publish(candidate("invalid","-1",1L)));
        assertEquals(old.versionId(),f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
        assertEquals(1,f.jdbc().queryForObject("select count(*) from market_ingestion_operation where state='REJECTED'",Integer.class));
        var next=s.publish(candidate("new","11",1L));
        assertNotEquals(old.versionId(),next.versionId());
        assertEquals(0,f.jdbc().queryForObject("select close_price from market_daily_bar where version_id=?",BigDecimal.class,old.versionId()).compareTo(new BigDecimal("10")));
    }
    @Test void concurrentExpectedRevisionOnlyLetsOneDifferentCandidatePublish() throws Exception {
        var f=new MarketDatabaseFixture();var s=service(f);s.publish(candidate("initial","10",0L));
        var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await();try{s.publish(candidate("a","11",1L));return "OK";}catch(BusinessException e){return e.code();}});
            var b=pool.submit(()->{barrier.await();try{s.publish(candidate("b","12",1L));return "OK";}catch(BusinessException e){return e.code();}});
            var outcomes=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(1,outcomes.stream().filter("OK"::equals).count());
            assertEquals(1,outcomes.stream().filter("MARKET_REVISION_CONFLICT"::equals).count());
        } finally {pool.shutdownNow();}
    }
    @Test void malformedLongMetadataStillReturnsControlledRejection() {
        var f=new MarketDatabaseFixture();var s=service(f);s.publish(candidate("initial","10",0L));
        var valid=candidate("too-long","11",1L);
        var bad=new MarketCandidate(valid.operationId(),"X".repeat(200),valid.ticker(),valid.displayName(),false,
            valid.listingStatus(),valid.provider(),valid.priceBasis(),valid.fetchedAt(),valid.sourceVersionId(),
            valid.sourcePayloadHash(),valid.bars(),valid.calendar(),valid.expectedRevision());
        var error=assertThrows(BusinessException.class,()->s.publish(bad));
        assertEquals("MARKET_DATA_INVALID",error.code());
    }
    @Test void newerValidatedMetadataMarksDelistedAndOlderCandidateCannotRevertIt() {
        var f=new MarketDatabaseFixture();var s=service(f);
        s.publish(candidate("status-initial","10",0L));
        var base=candidate("status-newer","10",1L);
        var newer=new MarketCandidate(base.operationId(),base.instrumentId(),base.ticker(),base.displayName(),
            base.displayNameVerified(),"DELISTED",base.provider(),base.priceBasis(),
            Instant.parse("2026-09-28T08:00:00Z"),base.sourceVersionId(),base.sourcePayloadHash(),
            base.bars(),base.calendar(),base.expectedRevision());
        s.publish(newer);
        assertEquals("DELISTED",f.jdbc().queryForObject("select listing_status from instrument where instrument_id='SH600030'",String.class));
        var old=candidate("status-older","10",1L);
        s.publish(old);
        assertEquals("DELISTED",f.jdbc().queryForObject("select listing_status from instrument where instrument_id='SH600030'",String.class));
    }    private MarketCandidate metadata(String op,String name,boolean verified,String status,String fetchedAt) {
        var b=candidate(op,"10",1L);
        return new MarketCandidate(op,b.instrumentId(),b.ticker(),name,verified,status,b.provider(),b.priceBasis(),
            Instant.parse(fetchedAt),b.sourceVersionId(),b.sourcePayloadHash(),b.bars(),b.calendar(),b.expectedRevision());
    }
    @Test void unverifiedInitialPricesAreNotMetadataVerification() {
        var f=new MarketDatabaseFixture();service(f).publish(candidate("metadata-unknown","10",0L));
        var row=f.jdbc().queryForMap("select metadata_source,metadata_checked_at,name_verified from instrument where instrument_id='SH600030'");
        assertNull(row.get("metadata_checked_at"));
        assertNull(row.get("metadata_source"));
        assertEquals(false,row.get("name_verified"));
    }
    @Test void laterUnverifiedPricesCannotAdvanceVerifiedMetadataTime() {
        var f=new MarketDatabaseFixture();var s=service(f);
        var original=s.publish(candidate("metadata-seed","10",0L));
        s.publish(metadata("metadata-confirmed","中信证券",true,"ACTIVE","2026-09-28T08:00:00Z"));
        var checked=f.jdbc().queryForObject("select metadata_checked_at from instrument where instrument_id='SH600030'",java.time.LocalDateTime.class);
        s.publish(metadata("metadata-price-only","SH600030",false,"UNKNOWN","2026-09-29T08:00:00Z"));
        var row=f.jdbc().queryForMap("select display_name,listing_status,metadata_checked_at from instrument where instrument_id='SH600030'");
        assertEquals("中信证券",row.get("display_name"));
        assertEquals("ACTIVE",row.get("listing_status"));
        assertEquals(checked,row.get("metadata_checked_at"));
        assertEquals(original.versionId(),f.jdbc().queryForObject("select version_id from instrument_data_head",String.class));
        assertEquals(1,f.jdbc().queryForObject("select count(*) from market_data_version",Integer.class));
    }
    @Test void firstVerifiedMetadataCanReplaceLegacyNullTimestamp() {
        var f=new MarketDatabaseFixture();var s=service(f);s.publish(candidate("metadata-legacy","10",0L));
        f.jdbc().update("update instrument set metadata_checked_at=NULL,metadata_source=NULL where instrument_id='SH600030'");
        s.publish(metadata("metadata-first","中信证券",true,"ACTIVE","2026-09-28T08:00:00Z"));
        var row=f.jdbc().queryForMap("select display_name,name_verified,listing_status,metadata_source,metadata_checked_at from instrument where instrument_id='SH600030'");
        assertEquals("中信证券",row.get("display_name"));assertEquals(true,row.get("name_verified"));
        assertEquals("ACTIVE",row.get("listing_status"));assertEquals("baostock",row.get("metadata_source"));
        assertNotNull(row.get("metadata_checked_at"));
    }}
