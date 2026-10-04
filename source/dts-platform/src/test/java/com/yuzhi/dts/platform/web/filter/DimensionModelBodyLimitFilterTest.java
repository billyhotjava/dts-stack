package com.yuzhi.dts.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DimensionModelBodyLimitFilterTest {

    private AuditService auditService;
    private DimensionModelBodyLimitFilter filter;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        filter = new DimensionModelBodyLimitFilter(
            new ObjectMapper().findAndRegisterModules(),
            auditService,
            8
        );
    }

    @Test
    void rejectsAnOversizedDeclaredLengthBeforeReadingOrCallingTheResource() throws Exception {
        MockHttpServletRequest request = requestWithDeclaredLength(9);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertRejected(response, 9L);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), eq(response));
    }

    @Test
    void rejectsAnOversizedChunkedBodyAndAuditsOnlyFixedTechnicalMetadata() throws Exception {
        MockHttpServletRequest request = requestWithUnknownLength("123456789".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertRejected(response, 9L);
        ArgumentCaptor<Map<String, Object>> payload = mapCaptor();
        verify(auditService).auditActionStrict(
            eq("MODELING_DIMENSION_MODEL_CREATE"),
            eq(AuditStage.FAIL),
            eq("dimension-model-request"),
            payload.capture()
        );
        assertThat(payload.getValue())
            .containsOnlyKeys("errorCode", "observedBytes", "maxBytes")
            .doesNotContainKeys("requestBody", "operationId", "modelSpec", "definitionBinding");
    }

    @Test
    void passesAWithinBudgetBodyThroughARepeatableRequestWrapper() throws Exception {
        byte[] body = "12345678".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = requestWithUnknownLength(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> firstRead = new AtomicReference<>();
        AtomicReference<byte[]> secondRead = new AtomicReference<>();
        FilterChain chain = (wrappedRequest, ignoredResponse) -> {
            HttpServletRequest wrapped = (HttpServletRequest) wrappedRequest;
            firstRead.set(wrapped.getInputStream().readAllBytes());
            secondRead.set(wrapped.getInputStream().readAllBytes());
        };

        filter.doFilter(request, response, chain);

        assertThat(firstRead.get()).containsExactly(body);
        assertThat(secondRead.get()).containsExactly(body);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(auditService, never()).auditActionStrict(
            eq("MODELING_DIMENSION_MODEL_CREATE"),
            eq(AuditStage.FAIL),
            eq("dimension-model-request"),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void propagatesStrictAuditFailureWithoutPretendingTheRequestWasRejectedNormally() throws Exception {
        MockHttpServletRequest request = requestWithDeclaredLength(9);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new IllegalStateException("audit unavailable"))
            .when(auditService)
            .auditActionStrict(
                eq("MODELING_DIMENSION_MODEL_CREATE"),
                eq(AuditStage.FAIL),
                eq("dimension-model-request"),
                org.mockito.ArgumentMatchers.any()
            );

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("audit unavailable");
        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), eq(response));
    }

    private void assertRejected(MockHttpServletResponse response, long observedBytes) throws Exception {
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("DIMENSION_MODEL_REQUEST_TOO_LARGE");
        ArgumentCaptor<Map<String, Object>> payload = mapCaptor();
        verify(auditService).auditActionStrict(
            eq("MODELING_DIMENSION_MODEL_CREATE"),
            eq(AuditStage.FAIL),
            eq("dimension-model-request"),
            payload.capture()
        );
        assertThat(payload.getValue())
            .containsEntry("observedBytes", observedBytes)
            .containsEntry("maxBytes", 8L);
    }

    private static MockHttpServletRequest requestWithDeclaredLength(long declaredLength) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return declaredLength;
            }
        };
        request.setMethod("POST");
        request.setServletPath("/api/modeling/model-specs/dimension");
        return request;
    }

    private static MockHttpServletRequest requestWithUnknownLength(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setMethod("POST");
        request.setServletPath("/api/modeling/model-specs/dimension");
        request.setContent(body);
        return request;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
