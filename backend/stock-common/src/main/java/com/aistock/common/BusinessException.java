package com.aistock.common;

import org.springframework.http.HttpStatus;

public final class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }
    public Object data() { return null; }
}
