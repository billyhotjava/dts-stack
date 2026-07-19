package com.yuzhi.dts.platform.web.rest;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Stable HTTP contract shared by all retired business-object boundaries. */
final class LegacyModelingRetirementHttp {

    static final String SUNSET = "Thu, 31 Dec 2026 23:59:59 GMT";
    static final String SUCCESSOR_LINK = "</api/modeling/model-specs>; rel=\"successor-version\"";

    private LegacyModelingRetirementHttp() {}

    static <T> ResponseEntity<T> deprecated(T body) {
        return headers(ResponseEntity.ok()).body(body);
    }

    static ResponseEntity<ApiResponse<Object>> retired(String repairPath) {
        ApiResponse<Object> body = new ApiResponse<>(
            ResultStatus.ERROR.getCode(),
            "Business-object writes are retired; use canonical ModelSpec and catalog-domain APIs",
            "BUSINESS_OBJECT_RETIRED",
            Map.of("repairPath", repairPath)
        );
        return headers(ResponseEntity.status(HttpStatus.GONE)).body(body);
    }

    static <T extends ResponseEntity.BodyBuilder> T headers(T builder) {
        builder.header("Deprecation", "true");
        builder.header("Sunset", SUNSET);
        builder.header(HttpHeaders.LINK, SUCCESSOR_LINK);
        builder.header(HttpHeaders.CACHE_CONTROL, "no-store");
        return builder;
    }
}
