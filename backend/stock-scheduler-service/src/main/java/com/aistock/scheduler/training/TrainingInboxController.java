package com.aistock.scheduler.training;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.common.training.TrainingProgress;
import com.aistock.scheduler.task.TaskSecurityFilter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(TrainingInboxController.PATH)
@ConditionalOnProperty(name="aistock.training.inbox-enabled",havingValue="true")
public final class TrainingInboxController {
    static final String PATH="/internal/v1/tasks/training-inbox";
    private final TrainingInbox inbox;
    private final ObjectMapper json=new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public TrainingInboxController(TrainingInbox inbox) {
        this.inbox=inbox;
    }

    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TrainingInbox.Envelope> receive(HttpServletRequest request,HttpServletResponse response) throws IOException {
        authorize(request,response,PATH);
        byte[] body=request.getInputStream().readNBytes(2049);
        if(body.length>2048)throw TrainingInbox.invalid();
        TrainingInbox.Envelope input;
        try {
            var data=json.readTree(body);
            if(data==null||!data.isObject()||data.size()!=4)throw TrainingInbox.invalid();
            for(String field:List.of("taskId","configurationId","configurationHash","datasetHash"))
                if(!data.path(field).isTextual())throw TrainingInbox.invalid();
            input=new TrainingInbox.Envelope(data.get("taskId").textValue(),data.get("configurationId").textValue(),
                data.get("configurationHash").textValue(),data.get("datasetHash").textValue());
        } catch(IOException error) {throw TrainingInbox.invalid();}
        return ApiResponse.success(inbox.receive(input),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TrainingInbox.Envelope> lookup(@PathVariable String taskId,HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        authorize(request,response,PATH+"/"+taskId);
        if(request.getInputStream().read()!=-1)throw TrainingInbox.invalid();
        return ApiResponse.success(inbox.get(taskId),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }

    @GetMapping("/{taskId}/status")
    public ApiResponse<com.aistock.common.training.TrainingProgress> status(@PathVariable String taskId,
        HttpServletRequest request,HttpServletResponse response) throws IOException {
        authorize(request,response,PATH+"/"+taskId+"/status");
        if(request.getInputStream().read()!=-1)throw TrainingInbox.invalid();
        return ApiResponse.success(inbox.status(taskId),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<TrainingProgress> cancel(@PathVariable String taskId,HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        authorize(request,response,PATH+"/"+taskId+"/cancel");
        if(request.getInputStream().read()!=-1)throw TrainingInbox.invalid();
        return ApiResponse.success(inbox.cancel(taskId),TraceIds.normalize(request.getHeader("X-Trace-Id")));
    }

    private void authorize(HttpServletRequest request,HttpServletResponse response,String path) {
        response.setHeader("Cache-Control","no-store");
        if(!path.equals(request.getRequestURI())||!Boolean.TRUE.equals(request.getAttribute(TaskSecurityFilter.AUTHENTICATED))
            ||Collections.list(request.getHeaders("X-Service-Token")).size()!=1)
            throw new BusinessException("INTERNAL_AUTH_REQUIRED","缺少有效服务身份",HttpStatus.UNAUTHORIZED);
        if(!request.getParameterMap().isEmpty())throw TrainingInbox.invalid();
    }
}
