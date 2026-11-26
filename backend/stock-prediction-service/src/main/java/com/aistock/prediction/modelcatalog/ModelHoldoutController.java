package com.aistock.prediction.modelcatalog;

import com.aistock.common.ApiResponse;
import com.aistock.common.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class ModelHoldoutController {
    private final ModelHoldoutComparison comparison;

    public ModelHoldoutController(ModelRepository models,ObjectMapper mapper,
        @Value("${aistock.internal.token:}") String token,
        @Value("${aistock.algorithm.url:http://127.0.0.1:18090}") String algorithm) {
        comparison=new ModelHoldoutComparison(models,mapper,token,algorithm);
    }

    @GetMapping("/api/v1/admin/models/{id}/holdout-comparison")
    public ApiResponse<ModelHoldoutComparison.Result> compare(@PathVariable UUID id,
        Authentication auth,HttpServletRequest request) {
        ModelReleaseService.requireAdmin(auth!=null&&auth.isAuthenticated()
            &&auth.getPrincipal() instanceof ModelRecords.Actor actor?actor:null);
        return ApiResponse.success(comparison.compare(id),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }
}
