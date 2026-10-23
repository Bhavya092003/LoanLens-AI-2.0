package com.lending.platform.common.api;

import org.springframework.http.HttpStatus;

public class NotImplementedApiException extends ApiException {
    public NotImplementedApiException(String capability) {
        super(HttpStatus.NOT_IMPLEMENTED, "FEATURE_NOT_IMPLEMENTED", capability + " is not implemented yet");
    }
}
