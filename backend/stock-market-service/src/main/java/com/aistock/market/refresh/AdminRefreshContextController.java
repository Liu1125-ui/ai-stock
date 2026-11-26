package com.aistock.market.refresh;
import com.aistock.common.*;
import com.aistock.common.adminrefresh.AdminMarketRefreshInput;
import com.aistock.common.adminrefresh.AdminMarketRefreshRecords.Context;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/market/admin-refresh-context")
public class AdminRefreshContextController {
    private final AdminRefreshContextRepository repository;
    private final Clock clock;
    public AdminRefreshContextController(AdminRefreshContextRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    @GetMapping("/{instrumentId}")
    public ResponseEntity<ApiResponse<Context>> context(@PathVariable String instrumentId,HttpServletRequest request)throws IOException {
        if(!request.getParameterMap().isEmpty()||request.getInputStream().read()!=-1)throw AdminMarketRefreshInput.invalid();
        var result=repository.find(instrumentId,clock.instant());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result,TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }
}