package com.aistock.gateway.adminmarket;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.common.marketlifecycle.MarketLifecycleAdminQuery;
import com.aistock.common.marketlifecycle.MarketLifecycleContract;
import com.aistock.common.marketlifecycle.MarketUseRecords.Command;
import com.aistock.gateway.MarketReviewPublicClient;
import com.aistock.gateway.ProxyHeaderPolicy;
import com.aistock.gateway.security.AdminReadAccess;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public final class MarketLifecycleActionProxyController {
    private final MarketReviewPublicClient client;
    private final AdminReadAccess access;
    private final ObjectMapper mapper;
    private final Set<String> origins;

    public MarketLifecycleActionProxyController(MarketReviewPublicClient client,AdminReadAccess access,ObjectMapper mapper,
        @Value("${aistock.auth.allowed-origins:http://127.0.0.1:15173,http://localhost:15173}") String origins) {
        this.client=client;
        this.access=access;
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.origins=Set.copyOf(Arrays.stream(origins.split(",")).map(String::strip).filter(s->!s.isEmpty()).toList());
    }

    @RequestMapping({MarketLifecycleAdminQuery.ACTIONS,MarketLifecycleAdminQuery.OPERATION})
    public ResponseEntity<byte[]> handle(@PathVariable String instrumentId,@PathVariable String versionId,
        @PathVariable(required=false) String operationId,@RequestParam MultiValueMap<String,String> query,
        HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store");
        String path=request.getRequestURI();
        boolean write="POST".equals(request.getMethod());
        if((!write&&!"GET".equals(request.getMethod()))||!MarketLifecycleAdminQuery.actionPath(path,write))
            throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        access.requireAdmin(request);
        MarketLifecycleAdminQuery.parse(query,false);

        byte[] body=null;
        Command command=null;
        if(write) {
            if(!origins.contains(single(request,"Origin"))||!"1".equals(single(request,"X-Aistock-Admin-Action")))
                throw new BusinessException("TASK_ACTION_ORIGIN_DENIED","当前来源不允许提交操作",HttpStatus.FORBIDDEN);
            try {
                if(!MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(single(request,"Content-Type"))))
                    throw MarketLifecycleContract.invalid();
            } catch(IllegalArgumentException error) {throw MarketLifecycleContract.invalid();}
            body=request.getInputStream().readNBytes(4097);
            command=MarketLifecycleContract.parseCommand(body);
        } else if(request.getInputStream().read()!=-1) {
            throw MarketLifecycleContract.invalid();
        }

        try {
            var headers=ProxyHeaderPolicy.request(request);
            if(write)headers.remove("Content-Type"); // Feign sets the JSON content type for this route.
            var reply=write?client.post(path.substring(8),body,headers):client.get(path.substring(8),headers);
            if(reply==null||reply.getBody()==null||reply.getBody().length>65536)throw unavailable();
            var envelope=mapper.readTree(reply.getBody());
            if(envelope==null||!envelope.isObject())throw unavailable();
            int status=reply.getStatusCode().value();
            if(status!=200||!"OK".equals(envelope.path("code").asText()))
                throw upstream(status,reply.getBody());
            var data=envelope.path("data");
            try {
                AdminMarketDataProxyController.validateReceipt(data,instrumentId,versionId);
            } catch(BusinessException error) {throw unavailable();}
            String expected=write?command.operationId():operationId;
            if(!expected.equals(data.path("operationId").textValue())
                ||write&&(!command.action().equals(data.path("action").textValue())
                    ||!command.expectedRevision().equals(data.path("expectedRevision").textValue())
                    ||!command.reason().equals(data.path("reason").textValue())))throw unavailable();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                .body(mapper.writeValueAsBytes(ApiResponse.success(data,TraceIds.normalize(request.getHeader("X-Trace-Id")))));
        } catch(feign.FeignException error) {throw upstream(error.status(),error.content());}
        catch(BusinessException error) {throw error;}
        catch(IOException|RuntimeException error) {throw unavailable();}
    }

    private static String single(HttpServletRequest request,String name) {
        var values=Collections.list(request.getHeaders(name));
        return values.size()==1?values.get(0):"";
    }

    private BusinessException upstream(int status,byte[] body) {
        if(status==401)return new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);
        if(status==403)return new BusinessException("ACCESS_DENIED","当前账号或来源不允许此操作",HttpStatus.FORBIDDEN);
        if(body==null||body.length>65536)return unavailable();
        try {
            var json=mapper.readTree(body);
            String code=json==null?"":json.path("code").asText();
            if(status==400&&"MARKET_VERSION_REQUEST_INVALID".equals(code))return MarketLifecycleContract.invalid();
            if(status==404&&"MARKET_VERSION_RESOURCE_NOT_FOUND".equals(code))
                return new BusinessException(code,"未找到对应操作，请保留原编号核对",HttpStatus.NOT_FOUND);
            if(status==409&&"MARKET_VERSION_OPERATION_CONFLICT".equals(code))
                return new BusinessException(code,"操作编号与原请求不匹配",HttpStatus.CONFLICT);
            if(status==503&&"MARKET_LIFECYCLE_NOT_READY".equals(code))
                return new BusinessException(code,"版本操作暂未开放",HttpStatus.SERVICE_UNAVAILABLE);
        } catch(IOException error) {return unavailable();}
        return unavailable();
    }

    private static BusinessException unavailable() {
        return new BusinessException("MARKET_ADMIN_UNAVAILABLE","版本操作结果暂无法确认，请查询原操作编号",HttpStatus.SERVICE_UNAVAILABLE);
    }
}
