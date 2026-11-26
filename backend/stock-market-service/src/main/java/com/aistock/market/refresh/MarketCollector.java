package com.aistock.market.refresh;
import com.aistock.market.ingestion.MarketCandidate;
import java.time.LocalDate;
import com.aistock.market.quotation.StockValuationStore.Sample;
@FunctionalInterface public interface MarketCollector {
    Collected collect(String operationId,String instrumentId,LocalDate start,LocalDate end);
    record Collected(String operationId,String state,MarketCandidate candidate,String errorCode,Sample valuation) {
        public Collected(String operationId,String state,MarketCandidate candidate,String errorCode) {
            this(operationId,state,candidate,errorCode,null);
        }
    }
}
