package com.aistock.market.marketlifecycle;

import com.aistock.common.*;
import com.aistock.common.marketlifecycle.*;
import com.aistock.common.marketlifecycle.MarketUseRecords.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/market-version-uses")
public final class MarketUseController {
    private final MarketLifecycleService service;private final MarketUseProofClient proofs;private final boolean enabled;private final boolean evidenceGatesEnabled;
    public MarketUseController(MarketLifecycleService service,MarketUseProofClient proofs,@Value("${aistock.market.lifecycle.protocol-enabled:false}")boolean enabled,@Value("${aistock.market.lifecycle.evidence-gates-enabled:false}")boolean evidenceGatesEnabled){this.service=service;this.proofs=proofs;this.enabled=enabled;this.evidenceGatesEnabled=evidenceGatesEnabled;}
    @PostMapping public ApiResponse<?> register(HttpServletRequest request)throws IOException {
        if(!request.getParameterMap().isEmpty())throw MarketUseProtocol.invalid();var r=MarketUseProtocol.register(body(request));
        return ok(request,MarketUseProtocol.receipt(r.requestId(),service.register(r.requestId(),r.identity(),r.selector(),enabled)));
    }
    @GetMapping("/capabilities") public ApiResponse<?> capabilities(HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty())throw MarketUseProtocol.invalid();
        return ok(request,Map.of("protocolVersion",MarketLifecycleContract.PROTOCOL,"protocolEnabled",enabled,"evidenceGatesEnabled",evidenceGatesEnabled,"enforcementMode","OFF","actionsEnabled",false,"consumerIntegration","PENDING"));
    }
    @GetMapping("/requests/{id}") public ApiResponse<?> registration(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest request) {
        var identity=queryIdentity(query);return ok(request,MarketUseProtocol.receipt(id,service.registration(id,identity)));
    }
    @GetMapping("/{id}") public ApiResponse<?> binding(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,HttpServletRequest request) {
        var identity=queryIdentity(query);return ok(request,MarketUseProtocol.receipt(null,service.binding(id,identity)));
    }
    @PostMapping("/{id}/completion") public ApiResponse<?> completion(@PathVariable String id,HttpServletRequest request)throws IOException {
        if(!request.getParameterMap().isEmpty())throw MarketUseProtocol.invalid();var identity=MarketUseProtocol.completion(body(request));var bundle=service.binding(id,identity);
        service.markUseChecked(id);var proof=proofs.read(bundle).orElseThrow(()->new BusinessException("MARKET_USE_PROOF_UNAVAILABLE","尚不能核实任务终态，原使用状态保留",HttpStatus.SERVICE_UNAVAILABLE));
        service.releaseVerified(id,proof);return ok(request,MarketUseProtocol.receipt(null,service.binding(id,identity)));
    }
    private UseIdentity queryIdentity(MultiValueMap<String,String> query) {
        if(!query.keySet().equals(Set.of("owner","purpose","ownerOperationId","generation"))||query.values().stream().anyMatch(v->v.size()!=1))throw MarketUseProtocol.invalid();
        var id=new UseIdentity(query.getFirst("owner"),query.getFirst("purpose"),query.getFirst("ownerOperationId"),query.getFirst("generation"));MarketLifecycleContract.identity(id);return id;
    }
    private byte[] body(HttpServletRequest request)throws IOException{var bytes=request.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new BusinessException("MARKET_PAYLOAD_TOO_LARGE","请求超过上限",HttpStatus.PAYLOAD_TOO_LARGE);return bytes;}
    private ApiResponse<?> ok(HttpServletRequest request,Object value){return ApiResponse.success(value,TraceIds.normalize(request.getHeader("X-Trace-Id")));}
}
