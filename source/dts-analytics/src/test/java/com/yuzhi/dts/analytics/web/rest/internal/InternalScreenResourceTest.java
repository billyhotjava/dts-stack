package com.yuzhi.dts.analytics.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.web.rest.internal.dto.ScreenSummaryDto;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/**
 * Sprint-17 / F1 — verifies the internal screens endpoint enforces the
 * {@code X-DTS-Service} allow-list and serializes archived rows so
 * dts-platform can flip {@code enabled=false} on its mirror.
 */
@ExtendWith(MockitoExtension.class)
class InternalScreenResourceTest {

    @Mock AnalyticsScreenRepository repository;
    @Mock HttpServletRequest request;

    InternalScreenResource resource;

    @BeforeEach
    void setUp() {
        resource = new InternalScreenResource(repository);
    }

    @Test
    @DisplayName("rejects request without X-DTS-Service header")
    void rejectsMissingHeader() {
        when(request.getHeader("X-DTS-Service")).thenReturn(null);

        ResponseEntity<?> r = resource.list(request);

        assertThat(r.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("rejects request from unknown service")
    void rejectsUnknownService() {
        when(request.getHeader("X-DTS-Service")).thenReturn("dts-malicious");

        ResponseEntity<?> r = resource.list(request);

        assertThat(r.getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("allows dts-platform and returns archived flag")
    void allowsDtsPlatform() {
        when(request.getHeader("X-DTS-Service")).thenReturn("dts-platform");
        when(repository.findAllByOrderByIdDesc()).thenReturn(List.of(
            screen(1L, "Sales", "INTERNAL", "DEPT_A", false),
            screen(2L, "OldOps", "SECRET", "DEPT_B", true)
        ));

        ResponseEntity<?> r = resource.list(request);

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        List<ScreenSummaryDto> body = (List<ScreenSummaryDto>) r.getBody();
        assertThat(body).hasSize(2);
        assertThat(body.get(0).id()).isEqualTo(1L);
        assertThat(body.get(0).archived()).isFalse();
        assertThat(body.get(1).archived()).isTrue();
    }

    private static AnalyticsScreen screen(long id, String name, String classification, String dept, boolean archived) {
        AnalyticsScreen s = new AnalyticsScreen();
        s.setId(id);
        s.setName(name);
        s.setClassification(classification);
        s.setOwnerDeptCode(dept);
        s.setArchived(archived);
        return s;
    }
}
