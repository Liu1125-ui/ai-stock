package com.aistock.gateway.adminmarket;

import com.aistock.common.*;
import com.aistock.common.adminmarket.AdminMarketQuery;
import com.aistock.common.marketlifecycle.MarketLifecycleAdminQuery;
import com.aistock.common.marketlifecycle.MarketLifecycleContract;
import com.aistock.gateway.*;
import com.aistock.gateway.security.AdminReadAccess;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.util.*;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/admin/market-data","/api/v1/admin/market-data/{instrumentId}/versions","/api/v1/admin/market-data/{instrumentId}/versions/{versionId}",MarketLifecycleAdminQuery.STATE,MarketLifecycleAdminQuery.HISTORY})
public class AdminMarketDataProxyController {
    static final int MAX_RESPONSE_BYTES=256*1024;
    private final MarketPublicClient market;
    private final AdminReadAccess access;
    private final ObjectMapper mapper;
    private final Clock clock;
    public AdminMarketDataProxyController(MarketPublicClient market,AdminReadAccess access,ObjectMapper mapper,Clock clock) {
        this.market=market;this.access=access;this.mapper=mapper;this.clock=clock;
    }
    @RequestMapping
    public ResponseEntity<byte[]> get(HttpServletRequest request,HttpServletResponse response,@RequestParam MultiValueMap<String,String> query)throws IOException {
        response.setHeader("Cache-Control","no-store");
        String path=request.getRequestURI();
        boolean lifecycle=MarketLifecycleAdminQuery.readPath(path);
        if(!"GET".equals(request.getMethod())||(!lifecycle&&!AdminMarketQuery.readPath(path)))
            throw new BusinessException("RESOURCE_NOT_FOUND","接口不存在",HttpStatus.NOT_FOUND);
        access.requireAdmin(request);
        if(request.getInputStream().read()!=-1)throw lifecycle?MarketLifecycleContract.invalid():AdminMarketQuery.invalid();
        boolean history=lifecycle&&path.endsWith("/history");
        var page=lifecycle?MarketLifecycleAdminQuery.parse(query,history):null;
        if(!lifecycle) {
            if(path.equals(AdminMarketQuery.BASE))AdminMarketQuery.listing(query);
            else if(path.endsWith("/versions"))AdminMarketQuery.history(query);
            else AdminMarketQuery.detail(query,clock);
        }
        var params=new LinkedHashMap<String,String>();query.forEach((key,values)->params.put(key,values.get(0)));
        try {
            var reply=market.get(path.substring("/api/v1/".length()),params,ProxyHeaderPolicy.request(request));
            if(reply==null||reply.getBody()==null||reply.getBody().length>MAX_RESPONSE_BYTES)throw unavailable();
            if(!reply.getStatusCode().is2xxSuccessful())throw upstream(reply.getStatusCode().value(),reply.getBody());
            var reader=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            if(lifecycle)reader=reader.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            var json=reader.readTree(reply.getBody());
            if(json==null||!"OK".equals(json.path("code").asText())||!json.path("data").isObject())throw unavailable();
            byte[] body=reply.getBody();
            if(lifecycle) {
                var data=json.path("data");
                validateLifecycleResponse(data,path,history,page.limit());
                body=mapper.writeValueAsBytes(ApiResponse.success(data,TraceIds.normalize(request.getHeader("X-Trace-Id"))));
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body);
        } catch(feign.FeignException e) {throw upstream(e.status(),e.content());}
        catch(BusinessException e){throw e;}
        catch(RuntimeException|IOException e){throw unavailable();}
    }
    private void validateLifecycleResponse(JsonNode data,String path,boolean history,int limit) {
        String[] segments=path.split("/");
        String instrument=segments[5],version=segments[7];
        try {
            if(history) {
                var items=data.path("items");
                var cursor=data.path("nextCursor");
                if(!fields(data,"items","nextCursor")||!items.isArray()||items.size()>limit)throw unavailable();
                if(!cursor.isNull()) {
                    MarketLifecycleContract.id(cursor.textValue());
                    if(items.isEmpty()||!cursor.textValue().equals(items.get(items.size()-1).path("operationId").textValue()))throw unavailable();
                }
                for(var item:items)validateReceipt(item,instrument,version);
                return;
            }
            if(!matchesVersion(data,instrument,version)
                ||!fields(data,"instrumentId","versionId","useState","revision","cycleId","activeUses","currentHead","actionsReady")
                ||!Set.of("ENABLED","DRAINING","DISABLED").contains(data.path("useState").asText())
                ||!data.path("activeUses").isIntegralNumber()||!data.path("activeUses").canConvertToLong()
                ||data.path("activeUses").longValue()<0||!data.path("currentHead").isBoolean()
                ||!data.path("actionsReady").isBoolean())throw unavailable();
            MarketLifecycleContract.revision(data.path("revision").textValue());
            if(!data.path("cycleId").isNull())MarketLifecycleContract.id(data.path("cycleId").textValue());
        }catch(BusinessException error){throw unavailable();}
    }

