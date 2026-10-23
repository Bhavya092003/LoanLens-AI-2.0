package com.lending.platform.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void usesStableEnvelopeForApiErrors() {
        HttpServletRequest request = request("/api/v1/applications");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(
                new ApiException(HttpStatus.NOT_IMPLEMENTED, "FEATURE_NOT_IMPLEMENTED", "Not available yet"), request);

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertEquals("FEATURE_NOT_IMPLEMENTED", response.getBody().errorCode());
        assertEquals("/api/v1/applications", response.getBody().path());
        assertTrue(response.getBody().timestamp() != null);
    }

    @Test
    void doesNotExposeUnexpectedExceptionDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("database password should not leak"), request("/api/v1/health"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected server error occurred", response.getBody().message());
        assertEquals("INTERNAL_ERROR", response.getBody().errorCode());
    }

    @Test
    void mapsOversizedMultipartUploadsTo413() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(1024), request("/api/v1/policies/documents"));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("FILE_TOO_LARGE", response.getBody().errorCode());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
