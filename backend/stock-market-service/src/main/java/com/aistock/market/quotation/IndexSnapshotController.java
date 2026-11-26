package com.aistock.market.quotation;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.market.refresh.IndexCollectorClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/market/index-snapshot")
public class IndexSnapshotController {
    private final IndexSnapshotStore store;
    private final IndexCollectorClient collector;

    public IndexSnapshotController(IndexSnapshotStore store, IndexCollectorClient collector) {
        this.store = store;
        this.collector = collector;
    }

    @PostMapping("/refresh")
    public ApiResponse<IndexSnapshotStore.Snapshot> refresh(@RequestBody IndexSnapshotStore.Request request,
            @RequestHeader(value = "X-Trace-Id", required = false) String trace) {
        store.checkRequest(request);
        var candidate = collector.collect(request);
        if (!request.start().equals(candidate.start()) || !request.end().equals(candidate.end())) {
            throw IndexSnapshotStore.invalid();
        }
        return ApiResponse.success(store.publish(candidate), TraceIds.normalize(trace));
    }

    @GetMapping
    public ApiResponse<IndexSnapshotStore.Snapshot> current(
            @RequestHeader(value = "X-Trace-Id", required = false) String trace) {
        var result = store.current().orElseThrow(() -> new BusinessException(
                "INDEX_SNAPSHOT_NOT_FOUND", "暂无指数快照", HttpStatus.NOT_FOUND));
        return ApiResponse.success(result, TraceIds.normalize(trace));
    }
}