    static void validateReceipt(JsonNode item,String instrument,String version) {
        if(!matchesVersion(item,instrument,version)
            ||!fields(item,"operationId","instrumentId","versionId","action","expectedRevision","reason","state",
                "errorCode","revision","beforeState","afterState","cycleId","createdAt","finishedAt")
            ||!Set.of("DISABLE","RESTORE").contains(item.path("action").asText())
            ||!item.path("reason").isTextual()||!item.path("state").isTextual()
            ||!item.path("createdAt").isTextual())throw unavailable();
        MarketLifecycleContract.id(item.path("operationId").textValue());
        MarketLifecycleContract.revision(item.path("revision").textValue());
        MarketLifecycleContract.revision(item.path("expectedRevision").textValue());
        for(String key:List.of("errorCode","beforeState","afterState","cycleId","finishedAt")) {
            var field=item.path(key);
            if(!field.isNull()&&!field.isTextual())throw unavailable();
        }
    }

    private static boolean fields(JsonNode value,String... names) {
        return value.isObject()&&value.size()==names.length&&Arrays.stream(names).allMatch(value::has);
    }

    private static boolean matchesVersion(JsonNode value,String instrument,String version) {
        return value.isObject()&&instrument.equals(value.path("instrumentId").textValue())
            &&version.equals(value.path("versionId").textValue());
    }

    private BusinessException upstream(int status,byte[] bytes) {
        if(status==401)return new BusinessException("AUTH_REQUIRED","请重新登录",HttpStatus.UNAUTHORIZED);
        if(status==403)return new BusinessException("ACCESS_DENIED","仅管理员可以访问此功能",HttpStatus.FORBIDDEN);
        try {
            if(bytes!=null&&bytes.length<=MAX_RESPONSE_BYTES) {
                var json=mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(bytes);
                String code=json==null?"":json.path("code").asText();
                if(status==400&&"MARKET_VERSION_REQUEST_INVALID".equals(code))return MarketLifecycleContract.invalid();
                if(status==404&&"MARKET_VERSION_RESOURCE_NOT_FOUND".equals(code))return new BusinessException(code,"未找到所属行情版本或历史记录",HttpStatus.NOT_FOUND);
                if(status==400&&"MARKET_ADMIN_QUERY_INVALID".equals(code))return AdminMarketQuery.invalid();
                if(status==404&&"MARKET_ADMIN_RESOURCE_NOT_FOUND".equals(code))return new BusinessException(code,"未找到该股票或所属行情版本",HttpStatus.NOT_FOUND);
                if(status==503&&"USER_SERVICE_UNAVAILABLE".equals(code))return new BusinessException(code,"账号服务暂不可用",HttpStatus.SERVICE_UNAVAILABLE);
            }
        }catch(IOException|RuntimeException ignored){/* Never expose upstream error details. */}
        return unavailable();
    }
    private static BusinessException unavailable(){return new BusinessException("MARKET_ADMIN_UNAVAILABLE","行情管理暂不可用，请稍后刷新",HttpStatus.SERVICE_UNAVAILABLE);}
}
