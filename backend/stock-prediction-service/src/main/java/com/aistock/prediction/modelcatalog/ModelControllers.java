package com.aistock.prediction.modelcatalog;
import com.aistock.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.aistock.prediction.modelcatalog.ModelRecords.*;
@RestController
public class ModelControllers {
    private final ModelCatalogService catalog;private final ModelReleaseService release;private final ModelRepository repo;private final ObjectMapper mapper;private final String token;
    public ModelControllers(ModelCatalogService catalog,ModelReleaseService release,ModelRepository repo,ObjectMapper mapper,@Value("${aistock.internal.token:}")String token){this.catalog=catalog;this.release=release;this.repo=repo;this.mapper=mapper;this.token=token;}
    @PostMapping("/internal/v1/models/candidates") public ApiResponse<Version> register(HttpServletRequest r){requireService(r);return ok(catalog.register(read(r,Registration.class)),r);}
    @GetMapping("/internal/v1/models/{modelId}/publication") public ApiResponse<PublicationDescriptor> publication(@PathVariable UUID modelId,HttpServletRequest r){
        requireService(r);return ok(repo.locked(head->{var v=repo.find(modelId,false);if(!"PUBLISHED".equals(v.state()))throw new BusinessException("MODEL_NOT_PUBLISHED","模型尚未发布或已下线",HttpStatus.CONFLICT);return new PublicationDescriptor(v.modelId(),v.artifactId(),v.manifestHash(),v.checkpointHash(),v.revision());}),r);
    }
    @GetMapping("/api/v1/admin/models") public ApiResponse<?> admin(Authentication auth,HttpServletRequest r){ModelReleaseService.requireAdmin(actor(auth));return ok(repo.locked(head->Map.of("items",repo.list(),"head",head)),r);}
    @GetMapping("/api/v1/admin/models/{modelId}/events") public ApiResponse<?> events(Authentication auth,@PathVariable UUID modelId,HttpServletRequest r){ModelReleaseService.requireAdmin(actor(auth));return ok(repo.events(modelId),r);}
    @PostMapping("/api/v1/admin/models/{modelId}/{action:publish|reject|offline|recommend|rollback}") public ApiResponse<ActionResult> action(Authentication auth,@PathVariable UUID modelId,@PathVariable String action,HttpServletRequest r){var who=actor(auth);ModelReleaseService.requireAdmin(who);return ok(release.mutate(who,modelId,action,read(r,ActionInput.class)),r);}
    @GetMapping("/api/v1/models") public ApiResponse<?> published(Authentication auth,HttpServletRequest r){actor(auth);return ok(repo.locked(head->{var cards=repo.published().stream().map(v->Map.of("modelId",v.modelId(),"artifactId",v.artifactId(),"window",60,"horizon",5,"instruments",v.manifest().path("instruments"),"featureDefinition",v.manifest().path("featureDefinition"),"trainingCutoff",v.manifest().path("trainingCutoff"),"evaluation",v.evaluation(),"recommended",v.modelId().equals(head.currentModelId()))).toList();return Map.of("items",cards,"headRevision",head.revision());}),r);}
    private Actor actor(Authentication auth){if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof Actor actor))throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);return actor;}
    private void requireService(HttpServletRequest r){var supplied=r.getHeader("X-Service-Token");if(token.isBlank())throw new BusinessException("INTERNAL_AUTH_NOT_CONFIGURED","内部服务认证未配置",HttpStatus.SERVICE_UNAVAILABLE);if(supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new BusinessException("INTERNAL_AUTH_REQUIRED","缺少有效服务身份",HttpStatus.UNAUTHORIZED);}
    private <T>T read(HttpServletRequest r,Class<T> type){try{var bytes=r.getInputStream().readNBytes(65537);if(bytes.length>65536)throw new BusinessException("MODEL_PAYLOAD_TOO_LARGE","模型请求超过当前处理上限",HttpStatus.PAYLOAD_TOO_LARGE);return mapper.readValue(bytes,type);}catch(java.io.IOException e){throw new BusinessException("VALIDATION_FAILED","请求格式或字段不正确",HttpStatus.BAD_REQUEST);}}
    private <T>ApiResponse<T> ok(T value,HttpServletRequest r){return ApiResponse.success(value,TraceIds.normalize(r.getHeader("X-Trace-Id")));}
}
