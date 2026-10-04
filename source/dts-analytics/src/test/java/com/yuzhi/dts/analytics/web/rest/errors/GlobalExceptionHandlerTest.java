package com.yuzhi.dts.analytics.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void responseStatusException_preservesConflictStatusAndReason() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConflictController())
                .setControllerAdvice(handler)
                .build();

        mockMvc.perform(post("/status-conflict"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Error-Code", "CONFLICT"))
                .andExpect(header().string("X-Error-Retryable", "false"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("大屏密级无法确定"));
    }

    @RestController
    private static class ConflictController {

        @PostMapping("/status-conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "大屏密级无法确定");
        }
    }
}
