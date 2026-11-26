package com.aistock.market.quotation;

import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/instruments/market-overview")
public class MarketOverviewController {
    private final IndexOverviewService service;

    public MarketOverviewController(IndexOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<IndexOverviewService.Overview> overview(
            @RequestHeader(value = "X-Trace-Id", required = false) String trace) {
        return ApiResponse.success(service.overview(), TraceIds.normalize(trace));
    }
}
