package com.lending.platform.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        int status,
        String errorCode,
        String message,
        Instant timestamp,
        String path,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {}
}
