package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Server-generated request correlation shared by the body limit, service audit and safe errors. */
public final class DbtImplementationDraftCorrelation {

    public static final String HEADER = "X-Correlation-Id";
    public static final String REQUEST_ATTRIBUTE = DbtImplementationDraftCorrelation.class.getName() + ".id";

    private DbtImplementationDraftCorrelation() {}

    public static String install(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        String correlationId = existing instanceof String value && !value.isBlank() ? value : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        return correlationId;
    }

    public static String currentOrCreate() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
            if (existing instanceof String value && !value.isBlank()) return value;
            String correlationId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }
}
