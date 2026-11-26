package com.aistock.market.marketlifecycle;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.common.marketlifecycle.MarketLifecycleContract;
import com.aistock.market.adminquery.AdminMarketDataAccessFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.aistock.common.marketlifecycle.MarketLifecycleAdminQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(MarketLifecycleAdminQuery.STATE)
public final class MarketLifecycleAdminReadController {
    private final MarketLifecycleService lifecycle;

    public MarketLifecycleAdminReadController(MarketLifecycleService lifecycle) {
        this.lifecycle=lifecycle;
    }

    public record State(String instrumentId,String versionId,String useState,String revision,String cycleId,
                        long activeUses,boolean currentHead,boolean actionsReady) {}

    @GetMapping
    public ApiResponse<State> state(@PathVariable String instrumentId,@PathVariable String versionId,
        @RequestParam MultiValueMap<String,String> query,HttpServletRequest request,HttpServletResponse response) throws IOException {
        guard(instrumentId,versionId,request,response);
        MarketLifecycleAdminQuery.parse(query,false);
        var snapshot=lifecycle.snapshot(instrumentId,versionId);
        return ApiResponse.success(new State(snapshot.instrumentId(),snapshot.versionId(),snapshot.useState(),
            snapshot.revision(),snapshot.cycleId(),snapshot.activeUses(),snapshot.currentHead(),lifecycle.actionsReady()),trace(request));
    }

    @GetMapping("/history")
    public ApiResponse<MarketLifecycleRecords.History> history(@PathVariable String instrumentId,@PathVariable String versionId,
        @RequestParam MultiValueMap<String,String> query,HttpServletRequest request,HttpServletResponse response) throws IOException {
        guard(instrumentId,versionId,request,response);
        var page=MarketLifecycleAdminQuery.parse(query,true);
        return ApiResponse.success(lifecycle.history(instrumentId,versionId,page.cursor(),page.limit()),trace(request));
    }

    @PostMapping("/actions")
    public ApiResponse<MarketLifecycleRecords.Receipt> action(@PathVariable String instrumentId,@PathVariable String versionId,
        @RequestParam MultiValueMap<String,String> query,HttpServletRequest request,HttpServletResponse response) throws IOException {
        authorize(instrumentId,versionId,request,response);
        MarketLifecycleAdminQuery.parse(query,false);
        var contentTypes=java.util.Collections.list(request.getHeaders("Content-Type"));
        try {
            if(contentTypes.size()!=1||!MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(contentTypes.get(0))))
                throw MarketLifecycleContract.invalid();
        } catch(IllegalArgumentException error) {throw MarketLifecycleContract.invalid();}
        var command=MarketLifecycleContract.parseCommand(request.getInputStream().readNBytes(4097));
        return ApiResponse.success(lifecycle.action(instrumentId,versionId,actor(request),command),trace(request));
    }

    @GetMapping("/actions/{operationId}")
    public ApiResponse<MarketLifecycleRecords.Receipt> operation(@PathVariable String instrumentId,@PathVariable String versionId,
        @PathVariable String operationId,@RequestParam MultiValueMap<String,String> query,
        HttpServletRequest request,HttpServletResponse response) throws IOException {
        guard(instrumentId,versionId,request,response);
        MarketLifecycleAdminQuery.parse(query,false);
        MarketLifecycleContract.id(operationId);
        return ApiResponse.success(lifecycle.operation(instrumentId,versionId,actor(request),operationId),trace(request));
    }

    private static String actor(HttpServletRequest request) {
        String actor=(String)request.getAttribute(AdminMarketDataAccessFilter.ACTOR);
        MarketLifecycleContract.id(actor);
        return actor;
    }

    private static void guard(String stock,String version,
        HttpServletRequest request,HttpServletResponse response) throws IOException {
        authorize(stock,version,request,response);
        if(request.getInputStream().read()!=-1)throw MarketLifecycleContract.invalid();
    }

    private static void authorize(String stock,String version,HttpServletRequest request,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        if(!Boolean.TRUE.equals(request.getAttribute(AdminMarketDataAccessFilter.ADMIN)))
            throw new BusinessException("ACCESS_DENIED","仅管理员可以查看版本使用状态",HttpStatus.FORBIDDEN);
        MarketLifecycleContract.instrument(stock);
        MarketLifecycleContract.id(version);
    }

    private static String trace(HttpServletRequest request) {
        return TraceIds.normalize(request.getHeader("X-Trace-Id"));
    }
}
