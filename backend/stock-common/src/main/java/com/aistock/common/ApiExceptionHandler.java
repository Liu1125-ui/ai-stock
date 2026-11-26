package com.aistock.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_FAILED",
                "请求格式或参数不正确", TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException error,
            HttpServletRequest request) {
        String traceId = TraceIds.normalize(request.getHeader("X-Trace-Id"));
        return ResponseEntity.status(error.status())
                .body(ApiResponse.failure(error.code(), error.getMessage(), traceId));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(
            org.springframework.web.servlet.resource.NoResourceFoundException error,
            HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiResponse.failure("RESOURCE_NOT_FOUND",
                "接口不存在", TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoMapping(
            org.springframework.web.servlet.NoHandlerFoundException error,
            HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiResponse.failure("RESOURCE_NOT_FOUND",
                "接口不存在", TraceIds.normalize(request.getHeader("X-Trace-Id"))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception error,
            HttpServletRequest request) {
        String traceId = TraceIds.normalize(request.getHeader("X-Trace-Id"));
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).warn("Unexpected API failure: type={}, origin={}, traceId={}", error.getClass().getName(), error.getStackTrace().length == 0 ? "unknown" : error.getStackTrace()[0], traceId);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.failure("INTERNAL_ERROR", "服务暂时不可用", traceId));
    }
}
