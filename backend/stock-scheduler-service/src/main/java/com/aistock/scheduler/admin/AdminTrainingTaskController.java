package com.aistock.scheduler.admin;

import com.aistock.common.ApiResponse;
import com.aistock.common.BusinessException;
import com.aistock.common.TraceIds;
import com.aistock.scheduler.security.AdminTaskPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/tasks/training")
public final class AdminTrainingTaskController {
    private final AdminTrainingTasks tasks;
    public AdminTrainingTaskController(AdminTrainingTasks tasks){this.tasks=tasks;}

    @GetMapping
    public ApiResponse<AdminTrainingTasks.Page> list(HttpServletRequest request) {
        principal(request);
        var query=request.getParameterMap();
        if(query.size()>1||!query.isEmpty()&&(!query.containsKey("page")||query.get("page").length!=1))
            throw AdminTaskActionInput.invalid();
        var value=request.getParameter("page");
        if(value!=null&&!value.matches("[1-9][0-9]{0,2}"))throw AdminTaskActionInput.invalid();
        return ApiResponse.success(tasks.list(value==null?1:Integer.parseInt(value)),trace(request));
    }

    @PostMapping("/{taskId}/cancel")
    public ApiResponse<AdminTaskActionRecords.Receipt> cancel(@PathVariable String taskId,HttpServletRequest request) throws IOException {
        var actor=principal(request);
        if(!request.getParameterMap().isEmpty())throw AdminTaskActionInput.invalid();
        var command=AdminTaskActionInput.parse(request.getInputStream().readNBytes(4097),AdminTaskActionRecords.Action.CANCEL);
        return ApiResponse.success(tasks.cancel(actor.userId(),AdminTaskActionInput.validateId(taskId),command),trace(request));
    }

    private static AdminTaskPrincipal principal(HttpServletRequest request) {
        if(request.getAttribute(AdminTaskPrincipal.class.getName()) instanceof AdminTaskPrincipal actor&&actor.userId()!=null)
            return actor;
        throw new BusinessException("AUTH_REQUIRED","请先登录",HttpStatus.UNAUTHORIZED);
    }

    private static String trace(HttpServletRequest request){return TraceIds.normalize(request.getHeader("X-Trace-Id"));}
}
