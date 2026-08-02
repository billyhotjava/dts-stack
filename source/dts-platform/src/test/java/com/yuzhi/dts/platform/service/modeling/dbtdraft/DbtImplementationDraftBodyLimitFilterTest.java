package com.yuzhi.dts.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DbtImplementationDraftBodyLimitFilterTest {

    @Test
    void productionEnvelopeCoversWorstCaseJsonEscapingForTheDecodedContentBudget() {
        long worstCaseEscapedContent = DbtImplementationDraftContract.MAX_TOTAL_BYTES * 6L;

        assertThat(DbtImplementationDraftBodyLimitFilter.MAX_REQUEST_BYTES)
            .isGreaterThan(worstCaseEscapedContent);
    }

    @Test
    void rejectsUnknownLengthBodiesAboveTheHardLimitBeforeDispatcherDeserialization() throws Exception {
        byte[] body = "{\"files\":[\"0123456789012345678901234567890123456789\"]}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", draftPath()) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setServletPath(draftPath());
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean dispatched = new AtomicBoolean();
        DbtImplementationDraftRejectionAudit rejectionAudit = mock(DbtImplementationDraftRejectionAudit.class);
        DbtImplementationDraftBodyLimitFilter filter = new DbtImplementationDraftBodyLimitFilter(
            new ObjectMapper(),
            rejectionAudit,
            32
        );

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> dispatched.set(true));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("DBT_DRAFT_REQUEST_TOO_LARGE");
        assertThat(response.getHeader("X-Correlation-Id")).isNotBlank();
        assertThat(dispatched).isFalse();
        verify(rejectionAudit).recordBodyTooLarge(
            eq(request),
            eq(response.getHeader("X-Correlation-Id")),
            anyLong()
        );
    }

    @Test
    void replaysAnAcceptedBodyExactlyOnceToTheDispatcher() throws Exception {
        byte[] body = "{\"expectedEtag\":\"etag\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", draftPath());
        request.setServletPath(draftPath());
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        DbtImplementationDraftRejectionAudit rejectionAudit = mock(DbtImplementationDraftRejectionAudit.class);
        DbtImplementationDraftBodyLimitFilter filter = new DbtImplementationDraftBodyLimitFilter(
            new ObjectMapper(),
            rejectionAudit,
            64
        );

        filter.doFilter(request, response, (wrapped, ignoredResponse) -> {
            byte[] replayed = ((HttpServletRequest) wrapped).getInputStream().readAllBytes();
            assertThat(replayed).isEqualTo(body);
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void failsClosedWithoutWritingA413WhenStrictRejectionAuditCannotPersist() {
        byte[] body = "secret-body-that-is-too-large".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", draftPath());
        request.setServletPath(draftPath());
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean dispatched = new AtomicBoolean();
        DbtImplementationDraftRejectionAudit rejectionAudit = mock(DbtImplementationDraftRejectionAudit.class);
        doThrow(new IllegalStateException("audit unavailable"))
            .when(rejectionAudit)
            .recordBodyTooLarge(eq(request), org.mockito.ArgumentMatchers.anyString(), anyLong());
        DbtImplementationDraftBodyLimitFilter filter = new DbtImplementationDraftBodyLimitFilter(
            new ObjectMapper(),
            rejectionAudit,
            8
        );

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignored, ignoredResponse) -> dispatched.set(true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("audit unavailable");
        assertThat(response.getStatus()).isNotEqualTo(413);
        assertThat(dispatched).isFalse();
        assertThat(response.getHeader("X-Correlation-Id")).isNotBlank();
    }

    private static String draftPath() {
        return "/api/modeling/model-specs/20000000-0000-0000-0000-000000000083/dbt-drafts/" +
        "30000000-0000-0000-0000-000000000083/files";
    }
}
