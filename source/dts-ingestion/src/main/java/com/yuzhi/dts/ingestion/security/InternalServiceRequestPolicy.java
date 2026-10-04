package com.yuzhi.dts.ingestion.security;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Code-owned least-privilege policy for pairwise service credentials.
 *
 * <p>A configured token proves which service is calling; it does not grant that
 * service access to every authenticated endpoint. New service-to-service routes
 * must therefore be added here deliberately and reviewed together with their
 * controller authorization.</p>
 */
final class InternalServiceRequestPolicy {

    private static final Set<String> PLATFORM_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Pattern AIRFLOW_EXECUTION_READ = Pattern.compile(
        "^/internal/api-ingestion/executions/[A-Za-z0-9._:-]{1,128}$"
    );

    private InternalServiceRequestPolicy() {}

    static Grant authorize(String serviceName, String method, String requestPath) {
        String service = normalizeService(serviceName);
        String verb = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        String path = normalizePath(requestPath);
        if (service == null || path == null) {
            return Grant.denied();
        }
        if (
            "dts-platform".equals(service) &&
            PLATFORM_METHODS.contains(verb) &&
            ("/api/ingestion".equals(path) || path.startsWith("/api/ingestion/"))
        ) {
            return new Grant(true, service, true);
        }
        if ("dts-airflow".equals(service)) {
            boolean allowed =
                ("POST".equals(verb) && "/internal/api-ingestion/executions".equals(path)) ||
                ("POST".equals(verb) && "/internal/api-ingestion/scheduled-executions".equals(path)) ||
                ("GET".equals(verb) && AIRFLOW_EXECUTION_READ.matcher(path).matches());
            return allowed ? new Grant(true, service, false) : Grant.denied();
        }
        return Grant.denied();
    }

    private static String normalizeService(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return null;
        }
        String normalized = serviceName.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9][a-z0-9-]{0,63}") ? normalized : null;
    }

    private static String normalizePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }
        String path = requestPath.trim();
        if (
            !path.startsWith("/") ||
            path.contains("//") ||
            path.contains("..") ||
            path.indexOf(';') >= 0 ||
            path.indexOf('\\') >= 0 ||
            path.indexOf('%') >= 0
        ) {
            return null;
        }
        return path;
    }

    record Grant(boolean allowed, String serviceName, boolean forwardedIdentityAllowed) {
        static Grant denied() {
            return new Grant(false, null, false);
        }
    }
}
