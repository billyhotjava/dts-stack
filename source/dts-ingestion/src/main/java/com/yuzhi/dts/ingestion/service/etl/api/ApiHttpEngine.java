package com.yuzhi.dts.ingestion.service.etl.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.yuzhi.dts.ingestion.config.ApiProperties;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.service.etl.connector.ExecutionPlan;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.util.unit.DataSize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApiHttpEngine {

    private static final Duration DEFAULT_JWT_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration JWT_REFRESH_SKEW = Duration.ofSeconds(60);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> SUPPORTED_AUTH_PROVIDERS = List.of(
        "none",
        "apiKey",
        "bearerToken",
        "basic",
        "oauth2ClientCredentials",
        "jwtLogin"
    );

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    interface HostAddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    record ResolvedTarget(String host, InetAddress[] addresses) {
        ResolvedTarget {
            if (!StringUtils.hasText(host) || addresses == null || addresses.length == 0) {
                throw new IllegalArgumentException("Resolved target requires a host and addresses");
            }
            addresses = addresses.clone();
        }

        @Override
        public InetAddress[] addresses() {
            return addresses.clone();
        }
    }

    static final class PinnedDnsResolver implements DnsResolver {

        private final ResolvedTarget target;

        PinnedDnsResolver(ResolvedTarget target) {
            this.target = target;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            if (!target.host().equalsIgnoreCase(host)) {
                throw new UnknownHostException("Unvalidated host: " + host);
            }
            return target.addresses();
        }

        @Override
        public String resolveCanonicalHostname(String host) throws UnknownHostException {
            resolve(host);
            return target.host();
        }
    }

    private record PinnedHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {

        Optional<String> firstHeader(String name) {
            if (!StringUtils.hasText(name)) {
                return Optional.empty();
            }
            return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
        }
    }

    public record ApiHttpResult(
        String resourceId,
        URI uri,
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body,
        int attempts,
        int pageNo,
        List<JsonNode> records,
        String cursorValue
    ) {
        public String bodyText() {
            return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
        }
    }

    private final Sleeper sleeper;
    private final ConcurrentMap<String, RateGate> rateGates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Semaphore> concurrencyGates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedToken> jwtTokenCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedToken> oauth2TokenCache = new ConcurrentHashMap<>();
    private final CursorTracker cursorTracker = new CursorTracker();
    private final ApiProperties apiProperties;
    private final HostAddressResolver hostAddressResolver;
    private final boolean allowNonGlobalTargetsForTests;

    public ApiHttpEngine() {
        this(defaultSleeper(), new ApiProperties());
    }

    public ApiHttpEngine(ApiProperties apiProperties) {
        this(defaultSleeper(), apiProperties);
    }

    ApiHttpEngine(Sleeper sleeper) {
        this(sleeper, new ApiProperties());
    }

    ApiHttpEngine(Sleeper sleeper, ApiProperties apiProperties) {
        this(sleeper, apiProperties, InetAddress::getAllByName, false);
    }

    ApiHttpEngine(
        Sleeper sleeper,
        ApiProperties apiProperties,
        HostAddressResolver hostAddressResolver,
        boolean allowNonGlobalTargetsForTests
    ) {
        this.sleeper = sleeper == null ? duration -> {} : sleeper;
        this.apiProperties = apiProperties == null ? new ApiProperties() : apiProperties;
        this.hostAddressResolver = hostAddressResolver == null ? InetAddress::getAllByName : hostAddressResolver;
        this.allowNonGlobalTargetsForTests = allowNonGlobalTargetsForTests;
    }

    public static List<String> supportedAuthProviders() {
        return SUPPORTED_AUTH_PROVIDERS;
    }

    private static Sleeper defaultSleeper() {
        return duration -> {
            if (!duration.isNegative() && !duration.isZero()) {
                Thread.sleep(duration.toMillis());
            }
        };
    }

    public List<ApiHttpResult> execute(ExecutionPlan plan) {
        return execute(plan, null);
    }

    public List<ApiHttpResult> execute(ExecutionPlan plan, IngestionExecution execution) {
        return execute(plan, execution, Map.of());
    }

    public List<ApiHttpResult> execute(ExecutionPlan plan, IngestionExecution execution, Map<String, String> checkpoints) {
        if (plan == null || !"api-http".equalsIgnoreCase(plan.engine())) {
            throw new IllegalArgumentException("API HTTP 执行计划无效");
        }
        Map<String, Object> payload = safeMap(plan.payload());
        Map<String, Object> sourceConfig = safeMap(payload.get("sourceConfig"));
        String baseUrl = text(sourceConfig.get("baseUrl"));
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("API baseUrl 不能为空");
        }
        List<Map<String, Object>> resources = extractResources(sourceConfig);
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("API resource 配置不能为空");
        }

        List<ApiHttpResult> results = new ArrayList<>(resources.size());
        for (Map<String, Object> resource : resources) {
            results.addAll(executeResource(baseUrl, sourceConfig, resource, execution, checkpoints));
        }
        return List.copyOf(results);
    }

    private List<ApiHttpResult> executeResource(
        String baseUrl,
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        IngestionExecution execution,
        Map<String, String> checkpoints
    ) {
        Map<String, Object> requestPolicy = mergedPolicy(sourceConfig, resource, "requestPolicy");
        Map<String, Object> retryPolicy = mergedPolicy(sourceConfig, resource, "retryPolicy");
        Map<String, Object> rateLimit = mergedPolicy(sourceConfig, resource, "rateLimit");
        Map<String, Object> tlsPolicy = mergedPolicy(sourceConfig, resource, "tls");
        Map<String, Object> pagination = safeMap(resource.get("pagination"));
        Map<String, Object> cursor = safeMap(resource.get("cursor"));
        String resourceId = firstText(resource, "resourceId", "id", "name");
        if (!StringUtils.hasText(resourceId)) {
            resourceId = normalizeResourceId(firstText(resource, "path"));
        }
        String checkpointValue = checkpoints == null ? null : checkpoints.get(resourceId);
        Map<String, Object> query = initialQuery(sourceConfig, resource, pagination, cursor, execution, checkpointValue);
        URI uri = buildUri(baseUrl, firstText(resource, "path"), query);
        String method = Optional.ofNullable(firstText(resource, "method")).orElse("GET").trim().toUpperCase(Locale.ROOT);
        int maxPages = positiveInt(pagination.get("maxPages"), apiProperties.getMaxPages());
        List<ApiHttpResult> results = new ArrayList<>();
        int pageNo = 1;
        int consecutiveEmptyPages = 0;
        boolean explicitNextDriven = false;
        while (pageNo <= maxPages) {
            ApiHttpResult result = executePage(
                uri,
                method,
                resourceId,
                sourceConfig,
                resource,
                requestPolicy,
                tlsPolicy,
                retryPolicy,
                rateLimit,
                cursor,
                pageNo
            );
            results.add(result);
            consecutiveEmptyPages = result.records().isEmpty() ? consecutiveEmptyPages + 1 : 0;
            NextPage nextPage = nextPage(
                baseUrl,
                sourceConfig,
                resource,
                pagination,
                cursor,
                execution,
                checkpointValue,
                result,
                pageNo,
                consecutiveEmptyPages,
                explicitNextDriven,
                requestPolicy
            );
            if (nextPage == null) {
                break;
            }
            explicitNextDriven = nextPage.explicitNextDriven();
            uri = nextPage.uri();
            pageNo++;
        }
        if (pageNo > maxPages) {
            throw new ApiHttpException("API_RUNTIME_PAGINATION", "API 翻页超过最大页数: " + maxPages, null, 1);
        }
        return List.copyOf(results);
    }

    private ApiHttpResult executePage(
        URI uri,
        String method,
        String resourceId,
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> requestPolicy,
        Map<String, Object> tlsPolicy,
        Map<String, Object> retryPolicy,
        Map<String, Object> rateLimit,
        Map<String, Object> cursor,
        int pageNo
    ) {
        boolean retryAllowed = retryAllowed(method, sourceConfig, resource, retryPolicy);
        int maxRetries = retryAllowed
            ? nonNegativeInt(retryPolicy.get("maxRetries"), apiProperties.getRetry().getMaxRetries())
            : 0;
        int attempts = 0;
        int retries = 0;
        int redirects = 0;
        boolean jwtTokenRefreshedAfter401 = false;
        URI requestUri = uri;
        validateUri(requestUri, requestPolicy);
        while (true) {
            attempts++;
            try {
                Semaphore semaphore = concurrencyGate(resourceId, rateLimit);
                Integer retryStatus = null;
                Optional<String> retryAfter = Optional.empty();
                semaphore.acquire();
                try {
                    applyRateLimit(resourceId, rateLimit);
                    PinnedHttpResponse response = executePinned(
                        requestUri,
                        method,
                        sourceConfig,
                        resource,
                        requestPolicy,
                        tlsPolicy,
                        positiveBytes(requestPolicy.get("maxResponseBytes"), apiProperties.maxResponseBytesAsInt()),
                        attempts
                    );
                    byte[] body = response.body();
                    int status = response.statusCode();
                    if (isRedirect(status)) {
                        if (!bool(requestPolicy.get("followRedirects"), false)) {
                            throw new ApiHttpException("API_RUNTIME_REDIRECT", "API 重定向默认关闭", status, attempts);
                        }
                        int maxRedirects = Math.min(10, positiveInt(requestPolicy.get("maxRedirects"), 5));
                        if (redirects >= maxRedirects) {
                            throw new ApiHttpException("API_RUNTIME_REDIRECT", "API 重定向超过最大跳数", status, attempts);
                        }
                        String location = response.firstHeader("Location").orElse(null);
                        if (!StringUtils.hasText(location)) {
                            throw new ApiHttpException("API_RUNTIME_REDIRECT", "API 重定向缺少 Location", status, attempts);
                        }
                        URI redirected = requestUri.resolve(location);
                        requestUri = guardRelatedUri(
                            requestUri.toString(),
                            redirected,
                            requestPolicy,
                            "API 重定向 URL 不允许跨源: "
                        );
                        redirects++;
                        continue;
                    }
                    if (status >= 200 && status < 300) {
                        List<JsonNode> records = extractRecords(body, resource);
                        String cursorValue = cursorTracker.advance(records, cursor);
                        return new ApiHttpResult(
                            resourceId,
                            requestUri,
                            status,
                            response.headers(),
                            body,
                            attempts,
                            pageNo,
                            records,
                            cursorValue
                        );
                    }
                    if (status == 401
                        && isJwtLogin(sourceConfig)
                        && !jwtTokenRefreshedAfter401
                        && retries < maxRetries) {
                        invalidateJwtToken(sourceConfig);
                        jwtTokenRefreshedAfter401 = true;
                        retries++;
                        continue;
                    }
                    if (shouldRetry(status) && retries < maxRetries) {
                        retries++;
                        retryStatus = status;
                        retryAfter = response.firstHeader("Retry-After");
                    } else {
                        throw statusException(status, attempts);
                    }
                } finally {
                    semaphore.release();
                }
                if (retryStatus != null) {
                    sleepBeforeRetry(retryStatus, retryAfter, attempts, retryPolicy);
                    continue;
                }
            } catch (ApiHttpException ex) {
                throw ex;
            } catch (IOException ex) {
                if (retries < maxRetries) {
                    retries++;
                    try {
                        sleepBeforeRetry(null, Optional.empty(), attempts, retryPolicy);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ApiHttpException("API_RUNTIME_NETWORK", "API 调用被中断", null, attempts, interrupted);
                    }
                    continue;
                }
                throw new ApiHttpException("API_RUNTIME_NETWORK", "API 网络调用失败: " + ex.getMessage(), null, attempts, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiHttpException("API_RUNTIME_NETWORK", "API 调用被中断", null, attempts, ex);
            }
        }
    }

    private Map<String, Object> initialQuery(
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> pagination,
        Map<String, Object> cursor,
        IngestionExecution execution,
        String checkpointValue
    ) {
        Map<String, Object> query = baseQuery(sourceConfig, resource, cursor, execution, checkpointValue);
        String type = firstText(pagination, "type");
        int pageSize = positiveInt(pagination.get("pageSize"), 0);
        if ("page".equalsIgnoreCase(type)) {
            query.putIfAbsent(Optional.ofNullable(firstText(pagination, "pageParam")).orElse("page"), 1);
            putPageSize(query, pagination, pageSize);
        } else if ("offset".equalsIgnoreCase(type)) {
            query.putIfAbsent(Optional.ofNullable(firstText(pagination, "offsetParam")).orElse("offset"), 0);
            putPageSize(query, pagination, pageSize);
        } else if ("token".equalsIgnoreCase(type)) {
            putPageSize(query, pagination, pageSize);
        }
        query.putAll(authQueryParameters(sourceConfig));
        return query;
    }

    private Map<String, Object> baseQuery(
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> cursor,
        IngestionExecution execution,
        String checkpointValue
    ) {
        Map<String, Object> query = new LinkedHashMap<>(safeMap(resource.get("query")));
        if (cursor == null || cursor.isEmpty()) {
            return query;
        }
        String injectInto = firstText(cursor, "injectInto");
        if (StringUtils.hasText(injectInto) && !"query".equalsIgnoreCase(injectInto)) {
            return query;
        }
        String parameterName = cursorStartParameter(cursor);
        if (isBackfillExecution(execution)) {
            if (StringUtils.hasText(parameterName)) {
                query.put(parameterName, execution.getBackfillWindowStart().toString());
            }
            String endParameterName = firstText(cursor, "endParameterName", "parameterEndName", "upperParameterName", "endParam");
            if (StringUtils.hasText(endParameterName)) {
                query.put(endParameterName, execution.getBackfillWindowEnd().toString());
            }
            return query;
        }
        String startValue = cursorTracker.startValue(cursor, checkpointValue);
        if (StringUtils.hasText(parameterName) && StringUtils.hasText(startValue)) {
            query.putIfAbsent(parameterName, startValue);
        }
        return query;
    }

    private String cursorStartParameter(Map<String, Object> cursor) {
        return Optional.ofNullable(firstText(cursor, "startParameterName"))
            .orElse(Optional.ofNullable(firstText(cursor, "parameterName")).orElse(firstText(cursor, "field")));
    }

    private boolean isBackfillExecution(IngestionExecution execution) {
        return execution != null && execution.getBackfillWindowStart() != null && execution.getBackfillWindowEnd() != null;
    }

    private NextPage nextPage(
        String baseUrl,
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> pagination,
        Map<String, Object> cursor,
        IngestionExecution execution,
        String checkpointValue,
        ApiHttpResult result,
        int pageNo,
        int consecutiveEmptyPages,
        boolean explicitNextDriven,
        Map<String, Object> requestPolicy
    ) {
        if (pagination == null || pagination.isEmpty()) {
            return null;
        }
        JsonNode body = parseBody(result.body(), result.attempts());
        String nextUrl = nextUrlFromBody(body, pagination);
        if (StringUtils.hasText(nextUrl)) {
            URI nextUri = guardContinuationUri(baseUrl, uriFromNext(baseUrl, nextUrl), requestPolicy);
            return new NextPage(appendQueryParameters(nextUri, authQueryParameters(sourceConfig)), true);
        }
        Optional<URI> linkNext = linkNextUri(result.headers());
        if (linkNext.isPresent()) {
            URI nextUri = guardContinuationUri(baseUrl, linkNext.get(), requestPolicy);
            return new NextPage(appendQueryParameters(nextUri, authQueryParameters(sourceConfig)), true);
        }
        if (explicitNextDriven) {
            return null;
        }

        String type = firstText(pagination, "type");
        if ("token".equalsIgnoreCase(type)) {
            String token = text(JsonPathLite.read(body, firstText(pagination, "nextTokenPath")).asText(null));
            if (!StringUtils.hasText(token)) {
                return null;
            }
            Map<String, Object> query = baseQuery(sourceConfig, resource, cursor, execution, checkpointValue);
            String tokenParam = Optional.ofNullable(firstText(pagination, "tokenParam")).orElse("pageToken");
            query.put(tokenParam, token);
            putPageSize(query, pagination, positiveInt(pagination.get("pageSize"), 0));
            query.putAll(authQueryParameters(sourceConfig));
            return new NextPage(buildUri(baseUrl, firstText(resource, "path"), query), false);
        }

        int emptyPageLimit = positiveInt(pagination.get("emptyPageLimit"), 1);
        if (consecutiveEmptyPages >= emptyPageLimit) {
            return null;
        }
        if ("page".equalsIgnoreCase(type)) {
            Map<String, Object> query = baseQuery(sourceConfig, resource, cursor, execution, checkpointValue);
            query.put(Optional.ofNullable(firstText(pagination, "pageParam")).orElse("page"), pageNo + 1);
            putPageSize(query, pagination, positiveInt(pagination.get("pageSize"), 0));
            query.putAll(authQueryParameters(sourceConfig));
            return new NextPage(buildUri(baseUrl, firstText(resource, "path"), query), false);
        }
        if ("offset".equalsIgnoreCase(type)) {
            int pageSize = positiveInt(pagination.get("pageSize"), 100);
            Map<String, Object> query = baseQuery(sourceConfig, resource, cursor, execution, checkpointValue);
            query.put(Optional.ofNullable(firstText(pagination, "offsetParam")).orElse("offset"), pageNo * pageSize);
            putPageSize(query, pagination, pageSize);
            query.putAll(authQueryParameters(sourceConfig));
            return new NextPage(buildUri(baseUrl, firstText(resource, "path"), query), false);
        }
        return null;
    }

    private List<JsonNode> extractRecords(byte[] body, Map<String, Object> resource) {
        String recordPath = firstText(resource, "recordPath");
        if (!StringUtils.hasText(recordPath)) {
            return List.of();
        }
        return JsonPathLite.readRecords(parseBody(body, 1), recordPath);
    }

    private JsonNode parseBody(byte[] body, int attempts) {
        try {
            return OBJECT_MAPPER.readTree(body == null ? new byte[0] : body);
        } catch (IOException ex) {
            throw new ApiHttpException(
                "API_RUNTIME_RESPONSE_PARSE",
                "API 响应 JSON 解析失败: " + ex.getMessage(),
                null,
                attempts,
                ex
            );
        }
    }

    private String nextUrlFromBody(JsonNode body, Map<String, Object> pagination) {
        String nextUrlPath = firstText(pagination, "nextUrlPath");
        if (!StringUtils.hasText(nextUrlPath)) {
            return null;
        }
        return text(JsonPathLite.read(body, nextUrlPath).asText(null));
    }

    private Optional<URI> linkNextUri(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"link".equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                Optional<URI> next = parseLinkNext(value);
                if (next.isPresent()) {
                    return next;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<URI> parseLinkNext(String linkHeader) {
        if (!StringUtils.hasText(linkHeader)) {
            return Optional.empty();
        }
        for (String part : linkHeader.split(",")) {
            String value = part.trim();
            int start = value.indexOf('<');
            int end = value.indexOf('>');
            if (start >= 0 && end > start && value.toLowerCase(Locale.ROOT).contains("rel=\"next\"")) {
                return Optional.of(URI.create(value.substring(start + 1, end)));
            }
        }
        return Optional.empty();
    }

    private URI uriFromNext(String baseUrl, String nextUrl) {
        if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
            return URI.create(nextUrl);
        }
        return buildUri(baseUrl, nextUrl, Map.of());
    }

    private URI guardContinuationUri(String baseUrl, URI uri, Map<String, Object> requestPolicy) {
        return guardRelatedUri(baseUrl, uri, requestPolicy, "API 翻页 URL 不允许跨源: ");
    }

    private URI guardAuthUri(String baseUrl, URI uri, Map<String, Object> requestPolicy) {
        return guardRelatedUri(baseUrl, uri, requestPolicy, "API 鉴权 URL 不允许跨源: ");
    }

    private URI guardRelatedUri(String baseUrl, URI uri, Map<String, Object> requestPolicy, String messagePrefix) {
        Map<String, Object> policy = safeMap(requestPolicy);
        validateUri(uri, policy);
        if (sameOrigin(baseUrl, uri) || allowedByPolicy(uri.getHost(), policy)) {
            return uri;
        }
        throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", messagePrefix + uri.getHost(), null, 0);
    }

    private boolean sameOrigin(String baseUrl, URI uri) {
        if (!StringUtils.hasText(baseUrl) || uri == null) {
            return false;
        }
        try {
            URI base = URI.create(baseUrl);
            return sameText(base.getScheme(), uri.getScheme())
                && sameText(base.getHost(), uri.getHost())
                && effectivePort(base) == effectivePort(uri);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean allowedByPolicy(String host, Map<String, Object> requestPolicy) {
        List<String> allowedHosts = allowedHosts(safeMap(requestPolicy).get("allowedHosts"));
        if (allowedHosts.isEmpty()) {
            allowedHosts = apiProperties.getAllowedHosts();
        }
        return !allowedHosts.isEmpty() && hostAllowed(host, allowedHosts);
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.equalsIgnoreCase(right);
    }

    private int effectivePort(URI uri) {
        if (uri == null) {
            return -1;
        }
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }

    private URI appendQueryParameters(URI uri, Map<String, Object> query) {
        if (uri == null || query == null || query.isEmpty()) {
            return uri;
        }
        String rawQuery = uri.getRawQuery();
        List<String> pairs = new ArrayList<>();
        query.forEach((key, value) -> {
            if (!StringUtils.hasText(key) || value == null) {
                return;
            }
            String encodedKey = urlEncode(key);
            if (rawQuery != null && rawQuery.contains(encodedKey + "=")) {
                return;
            }
            pairs.add(encodedKey + "=" + urlEncode(String.valueOf(value)));
        });
        if (pairs.isEmpty()) {
            return uri;
        }
        String value = uri.toString();
        String fragment = "";
        int fragmentIndex = value.indexOf('#');
        if (fragmentIndex >= 0) {
            fragment = value.substring(fragmentIndex);
            value = value.substring(0, fragmentIndex);
        }
        value += (uri.getRawQuery() == null ? "?" : "&") + String.join("&", pairs);
        return URI.create(value + fragment);
    }

    private void putPageSize(Map<String, Object> query, Map<String, Object> pagination, int pageSize) {
        if (pageSize <= 0) {
            return;
        }
        query.putIfAbsent(Optional.ofNullable(firstText(pagination, "sizeParam")).orElse("size"), pageSize);
    }

    private PinnedHttpResponse executePinned(
        URI uri,
        String method,
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> requestPolicy,
        Map<String, Object> tlsPolicy,
        int maxResponseBytes,
        int attempts
    ) throws IOException {
        validateUri(uri, requestPolicy);
        ResolvedTarget target = resolveTarget(uri.getHost());
        HttpUriRequestBase request = buildRequest(uri, method, sourceConfig, resource, requestPolicy);
        try (CloseableHttpClient client = buildClient(requestPolicy, tlsPolicy, sourceConfig, target)) {
            return client.execute(request, response -> {
                HttpEntity entity = response.getEntity();
                byte[] body = entity == null
                    ? new byte[0]
                    : readBody(entity.getContent(), maxResponseBytes, attempts);
                return new PinnedHttpResponse(response.getCode(), responseHeaders(response.getHeaders()), body);
            });
        }
    }

    private PinnedHttpResponse executeRawPinned(
        URI uri,
        String method,
        Map<String, String> headers,
        String body,
        Map<String, Object> requestPolicy,
        Map<String, Object> tlsPolicy,
        Map<String, Object> sourceConfig,
        int maxResponseBytes,
        int attempts
    ) throws IOException {
        validateUri(uri, requestPolicy);
        ResolvedTarget target = resolveTarget(uri.getHost());
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        headers.forEach(request::setHeader);
        if (!("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method))) {
            ContentType contentType = ContentType.parse(
                headers.entrySet().stream()
                    .filter(entry -> "Content-Type".equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("application/octet-stream")
            );
            request.setEntity(new ByteArrayEntity((body == null ? "" : body).getBytes(StandardCharsets.UTF_8), contentType));
        }
        try (CloseableHttpClient client = buildClient(requestPolicy, tlsPolicy, sourceConfig, target)) {
            return client.execute(request, response -> {
                HttpEntity entity = response.getEntity();
                byte[] responseBody = entity == null
                    ? new byte[0]
                    : readBody(entity.getContent(), maxResponseBytes, attempts);
                return new PinnedHttpResponse(response.getCode(), responseHeaders(response.getHeaders()), responseBody);
            });
        }
    }

    private CloseableHttpClient buildClient(
        Map<String, Object> requestPolicy,
        Map<String, Object> tlsPolicy,
        Map<String, Object> sourceConfig,
        ResolvedTarget target
    ) {
        Timeout connectTimeout = Timeout.ofMilliseconds(
            durationMillis(requestPolicy.get("connectTimeoutMillis"), apiProperties.getConnectTimeout())
        );
        Timeout readTimeout = Timeout.ofMilliseconds(
            durationMillis(requestPolicy.get("readTimeoutMillis"), apiProperties.getReadTimeout())
        );
        var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(new PinnedDnsResolver(target))
            .setDefaultConnectionConfig(
                ConnectionConfig.custom()
                    .setConnectTimeout(connectTimeout)
                    .setSocketTimeout(readTimeout)
                    .build()
            );
        Map<String, Object> safeTlsPolicy = safeMap(tlsPolicy);
        if (!bool(safeTlsPolicy.get("verifyTls"), true)) {
            connectionManagerBuilder.setSSLSocketFactory(
                SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(insecureSslContext())
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build()
            );
        } else {
            String caPem = tlsCaPem(safeTlsPolicy, sourceConfig);
            if (StringUtils.hasText(caPem)) {
                connectionManagerBuilder.setSSLSocketFactory(
                    SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(customCaSslContext(caPem))
                        .build()
                );
            }
        }
        RequestConfig requestConfig = RequestConfig.custom()
            .setRedirectsEnabled(false)
            .setConnectTimeout(connectTimeout)
            .setResponseTimeout(readTimeout)
            .build();
        return HttpClients.custom()
            .setConnectionManager(connectionManagerBuilder.build())
            .setDefaultRequestConfig(requestConfig)
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .build();
    }

    private HttpUriRequestBase buildRequest(
        URI uri,
        String method,
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> requestPolicy
    ) {
        validateUri(uri, requestPolicy);
        HttpUriRequestBase request = new HttpUriRequestBase(method, uri);
        Map<String, Object> headers = new LinkedHashMap<>(safeMap(sourceConfig.get("defaultHeaders")));
        headers.putAll(safeMap(resource.get("headers")));
        applyAuth(headers, sourceConfig);
        headers.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                request.setHeader(key, String.valueOf(value));
            }
        });

        Object bodyTemplate = resource.get("bodyTemplate");
        if (!("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method))) {
            byte[] body = (bodyTemplate == null ? "" : String.valueOf(bodyTemplate)).getBytes(StandardCharsets.UTF_8);
            request.setEntity(new ByteArrayEntity(body, requestContentType(headers)));
        }
        return request;
    }

    private ContentType requestContentType(Map<String, Object> headers) {
        String value = headers.entrySet().stream()
            .filter(entry -> "Content-Type".equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue)
            .filter(java.util.Objects::nonNull)
            .map(String::valueOf)
            .findFirst()
            .orElse("application/octet-stream");
        try {
            return ContentType.parse(value);
        } catch (RuntimeException ignored) {
            return ContentType.APPLICATION_OCTET_STREAM;
        }
    }

    private Map<String, List<String>> responseHeaders(Header[] headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (headers != null) {
            for (Header header : headers) {
                result.computeIfAbsent(header.getName(), ignored -> new ArrayList<>()).add(header.getValue());
            }
        }
        Map<String, List<String>> immutable = new LinkedHashMap<>();
        result.forEach((name, values) -> immutable.put(name, List.copyOf(values)));
        return Map.copyOf(immutable);
    }

    private SSLContext insecureSslContext() {
        try {
            TrustManager[] trustManagers = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                },
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new SecureRandom());
            return sslContext;
        } catch (Exception ex) {
            throw new ApiHttpException("API_RUNTIME_TLS", "API TLS 策略初始化失败: " + ex.getMessage(), null, 0, ex);
        }
    }

    private String tlsCaPem(Map<String, Object> tlsPolicy, Map<String, Object> sourceConfig) {
        String inlinePem = firstText(tlsPolicy, "caPem", "customCaPem", "caCertificatePem");
        if (StringUtils.hasText(inlinePem)) {
            return inlinePem;
        }
        String secretRef = firstText(tlsPolicy, "caSecretRef", "customCaSecretRef", "caPemSecretRef", "caCertificateSecretRef");
        Map<String, Object> secrets = safeMap(sourceConfig == null ? null : sourceConfig.get("secrets"));
        if (StringUtils.hasText(secretRef)) {
            String resolved = text(secrets.get(secretRef));
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }
        return firstText(secrets, "caPem", "customCaPem", "caCertificatePem");
    }

    private SSLContext customCaSslContext(String caPem) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(
                new ByteArrayInputStream(caPem.getBytes(StandardCharsets.UTF_8))
            );
            if (certificates.isEmpty()) {
                throw new ApiHttpException("API_RUNTIME_TLS", "API TLS CA 证书为空", null, 0);
            }
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : certificates) {
                trustStore.setCertificateEntry("api-ca-" + index++, certificate);
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (ApiHttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiHttpException("API_RUNTIME_TLS", "API TLS CA 证书加载失败: " + ex.getMessage(), null, 0, ex);
        }
    }

    private void applyAuth(Map<String, Object> headers, Map<String, Object> sourceConfig) {
        Map<String, Object> auth = normalizedAuth(sourceConfig);
        String provider = firstText(auth, "provider");
        if (!StringUtils.hasText(provider) || "none".equalsIgnoreCase(provider)) {
            return;
        }
        if (SUPPORTED_AUTH_PROVIDERS.stream().noneMatch(supported -> supported.equalsIgnoreCase(provider))) {
            throw unsupportedAuthProvider(provider);
        }
        Map<String, Object> secrets = safeMap(sourceConfig.get("secrets"));
        if ("bearerToken".equalsIgnoreCase(provider)) {
            String token = resolveSecret(secrets, firstText(auth, "tokenRef"), "token");
            if (!StringUtils.hasText(token)) {
                throw new ApiHttpException("API_RUNTIME_AUTH", "Bearer token 密钥缺失", null, 0);
            }
            headers.put("Authorization", "Bearer " + token);
        } else if ("apiKey".equalsIgnoreCase(provider) && !"query".equalsIgnoreCase(firstText(auth, "location"))) {
            String name = firstText(auth, "name");
            String value = resolveSecret(secrets, firstText(auth, "valueRef"), "value");
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                throw new ApiHttpException("API_RUNTIME_AUTH", "API Key 配置不完整", null, 0);
            }
            headers.put(name, value);
        } else if ("basic".equalsIgnoreCase(provider)) {
            String username = firstText(auth, "username");
            String password = resolveSecret(secrets, firstText(auth, "passwordRef"), "password");
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new ApiHttpException("API_RUNTIME_AUTH", "Basic Auth 配置不完整", null, 0);
            }
            String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + encoded);
        } else if ("jwtLogin".equalsIgnoreCase(provider)) {
            CachedToken token = resolveJwtToken(sourceConfig, auth, secrets);
            if ("header".equalsIgnoreCase(firstText(auth, "tokenPlacement"))) {
                String headerName = firstText(auth, "tokenHeaderName");
                if (!StringUtils.hasText(headerName)) {
                    throw new ApiHttpException("API_RUNTIME_AUTH", "JWT token header 名称缺失", null, 0);
                }
                headers.put(headerName, token.token());
            } else {
                headers.put("Authorization", "Bearer " + token.token());
            }
        } else if ("oauth2ClientCredentials".equalsIgnoreCase(provider)) {
            CachedToken token = resolveOAuth2Token(sourceConfig, auth, secrets);
            headers.put("Authorization", "Bearer " + token.token());
        }
    }

    private ApiHttpException unsupportedAuthProvider(String provider) {
        return new ApiHttpException("API_RUNTIME_AUTH_UNSUPPORTED", "API 鉴权方式暂未开放: " + provider, null, 0);
    }

    private Map<String, Object> normalizedAuth(Map<String, Object> sourceConfig) {
        Map<String, Object> auth = new LinkedHashMap<>(safeMap(sourceConfig.get("auth")));
        Map<String, Object> config = safeMap(auth.get("config"));
        config.forEach(auth::putIfAbsent);
        Map<String, Object> secretRefs = safeMap(auth.get("secretRefs"));
        secretRefs.forEach((fieldName, secretRef) -> {
            if (!StringUtils.hasText(fieldName) || secretRef == null) {
                return;
            }
            String refKey = fieldName.endsWith("Ref") ? fieldName : fieldName + "Ref";
            auth.putIfAbsent(refKey, secretRef);
        });
        return auth;
    }

    private CachedToken resolveJwtToken(Map<String, Object> sourceConfig, Map<String, Object> auth, Map<String, Object> secrets) {
        String loginUrl = firstText(auth, "loginUrl");
        String tokenPath = firstText(auth, "tokenPath");
        if (!StringUtils.hasText(loginUrl) || !StringUtils.hasText(tokenPath)) {
            throw new ApiHttpException("API_RUNTIME_AUTH", "JWT 登录配置不完整", null, 0);
        }
        String cacheKey = jwtCacheKey(sourceConfig, auth);
        CachedToken cached = jwtTokenCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now.plus(JWT_REFRESH_SKEW))) {
            return cached;
        }
        CachedToken refreshed = loginForJwtToken(sourceConfig, auth, secrets, now);
        jwtTokenCache.put(cacheKey, refreshed);
        return refreshed;
    }

    private boolean isJwtLogin(Map<String, Object> sourceConfig) {
        return "jwtLogin".equalsIgnoreCase(firstText(normalizedAuth(sourceConfig), "provider"));
    }

    private void invalidateJwtToken(Map<String, Object> sourceConfig) {
        Map<String, Object> auth = normalizedAuth(sourceConfig);
        if (StringUtils.hasText(firstText(auth, "loginUrl")) && StringUtils.hasText(firstText(auth, "tokenPath"))) {
            jwtTokenCache.remove(jwtCacheKey(sourceConfig, auth));
        }
    }

    private String jwtCacheKey(Map<String, Object> sourceConfig, Map<String, Object> auth) {
        return firstText(sourceConfig, "baseUrl")
            + "|"
            + firstText(auth, "loginUrl")
            + "|"
            + firstText(auth, "tokenPath")
            + "|"
            + firstText(auth, "tokenPlacement");
    }

    private CachedToken resolveOAuth2Token(Map<String, Object> sourceConfig, Map<String, Object> auth, Map<String, Object> secrets) {
        String tokenUrl = firstText(auth, "tokenUrl");
        String clientId = firstText(auth, "clientId");
        String clientSecret = resolveSecret(secrets, firstText(auth, "clientSecretRef"), "clientSecret");
        if (!StringUtils.hasText(tokenUrl) || !StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new ApiHttpException("API_RUNTIME_AUTH", "OAuth2 client_credentials 配置不完整", null, 0);
        }
        String cacheKey = firstText(sourceConfig, "baseUrl") + "|" + tokenUrl + "|" + clientId + "|" + firstText(auth, "scope");
        CachedToken cached = oauth2TokenCache.get(cacheKey);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now.plus(JWT_REFRESH_SKEW))) {
            return cached;
        }
        CachedToken refreshed = requestOAuth2Token(sourceConfig, auth, clientSecret, now);
        oauth2TokenCache.put(cacheKey, refreshed);
        return refreshed;
    }

    private CachedToken requestOAuth2Token(
        Map<String, Object> sourceConfig,
        Map<String, Object> auth,
        String clientSecret,
        Instant now
    ) {
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", firstText(auth, "clientId"));
        form.put("client_secret", clientSecret);
        String scope = firstText(auth, "scope");
        if (StringUtils.hasText(scope)) {
            form.put("scope", scope);
        }
        String body = formEncoded(form);
        URI uri = buildAuthUri(sourceConfig, firstText(auth, "tokenUrl"));
        try {
            PinnedHttpResponse response = executeRawPinned(
                uri,
                "POST",
                Map.of("Content-Type", "application/x-www-form-urlencoded"),
                body,
                safeMap(sourceConfig.get("requestPolicy")),
                safeMap(sourceConfig.get("tls")),
                sourceConfig,
                apiProperties.maxResponseBytesAsInt(),
                1
            );
            byte[] bodyBytes = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiHttpException(
                    "API_RUNTIME_AUTH",
                    "OAuth2 token endpoint 失败: HTTP " + response.statusCode(),
                    response.statusCode(),
                    1
                );
            }
            JsonNode json = OBJECT_MAPPER.readTree(bodyBytes);
            String token = text(json.path("access_token").asText(null));
            if (!StringUtils.hasText(token)) {
                throw new ApiHttpException("API_RUNTIME_AUTH", "OAuth2 响应未包含 access_token", response.statusCode(), 1);
            }
            Instant expiresAt = resolveTokenExpiresAt(json, "$.expires_in", token, now);
            return new CachedToken(token, expiresAt);
        } catch (ApiHttpException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiHttpException("API_RUNTIME_AUTH", "OAuth2 token endpoint 调用失败", null, 1, ex);
        }
    }

    private String formEncoded(Map<String, Object> form) {
        List<String> parts = new ArrayList<>();
        form.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                parts.add(urlEncode(key) + "=" + urlEncode(String.valueOf(value)));
            }
        });
        return String.join("&", parts);
    }

    private CachedToken loginForJwtToken(
        Map<String, Object> sourceConfig,
        Map<String, Object> auth,
        Map<String, Object> secrets,
        Instant now
    ) {
        String loginUrl = firstText(auth, "loginUrl");
        String loginMethod = Optional.ofNullable(firstText(auth, "loginMethod")).orElse("POST").trim().toUpperCase(Locale.ROOT);
        String body = renderSecretTemplate(firstText(auth, "loginBodyTemplate"), secrets);
        URI uri = buildAuthUri(sourceConfig, loginUrl);
        try {
            PinnedHttpResponse response = executeRawPinned(
                uri,
                loginMethod,
                Map.of("Content-Type", "application/json"),
                body,
                safeMap(sourceConfig.get("requestPolicy")),
                safeMap(sourceConfig.get("tls")),
                sourceConfig,
                apiProperties.maxResponseBytesAsInt(),
                1
            );
            byte[] bodyBytes = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiHttpException("API_RUNTIME_AUTH", "JWT 登录失败: HTTP " + response.statusCode(), response.statusCode(), 1);
            }
            JsonNode json = OBJECT_MAPPER.readTree(bodyBytes);
            String token = text(jsonAt(json, firstText(auth, "tokenPath")).asText(null));
            if (!StringUtils.hasText(token)) {
                throw new ApiHttpException("API_RUNTIME_AUTH", "JWT 登录响应未包含 token", response.statusCode(), 1);
            }
            Instant expiresAt = resolveTokenExpiresAt(json, firstText(auth, "expiresInPath"), token, now);
            return new CachedToken(token, expiresAt);
        } catch (ApiHttpException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ApiHttpException("API_RUNTIME_AUTH", "JWT 登录调用失败", null, 1, ex);
        }
    }

    private String renderSecretTemplate(String template, Map<String, Object> secrets) {
        if (template == null) {
            return "";
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : secrets.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace("{{secretRefs." + entry.getKey() + "}}", value);
            rendered = rendered.replace("{{secrets." + entry.getKey() + "}}", value);
        }
        return rendered;
    }

    private URI buildAuthUri(Map<String, Object> sourceConfig, String loginUrl) {
        String baseUrl = firstText(sourceConfig, "baseUrl");
        URI uri;
        if (loginUrl.startsWith("http://") || loginUrl.startsWith("https://")) {
            uri = URI.create(loginUrl);
        } else {
            uri = buildUri(baseUrl, loginUrl, Map.of());
        }
        return guardAuthUri(baseUrl, uri, safeMap(sourceConfig.get("requestPolicy")));
    }

    private Instant resolveTokenExpiresAt(JsonNode json, String expiresInPath, String token, Instant now) {
        if (StringUtils.hasText(expiresInPath)) {
            JsonNode expiresIn = jsonAt(json, expiresInPath);
            if (expiresIn.isNumber()) {
                return now.plusSeconds(Math.max(0L, expiresIn.asLong()));
            }
            if (expiresIn.isTextual() && StringUtils.hasText(expiresIn.asText())) {
                try {
                    return now.plusSeconds(Math.max(0L, Long.parseLong(expiresIn.asText().trim())));
                } catch (NumberFormatException ignored) {
                    // fall through to default TTL
                }
            }
        }
        Optional<Instant> jwtExpiresAt = jwtExpiresAt(token);
        if (jwtExpiresAt.isPresent()) {
            return jwtExpiresAt.get();
        }
        return now.plus(DEFAULT_JWT_TOKEN_TTL);
    }

    private Optional<Instant> jwtExpiresAt(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2 || !StringUtils.hasText(parts[1])) {
            return Optional.empty();
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(paddedBase64Url(parts[1]));
            JsonNode payload = OBJECT_MAPPER.readTree(payloadBytes);
            JsonNode exp = payload.path("exp");
            Long epochSeconds = null;
            if (exp.isNumber()) {
                epochSeconds = exp.asLong();
            } else if (exp.isTextual() && StringUtils.hasText(exp.asText())) {
                epochSeconds = Long.parseLong(exp.asText().trim());
            }
            return epochSeconds != null && epochSeconds > 0 ? Optional.of(Instant.ofEpochSecond(epochSeconds)) : Optional.empty();
        } catch (IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String paddedBase64Url(String value) {
        int padding = (4 - (value.length() % 4)) % 4;
        return padding == 0 ? value : value + "=".repeat(padding);
    }

    private JsonNode jsonAt(JsonNode root, String path) {
        if (root == null || !StringUtils.hasText(path)) {
            return MissingNode.getInstance();
        }
        String normalized = path.trim();
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        JsonNode cursor = root;
        for (String part : normalized.split("\\.")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            cursor = cursor.path(part);
        }
        return cursor;
    }

    private Map<String, Object> authQueryParameters(Map<String, Object> sourceConfig) {
        Map<String, Object> auth = normalizedAuth(sourceConfig);
        if (!"apiKey".equalsIgnoreCase(firstText(auth, "provider")) || !"query".equalsIgnoreCase(firstText(auth, "location"))) {
            return Map.of();
        }
        String name = firstText(auth, "name");
        String value = resolveSecret(safeMap(sourceConfig.get("secrets")), firstText(auth, "valueRef"), "value");
        if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
            throw new ApiHttpException("API_RUNTIME_AUTH", "API Key 配置不完整", null, 0);
        }
        return Map.of(name, value);
    }

    private String resolveSecret(Map<String, Object> secrets, String secretRef, String fallbackField) {
        if (StringUtils.hasText(secretRef)) {
            String direct = text(secrets.get(secretRef));
            if (StringUtils.hasText(direct)) {
                return direct;
            }
        }
        return text(secrets.get(fallbackField));
    }

    private byte[] readBody(InputStream inputStream, int maxResponseBytes, int attempts) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxResponseBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxResponseBytes) {
                    throw new ApiHttpException(
                        "API_RUNTIME_RESPONSE_TOO_LARGE",
                        "API 响应超过最大字节限制: " + maxResponseBytes,
                        null,
                        attempts
                    );
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void sleepBeforeRetry(Integer status, Optional<String> retryAfter, int attempts, Map<String, Object> retryPolicy)
        throws InterruptedException {
        Duration duration = status != null && status == 429
            ? parseRetryAfter(retryAfter).orElse(backoff(attempts, retryPolicy))
            : backoff(attempts, retryPolicy);
        sleeper.sleep(duration);
    }

    private Duration backoff(int attempts, Map<String, Object> retryPolicy) {
        long baseMillis = durationMillis(retryPolicy.get("baseBackoffMillis"), apiProperties.getRetry().getBaseBackoff());
        long maxMillis = durationMillis(
            firstPresent(retryPolicy, "maxBackoffMillis", "backoffCapMillis", "backoffCap"),
            apiProperties.getRetry().getBackoffCap()
        );
        long multiplier = Math.max(1L, 1L << Math.max(0, attempts - 1));
        return Duration.ofMillis(Math.min(maxMillis, baseMillis * multiplier));
    }

    private Optional<Duration> parseRetryAfter(Optional<String> retryAfter) {
        if (retryAfter.isEmpty() || !StringUtils.hasText(retryAfter.get())) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(retryAfter.get().trim());
            return Optional.of(Duration.ofSeconds(Math.max(0L, seconds)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private void applyRateLimit(String resourceId, Map<String, Object> rateLimit) throws InterruptedException {
        int requestsPerSecond = nonNegativeInt(rateLimit.get("requestsPerSecond"), apiProperties.getRateLimit().getDefaultRps());
        if (requestsPerSecond <= 0) {
            return;
        }
        int defaultBurst = Math.max(1, requestsPerSecond * apiProperties.getRateLimit().getBurstMultiplier());
        int burst = positiveInt(rateLimit.get("burst"), defaultBurst);
        String gateKey = resourceId + "|" + requestsPerSecond + "|" + burst;
        RateGate gate = rateGates.computeIfAbsent(gateKey, ignored -> new RateGate(requestsPerSecond, burst));
        Duration wait = gate.reserve();
        if (!wait.isZero() && !wait.isNegative()) {
            sleeper.sleep(wait);
        }
    }

    private Semaphore concurrencyGate(String resourceId, Map<String, Object> rateLimit) {
        int maxConcurrency = positiveInt(rateLimit.get("maxConcurrency"), apiProperties.getRateLimit().getMaxConcurrency());
        return concurrencyGates.computeIfAbsent(resourceId + "|" + maxConcurrency, ignored -> new Semaphore(maxConcurrency));
    }

    private URI buildUri(String baseUrl, String path, Map<String, Object> query) {
        String safeBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String safePath = StringUtils.hasText(path) ? path.trim() : "/";
        if (!safePath.startsWith("/")) {
            safePath = "/" + safePath;
        }
        StringBuilder uri = new StringBuilder(safeBase).append(safePath);
        if (query != null && !query.isEmpty()) {
            List<String> pairs = new ArrayList<>();
            query.forEach((key, value) -> {
                if (StringUtils.hasText(key) && value != null) {
                    pairs.add(urlEncode(key) + "=" + urlEncode(String.valueOf(value)));
                }
            });
            if (!pairs.isEmpty()) {
                uri.append(uri.indexOf("?") >= 0 ? "&" : "?").append(String.join("&", pairs));
            }
        }
        return URI.create(uri.toString());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean shouldRetry(int status) {
        return status == 429 || status >= 500;
    }

    private boolean isRedirect(int status) {
        return status >= 300 && status < 400;
    }

    private ApiHttpException statusException(int status, int attempts) {
        if (status == 401 || status == 403) {
            return new ApiHttpException("API_RUNTIME_AUTH", "API 鉴权失败: HTTP " + status, status, attempts);
        }
        if (status == 429) {
            return new ApiHttpException("API_RUNTIME_RATE_LIMIT", "API 触发限流: HTTP 429", status, attempts);
        }
        if (status >= 400 && status < 500) {
            return new ApiHttpException("API_RUNTIME_CLIENT", "API 客户端错误: HTTP " + status, status, attempts);
        }
        return new ApiHttpException("API_RUNTIME_SERVER", "API 服务端错误: HTTP " + status, status, attempts);
    }

    private List<Map<String, Object>> extractResources(Map<String, Object> sourceConfig) {
        Object raw = sourceConfig.get("resources");
        List<Map<String, Object>> resources = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> resource = safeMap(item);
                if (!resource.isEmpty()) {
                    resources.add(resource);
                }
            }
        }
        if (!resources.isEmpty()) {
            return resources;
        }
        Map<String, Object> single = safeMap(sourceConfig.get("resource"));
        if (!single.isEmpty()) {
            return List.of(single);
        }
        if (StringUtils.hasText(firstText(sourceConfig, "path"))) {
            return List.of(sourceConfig);
        }
        return List.of();
    }

    private Map<String, Object> mergedPolicy(Map<String, Object> sourceConfig, Map<String, Object> resource, String key) {
        Map<String, Object> merged = new LinkedHashMap<>(safeMap(sourceConfig.get(key)));
        merged.putAll(safeMap(resource.get(key)));
        return merged;
    }

    private void validateUri(URI uri, Map<String, Object> requestPolicy) {
        if (uri == null || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API URL 无效", null, 0);
        }
        Map<String, Object> policy = safeMap(requestPolicy);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API URL 仅允许 http/https", null, 0);
        }
        boolean allowHttp = bool(policy.get("allowHttp"), apiProperties.isAllowHttp());
        if ("http".equals(scheme) && !allowHttp) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API 明文 HTTP 默认禁止，请配置 allow-http", null, 0);
        }
        List<String> allowedHosts = allowedHosts(policy.get("allowedHosts"));
        if (allowedHosts.isEmpty()) {
            allowedHosts = apiProperties.getAllowedHosts();
        }
        if (!allowedHosts.isEmpty() && !hostAllowed(uri.getHost(), allowedHosts)) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API host 不在允许列表: " + uri.getHost(), null, 0);
        }
    }

    ResolvedTarget resolveTarget(String host) {
        if (!StringUtils.hasText(host)) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API host 无效", null, 0);
        }
        final InetAddress[] addresses;
        try {
            addresses = hostAddressResolver.resolve(host);
        } catch (IOException ex) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API host DNS 解析失败", null, 0, ex);
        }
        if (addresses == null || addresses.length == 0) {
            throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API host DNS 未返回地址", null, 0);
        }
        if (!allowNonGlobalTargetsForTests) {
            for (InetAddress address : addresses) {
                if (!isGlobalUnicast(address)) {
                    throw new ApiHttpException("API_RUNTIME_BLOCKED_URL", "API host 解析到非公网单播地址", null, 0);
                }
            }
        }
        return new ResolvedTarget(host, addresses);
    }

    private List<String> allowedHosts(Object raw) {
        List<String> hosts = new ArrayList<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String host = text(item);
                if (StringUtils.hasText(host)) {
                    hosts.add(host.toLowerCase(Locale.ROOT));
                }
            }
        } else {
            String text = text(raw);
            if (StringUtils.hasText(text)) {
                for (String item : text.split(",")) {
                    String host = item.trim();
                    if (StringUtils.hasText(host)) {
                        hosts.add(host.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return List.copyOf(hosts);
    }

    private boolean hostAllowed(String host, List<String> allowedHosts) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String allowedHost : allowedHosts) {
            if (!StringUtils.hasText(allowedHost)) {
                continue;
            }
            String allowed = allowedHost.trim().toLowerCase(Locale.ROOT);
            if (allowed.equals(normalized)) {
                return true;
            }
            if (allowed.startsWith("*.") && normalized.endsWith(allowed.substring(1))) {
                return true;
            }
        }
        return false;
    }

    private boolean retryAllowed(
        String method,
        Map<String, Object> sourceConfig,
        Map<String, Object> resource,
        Map<String, Object> retryPolicy
    ) {
        String normalized = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "GET";
        if ("GET".equals(normalized) || "HEAD".equals(normalized)) {
            return true;
        }
        if ("PUT".equals(normalized) || "DELETE".equals(normalized)) {
            return bool(retryPolicy.get("idempotent"), false) || bool(resource.get("idempotent"), false);
        }
        if ("POST".equals(normalized) || "PATCH".equals(normalized)) {
            Map<String, Object> headers = new LinkedHashMap<>(safeMap(sourceConfig.get("defaultHeaders")));
            headers.putAll(safeMap(resource.get("headers")));
            return headers.entrySet().stream()
                .filter(entry -> "Idempotency-Key".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(this::text)
                .anyMatch(StringUtils::hasText);
        }
        return false;
    }

    private boolean isGlobalUnicast(InetAddress address) {
        if (address == null
            || address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            if (first == 0 || first == 10 || first == 127 || first >= 224) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 169 && second == 254) {
                return false;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return false;
            }
            if (first == 192
                && ((second == 0 && third == 0)
                    || (second == 0 && third == 2)
                    || (second == 88 && third == 99)
                    || second == 168)) {
                return false;
            }
            if (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100))) {
                return false;
            }
            return !(first == 203 && second == 0 && third == 113);
        }
        if (bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            // fc00::/7 is unique-local, never a valid outbound API target.
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
            // Only IPv6 global-unicast 2000::/3 is eligible. Exclude the
            // documentation-only 2001:db8::/32 range as well.
            if ((first & 0xe0) != 0x20) {
                return false;
            }
            return !(first == 0x20
                && second == 0x01
                && Byte.toUnsignedInt(bytes[2]) == 0x0d
                && Byte.toUnsignedInt(bytes[3]) == 0xb8);
        }
        return false;
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private Map<String, Object> safeMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (key != null) {
                result.put(String.valueOf(key), item);
            }
        });
        return result;
    }

    private String firstText(Map<String, Object> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            String value = text(map.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean bool(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int positiveInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : defaultValue;
        }
        if (value != null) {
            try {
                int parsed = Integer.parseInt(String.valueOf(value));
                return parsed > 0 ? parsed : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int nonNegativeInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue() >= 0 ? number.intValue() : defaultValue;
        }
        if (value != null) {
            try {
                int parsed = Integer.parseInt(String.valueOf(value));
                return parsed >= 0 ? parsed : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private long durationMillis(Object value, Duration defaultValue) {
        long defaultMillis = defaultValue == null ? 1L : Math.max(1L, defaultValue.toMillis());
        if (value instanceof Number number) {
            return number.longValue() > 0 ? number.longValue() : defaultMillis;
        }
        if (value != null) {
            String text = String.valueOf(value).trim();
            try {
                long parsed = Long.parseLong(text);
                return parsed > 0 ? parsed : defaultMillis;
            } catch (NumberFormatException ignored) {
                try {
                    Duration parsed = Duration.parse(text);
                    return parsed.toMillis() > 0 ? parsed.toMillis() : defaultMillis;
                } catch (RuntimeException ignoredDuration) {
                    return defaultMillis;
                }
            }
        }
        return defaultMillis;
    }

    private int positiveBytes(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : defaultValue;
        }
        if (value != null) {
            String text = String.valueOf(value).trim();
            try {
                int parsed = Integer.parseInt(text);
                return parsed > 0 ? parsed : defaultValue;
            } catch (NumberFormatException ignored) {
                try {
                    long bytes = DataSize.parse(text).toBytes();
                    return bytes > 0 && bytes <= Integer.MAX_VALUE ? (int) bytes : defaultValue;
                } catch (RuntimeException ignoredDataSize) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private String normalizeResourceId(String value) {
        if (!StringUtils.hasText(value)) {
            return "api_resource";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "api_resource";
    }

    private static final class RateGate {
        private final int requestsPerSecond;
        private final int burst;
        private double tokens;
        private long lastNanos;

        private RateGate(int requestsPerSecond, int burst) {
            this.requestsPerSecond = Math.max(1, requestsPerSecond);
            this.burst = Math.max(1, burst);
            this.tokens = this.burst;
            this.lastNanos = System.nanoTime();
        }

        private synchronized Duration reserve() {
            long now = System.nanoTime();
            if (now > lastNanos) {
                double refill = ((now - lastNanos) / 1_000_000_000D) * requestsPerSecond;
                tokens = Math.min(burst, tokens + refill);
                lastNanos = now;
            }
            if (tokens >= 1D) {
                tokens -= 1D;
                return Duration.ZERO;
            }
            double missing = 1D - tokens;
            long waitNanos = (long) Math.ceil((missing / requestsPerSecond) * 1_000_000_000D);
            tokens = 0D;
            lastNanos = now + waitNanos;
            return Duration.ofNanos(waitNanos);
        }
    }

    private record CachedToken(String token, Instant expiresAt) {}

    private record NextPage(URI uri, boolean explicitNextDriven) {}
}
