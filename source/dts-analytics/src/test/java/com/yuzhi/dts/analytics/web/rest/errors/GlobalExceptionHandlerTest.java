package com.yuzhi.dts.analytics.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalArgument_mapsReadOnlySqlCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analytics/api/card/1/query");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ApiError> entity = handler.handleIllegalArgument(
                new IllegalArgumentException("Only SELECT/WITH read-only SQL is allowed"),
                request,
                response);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().code()).isEqualTo("SQL_READ_ONLY_REQUIRED");
        assertThat(response.getHeader("X-Error-Code")).isEqualTo("SQL_READ_ONLY_REQUIRED");
        assertThat(response.getHeader("X-Error-Retryable")).isEqualTo("false");
    }

    @Test
    void handleIllegalArgument_mapsTemplateMissingCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analytics/api/dataset");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ApiError> entity = handler.handleIllegalArgument(
                new IllegalArgumentException("Missing required SQL template parameters"),
                request,
                response);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().code()).isEqualTo("SQL_TEMPLATE_PARAM_MISSING");
        assertThat(response.getHeader("X-Error-Code")).isEqualTo("SQL_TEMPLATE_PARAM_MISSING");
    }

    @Test
    void handleIllegalArgument_usesDefaultCodeForGenericMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/analytics/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ApiError> entity = handler.handleIllegalArgument(
                new IllegalArgumentException("some generic message"),
                request,
                response);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().code()).isEqualTo("REQ_INVALID_ARGUMENT");
        assertThat(response.getHeader("X-Error-Code")).isEqualTo("REQ_INVALID_ARGUMENT");
    }
}
