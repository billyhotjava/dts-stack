package com.yuzhi.dts.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ModelSpecImportCorrelationFilterTest {

    private final ModelSpecImportCorrelationFilter filter = new ModelSpecImportCorrelationFilter();

    @Test
    void installsTheSameCorrelationIdForEveryImportCommandOnSuccessAndError() throws Exception {
        List<String> paths = List.of(
            "/api/modeling/model-spec-imports/dbt/archive/inspect",
            "/api/modeling/model-spec-imports/dbt/preview",
            "/api/modeling/model-spec-imports/dbt/apply",
            "/api/modeling/model-spec-imports/9cff300e-7c49-46f4-8236-8fd87ec45e23/retry"
        );

        for (int index = 0; index < paths.size(); index++) {
            MockHttpServletRequest request = request("POST", paths.get(index));
            MockHttpServletResponse response = new MockHttpServletResponse();
            int expectedStatus = index == paths.size() - 1 ? 422 : 200;

            filter.doFilter(
                request,
                response,
                (filteredRequest, filteredResponse) -> response.setStatus(expectedStatus)
            );

            String correlationId = response.getHeader(DbtImplementationDraftCorrelation.HEADER);
            assertThat(correlationId).isNotBlank();
            assertThat(request.getAttribute(DbtImplementationDraftCorrelation.REQUEST_ATTRIBUTE)).isEqualTo(correlationId);
            assertThat(response.getStatus()).isEqualTo(expectedStatus);
        }
    }

    @Test
    void doesNotAddImportCorrelationToUnrelatedOrReadOnlyRequests() throws Exception {
        for (MockHttpServletRequest request :
            List.of(
                request("POST", "/api/modeling/model-specs"),
                request("GET", "/api/modeling/model-spec-imports/dbt/preview")
            )) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {});

            assertThat(response.getHeader(DbtImplementationDraftCorrelation.HEADER)).isNull();
            assertThat(request.getAttribute(DbtImplementationDraftCorrelation.REQUEST_ATTRIBUTE)).isNull();
        }
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
