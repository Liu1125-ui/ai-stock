package com.aistock.common;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "成功", data, traceId);
    }
    public static ApiResponse<Void> failure(String code, String message, String traceId) {
        return new ApiResponse<>(code, message, null, traceId);
    }
}
