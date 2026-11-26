package com.aistock.prediction.training;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.common.training.TrainingSubmission;
import com.aistock.prediction.modelcatalog.ModelRecords.Actor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/training-submissions")
@ConditionalOnProperty(name="aistock.training.admission-enabled",havingValue="true")
public final class TrainingAdmissionController {
    public static final String EXPERIMENT_AUTHORITY="EXPERIMENT_SUBMIT";
    private final TrainingAdmissionRepository repository;
    private final TrainingConfigurationCatalog catalog;
    private final TrainingProgressService progress;

    public TrainingAdmissionController(TrainingAdmissionRepository repository,TrainingConfigurationCatalog catalog,TrainingProgressService progress) {
        this.repository=repository;
        this.catalog=catalog;
        this.progress=progress;
    }

    public record Receipt(String requestId,List<TrainingAdmissionRepository.Item> items) {}

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Receipt> submit(Authentication authentication,HttpServletRequest request) throws IOException {
        var owner=actor(authentication);
        if(!canSubmit(authentication))
            throw new BusinessException("EXPERIMENT_ACCESS_DENIED","当前账号没有训练实验权限",HttpStatus.FORBIDDEN);
        if(!request.getParameterMap().isEmpty())throw invalid();
        var submission=TrainingSubmission.parse(request.getInputStream().readNBytes(2049));
        var items=repository.admit(owner.userId(),submission,catalog::find);
        return response(request,submission.requestId(),items);
    }

    @GetMapping("/{requestId}")
    public ApiResponse<Receipt> receipt(@PathVariable String requestId,Authentication authentication,
        HttpServletRequest request) throws IOException {
        var owner=actor(authentication);
        if(!request.getParameterMap().isEmpty()||request.getInputStream().read()!=-1)throw invalid();
        return response(request,requestId,repository.receipt(owner.userId(),requestId));
    }

    @GetMapping
    public ApiResponse<TrainingAdmissionRepository.History> history(Authentication authentication,HttpServletRequest request)
        throws IOException {
        var owner=actor(authentication);
        var query=request.getParameterMap();
        if(!query.keySet().stream().allMatch("before"::equals)
            ||query.values().stream().anyMatch(values->values.length!=1)||request.getInputStream().read()!=-1)throw invalid();
        String before=request.getParameter("before");
        if(before!=null)id(before);
        return ok(request,repository.history(owner.userId(),before));
    }

    @GetMapping("/options")
    public ApiResponse<List<String>> options(Authentication authentication,HttpServletRequest request) throws IOException {
        actor(authentication);
        empty(request);
        return ok(request,canSubmit(authentication)?catalog.ids():List.of());
    }

    @GetMapping("/{requestId}/status")
    public ApiResponse<List<TrainingProgressService.Status>> status(@PathVariable String requestId,
        Authentication authentication,HttpServletRequest request) throws IOException {
        var owner=actor(authentication);
        empty(request);
        id(requestId);
        return ok(request,repository.ownedItems(owner.userId(),requestId).stream().map(progress::status).toList());
    }

    @GetMapping("/{requestId}/tasks/{taskId}/metrics")
    public ApiResponse<com.fasterxml.jackson.databind.JsonNode> metrics(@PathVariable String requestId,
        @PathVariable String taskId,Authentication authentication,HttpServletRequest request) throws IOException {
        var owner=actor(authentication);
        empty(request);
        id(requestId);
        id(taskId);
        var item=repository.ownedItems(owner.userId(),requestId).stream().filter(row->taskId.equals(row.taskId()))
            .findFirst().orElseThrow(TrainingAdmissionRepository::notFound);
        return ok(request,progress.metrics(item));
    }

    private static boolean canSubmit(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a->EXPERIMENT_AUTHORITY.equals(a.getAuthority()));
    }

    private static void empty(HttpServletRequest request) throws IOException {
        if(!request.getParameterMap().isEmpty()||request.getInputStream().read()!=-1)throw invalid();
    }

    private static void id(String id) {
        if(!id.matches("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))throw invalid();
    }

    private static <T> ApiResponse<T> ok(HttpServletRequest request,T data) {
        return ApiResponse.success(data,TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }

    private static Actor actor(Authentication authentication) {
        if(authentication==null||!(authentication.getPrincipal() instanceof Actor actor)||actor.userId()==null)
            throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);
        return actor;
    }

    private static ApiResponse<Receipt> response(HttpServletRequest request,String id,List<TrainingAdmissionRepository.Item> items) {
        return ApiResponse.success(new Receipt(id,items),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }

    private static BusinessException invalid() {
        return new BusinessException("TRAINING_REQUEST_INVALID","训练请求格式不正确",HttpStatus.BAD_REQUEST);
    }
}
