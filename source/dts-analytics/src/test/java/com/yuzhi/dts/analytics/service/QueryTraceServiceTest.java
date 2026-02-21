package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.repository.AnalyticsQueryTraceRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class QueryTraceServiceTest {

    @Test
    void summarizeFailures_returnsAggregatedStatsAndTopCodes() {
        RepoStub stub = new RepoStub();
        stub.total = 100L;
        stub.success = 76L;
        stub.summaryRows = List.of(new Object[] {"EXT_DB_TIMEOUT", 10L}, new Object[] {null, 3L});
        QueryTraceService service = new QueryTraceService(stub.repository(), new ObjectMapper());

        Map<String, Object> summary = service.summarizeFailures(7, 10, "card_query");

        assertThat(summary.get("windowDays")).isEqualTo(7);
        assertThat(summary.get("chain")).isEqualTo("card_query");
        assertThat(summary.get("total")).isEqualTo(100L);
        assertThat(summary.get("success")).isEqualTo(76L);
        assertThat(summary.get("failed")).isEqualTo(24L);
        assertThat(summary.get("failureRate")).isEqualTo(0.24d);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topErrorCodes = (List<Map<String, Object>>) summary.get("topErrorCodes");
        assertThat(topErrorCodes).hasSize(2);
        assertThat(topErrorCodes.get(0).get("code")).isEqualTo("EXT_DB_TIMEOUT");
        assertThat(topErrorCodes.get(0).get("category")).isEqualTo("timeout");
        assertThat(topErrorCodes.get(0).get("retryableHint")).isEqualTo(true);
        assertThat(topErrorCodes.get(1).get("code")).isEqualTo("UNKNOWN");
        assertThat(topErrorCodes.get(1).get("category")).isEqualTo("runtime");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topErrorCategories = (List<Map<String, Object>>) summary.get("topErrorCategories");
        assertThat(topErrorCategories).isNotEmpty();
        assertThat(topErrorCategories.get(0).get("category")).isEqualTo("timeout");
        assertThat(topErrorCategories.get(0).get("count")).isEqualTo(10L);
        assertThat(stub.lastChain).isEqualTo("card_query");
        assertThat(stub.lastPageable).isNotNull();
        assertThat(stub.lastPageable.getPageNumber()).isEqualTo(0);
        assertThat(stub.lastPageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void summarizeFailures_clampsWindowAndTopN() {
        RepoStub stub = new RepoStub();
        QueryTraceService service = new QueryTraceService(stub.repository(), new ObjectMapper());

        Map<String, Object> summary = service.summarizeFailures(0, 999, "   ");

        assertThat(summary.get("windowDays")).isEqualTo(1);
        assertThat(summary.get("chain")).isNull();
        assertThat(stub.lastChain).isNull();
        assertThat(stub.lastPageable).isNotNull();
        assertThat(stub.lastPageable.getPageNumber()).isEqualTo(0);
        assertThat(stub.lastPageable.getPageSize()).isEqualTo(20);
    }

    private static final class RepoStub {
        private long total = 0L;
        private long success = 0L;
        private List<Object[]> summaryRows = new ArrayList<>();
        private String lastChain;
        private Pageable lastPageable;
        private Instant lastSince;

        private AnalyticsQueryTraceRepository repository() {
            return (AnalyticsQueryTraceRepository) Proxy.newProxyInstance(
                    AnalyticsQueryTraceRepository.class.getClassLoader(),
                    new Class<?>[] {AnalyticsQueryTraceRepository.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "countAllSince" -> {
                            lastSince = args != null && args.length > 0 ? (Instant) args[0] : null;
                            lastChain = args != null && args.length > 1 ? (String) args[1] : null;
                            yield total;
                        }
                        case "countByStatusSince" -> {
                            lastSince = args != null && args.length > 0 ? (Instant) args[0] : null;
                            lastChain = args != null && args.length > 2 ? (String) args[2] : null;
                            yield success;
                        }
                        case "summarizeFailedErrorCodesSince" -> {
                            lastSince = args != null && args.length > 0 ? (Instant) args[0] : null;
                            lastChain = args != null && args.length > 1 ? (String) args[1] : null;
                            lastPageable = args != null && args.length > 2 ? (Pageable) args[2] : null;
                            yield summaryRows;
                        }
                        case "toString" -> "RepoStubProxy";
                        default -> throw new UnsupportedOperationException("Method not stubbed: " + method.getName());
                    });
        }
    }
}
