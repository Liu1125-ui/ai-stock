package com.aistock.market;

import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EngineeringStatusController {
    @GetMapping("/api/v1/system/status")
    public ApiResponse<Map<String, String>> status(HttpServletRequest request) {
        String traceId = TraceIds.normalize(request.getHeader("X-Trace-Id"));
        return ApiResponse.success(Map.of("component", "stock-market-service", "phase", "ENGINEERING_SCAFFOLD"), traceId);
    }
}
