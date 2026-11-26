package com.aistock.market.ingestion;

import com.aistock.common.BusinessException;
import com.aistock.market.quotation.RawBar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CandidateValidatorTest {
    private final CandidateValidator validator=new CandidateValidator();
    private RawBar bar(String low,String amount) {
        return new RawBar(LocalDate.of(2026,9,24),new BigDecimal("10"),new BigDecimal("12"),
            new BigDecimal(low),new BigDecimal("11"),new BigDecimal("1000"),new BigDecimal(amount),1,null);
    }
    private MarketCandidate candidate(List<RawBar> rows) {
        return new MarketCandidate("import-1","SH600030","SH600030","SH600030",false,"UNKNOWN",
            "baostock","RAW",Instant.parse("2026-09-27T08:00:00Z"),"source-1","a".repeat(64),rows,List.of(),0L);
    }
    @Test void validRawCandidateNeedsNoUnverifiedAdjustmentRatio() {
        assertDoesNotThrow(()->validator.validate(candidate(List.of(bar("9","11000")))));
    }
    @Test void invalidOhlcCannotBePublished() {
        var error=assertThrows(BusinessException.class,()->validator.validate(candidate(List.of(bar("11.5","11000")))));
        assertEquals("MARKET_DATA_INVALID",error.code());
    }
    @Test void duplicateDatesAndNegativeTurnoverAreRejected() {
        assertThrows(BusinessException.class,()->validator.validate(candidate(List.of(bar("9","11000"),bar("9","11000")))));
        assertThrows(BusinessException.class,()->validator.validate(candidate(List.of(bar("9","-1")))));
    }
    @Test void pricePrecisionCannotBeSilentlyRounded() {
        assertThrows(BusinessException.class,()->validator.validate(candidate(List.of(bar("9.0000001","11000")))));
        assertThrows(BusinessException.class,()->validator.validate(candidate(List.of())));
    }
    @Test void barsAndCalendarCannotClaimDatesOutsideHistoricalFetchWindow() {
        var base=candidate(List.of(bar("9","11000")));
        var futureBar=new RawBar(LocalDate.of(2030,1,2),new BigDecimal("10"),new BigDecimal("12"),
            new BigDecimal("9"),new BigDecimal("11"),new BigDecimal("1000"),new BigDecimal("11000"),1,null);
        var ancientBar=new RawBar(LocalDate.of(1989,1,2),new BigDecimal("10"),new BigDecimal("12"),
            new BigDecimal("9"),new BigDecimal("11"),new BigDecimal("1000"),new BigDecimal("11000"),1,null);
        assertThrows(BusinessException.class,()->validator.validate(withDates(base,List.of(futureBar),List.of())));
        assertThrows(BusinessException.class,()->validator.validate(withDates(base,List.of(ancientBar),List.of())));
        assertThrows(BusinessException.class,()->validator.validate(withDates(base,base.bars(),
            List.of(new com.aistock.market.quotation.TradingDay(LocalDate.of(2030,1,2),true)))));
    }
    @Test void publishedBarCannotFallOnExplicitlyClosedSourceCalendarDay() {
        var base=candidate(List.of(bar("9","11000")));
        assertThrows(BusinessException.class,()->validator.validate(withDates(base,base.bars(),
            List.of(new com.aistock.market.quotation.TradingDay(LocalDate.of(2026,9,24),false)))));
    }
    private MarketCandidate withDates(MarketCandidate c,List<RawBar> bars,
            List<com.aistock.market.quotation.TradingDay> calendar) {
        return new MarketCandidate(c.operationId(),c.instrumentId(),c.ticker(),c.displayName(),
            c.displayNameVerified(),c.listingStatus(),c.provider(),c.priceBasis(),c.fetchedAt(),
            c.sourceVersionId(),c.sourcePayloadHash(),bars,calendar,c.expectedRevision());
    }}
