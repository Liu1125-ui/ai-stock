package com.aistock.market.comparison;
import com.aistock.common.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static com.aistock.market.comparison.ComparisonEvidenceRecords.*;
@RestController
public class ComparisonEvidenceController {
    private final ComparisonEvidenceService service;
    public ComparisonEvidenceController(ComparisonEvidenceService service){this.service=service;}
    @GetMapping("/internal/v1/market/comparison-raw-window")
    public ApiResponse<ComparisonEvidenceService.RawWindow> raw(@RequestParam String instrumentId,
        @RequestParam LocalDate from,@RequestParam LocalDate to,jakarta.servlet.http.HttpServletRequest request,
        @RequestHeader(value="X-Trace-Id",required=false)String trace){
        if(!request.getParameterMap().keySet().equals(java.util.Set.of("instrumentId","from","to"))
            ||request.getParameterMap().values().stream().anyMatch(v->v.length!=1))
            throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
        return ApiResponse.success(service.rawWindow(instrumentId,from,to),TraceIds.normalize(trace));
    }
    @GetMapping("/internal/v1/market/comparison-options")
    public ApiResponse<ComparisonEvidenceOptions.Options> options(@RequestParam String ids,@RequestParam String period,
        jakarta.servlet.http.HttpServletRequest request,@RequestHeader(value="X-Trace-Id",required=false)String trace){
        if(!request.getParameterMap().keySet().equals(java.util.Set.of("ids","period"))||request.getParameterMap().values().stream().anyMatch(v->v.length!=1)||ids.length()>32)
            throw ComparisonEvidenceValidator.invalid("COMPARISON_REQUEST_INVALID");
        return ApiResponse.success(service.options(java.util.List.of(ids.split(",",-1)),period),TraceIds.normalize(trace));
    }
    @PostMapping("/internal/v1/market/comparison-evidence")
    public ApiResponse<Publication> publish(@RequestBody Candidate candidate,
        @RequestHeader(value="X-Trace-Id",required=false)String trace){
        return ApiResponse.success(service.publish(candidate),TraceIds.normalize(trace));
    }
    @GetMapping("/internal/v1/market/comparison-evidence/{id}")
    public ApiResponse<Evidence> evidence(@PathVariable UUID id,
        @RequestHeader(value="X-Trace-Id",required=false)String trace){
        return ApiResponse.success(service.version(id),TraceIds.normalize(trace));
    }
}