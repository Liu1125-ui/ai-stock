package com.aistock.market.quotation;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.market.refresh.MarketCollector;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class StockValuationController {
    private final StockValuationStore store;
    private final MarketCollector collector;

    public StockValuationController(StockValuationStore store, MarketCollector collector) {
        this.store = store;
        this.collector = collector;
    }

    @GetMapping("/api/v1/instruments/{instrumentId}/valuation")
    public ApiResponse<StockValuationStore.View> read(@PathVariable String instrumentId, @RequestParam String versionId,
            @RequestHeader(value = "X-Trace-Id", required = false) String trace) {
        return ApiResponse.success(store.read(instrumentId, versionId), TraceIds.normalize(trace));
    }

    @PostMapping("/internal/v1/market/stock-valuation/{instrumentId}/{versionId}/refresh")
    public ApiResponse<StockValuationStore.View> refresh(@PathVariable String instrumentId, @PathVariable String versionId,
            @RequestHeader(value = "X-Trace-Id", required = false) String trace) {
        var target = store.target(instrumentId, versionId);
        if (!"baostock".equals(target.provider()) || target.tradeDate() == null) {
            return ApiResponse.success(store.read(instrumentId, versionId), TraceIds.normalize(trace));
        }
        var operation = UUID.randomUUID().toString();
        var result = collector.collect(operation, instrumentId, target.tradeDate(), target.tradeDate());
        if (result == null || !"AVAILABLE".equals(result.state())) {
            throw new BusinessException("STOCK_VALUATION_SOURCE_UNAVAILABLE", "估值来源暂不可用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        var candidate = result.candidate();
        if (candidate == null || !operation.equals(result.operationId()) || !operation.equals(candidate.operationId())
                || !instrumentId.equals(candidate.instrumentId()) || !target.provider().equals(candidate.provider())
                || !"RAW".equals(candidate.priceBasis()) || candidate.bars() == null || candidate.bars().size() != 1
                || candidate.fetchedAt() == null) throw mismatch();
        var bar = candidate.bars().get(0);
        if (bar == null || !target.tradeDate().equals(bar.date()) || bar.tradeStatus() != 1
                || bar.close() == null || bar.close().compareTo(target.close()) != 0) throw mismatch();
        store.save(target, result.valuation(), candidate.fetchedAt());
        return ApiResponse.success(store.read(instrumentId, versionId), TraceIds.normalize(trace));
    }

    private BusinessException mismatch() {
        return new BusinessException("STOCK_VALUATION_DATE_MISMATCH", "估值和已发布日线的日期或收盘价不一致",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
