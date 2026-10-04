package com.yuzhi.dts.analytics.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PlatformAnalysisDatasetContractClient implements GovernedAnalysisDatasetContractProvider {

    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AnalyticsOutboundPlatformProperties outboundProps;
    private final Cache<String, GovernedAnalysisDatasetContract> cache = Caffeine.newBuilder()
        .maximumSize(2000)
        .expireAfterWrite(Duration.ofMinutes(5))
        .build();

    public PlatformAnalysisDatasetContractClient(
        RestTemplateBuilder builder,
        ObjectMapper objectMapper,
        AnalyticsOutboundPlatformProperties outboundProps
    ) {
        long timeout = Math.max(2, outboundProps.getTimeoutSeconds());
        this.restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(timeout))
            .setReadTimeout(Duration.ofSeconds(timeout))
            .build();
        this.objectMapper = objectMapper;
        this.outboundProps = outboundProps;
    }

    @Override
    public GovernedAnalysisDatasetContract get(UUID datasetId, int version, String checksum) {
        if (datasetId == null || version < 1 || !StringUtils.hasText(checksum)) {
            throw new AnalysisConflictException("ANALYSIS_DATASET_REF_INVALID", "dataset id, version and checksum are required");
        }
        String key = datasetId + ":" + version + ":" + checksum;
        GovernedAnalysisDatasetContract cached = cache.getIfPresent(key);
        if (cached != null) return cached;
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                uri(datasetId, version),
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class
            );
            Object data = response.getBody() == null ? null : response.getBody().get("data");
            if (data == null) {
                throw new AnalysisDependencyException("ANALYSIS_CONTRACT_EMPTY", "platform returned no dataset contract", null);
            }
            GovernedAnalysisDatasetContract contract = objectMapper.convertValue(data, GovernedAnalysisDatasetContract.class);
            if (
                !datasetId.equals(contract.datasetId()) ||
                version != contract.version() ||
                !checksum.equals(contract.contractChecksum()) ||
                !"PUBLISHED".equals(contract.status())
            ) {
                throw new AnalysisConflictException("ANALYSIS_CONTRACT_CONFLICT", "platform dataset contract no longer matches the pinned reference");
            }
            cache.put(key, contract);
            return contract;
        } catch (AnalysisConflictException | AnalysisDependencyException failure) {
            throw failure;
        } catch (HttpStatusCodeException failure) {
            if (failure.getStatusCode().value() == 404) {
                throw new AnalysisConflictException("ANALYSIS_DATASET_NOT_FOUND", "platform dataset version does not exist");
            }
            if (failure.getStatusCode().value() == 409) {
                throw new AnalysisConflictException("ANALYSIS_CONTRACT_CONFLICT", "platform dataset contract is not consumable");
            }
            throw new AnalysisDependencyException("ANALYSIS_CONTRACT_UNAVAILABLE", "platform dataset contract is unavailable", failure);
        } catch (Exception failure) {
            throw new AnalysisDependencyException("ANALYSIS_CONTRACT_UNAVAILABLE", "platform dataset contract is unavailable", failure);
        }
    }

    public void invalidate(UUID datasetId) {
        if (datasetId == null) return;
        String prefix = datasetId + ":";
        cache.asMap().keySet().removeIf(key -> key.startsWith(prefix));
    }

    private URI uri(UUID datasetId, int version) {
        String baseUrl = StringUtils.hasText(outboundProps.getBaseUrl())
            ? outboundProps.getBaseUrl().trim()
            : "http://dts-platform:8081";
        String apiPath = StringUtils.hasText(outboundProps.getApiPath()) ? outboundProps.getApiPath().trim() : "/api";
        return UriComponentsBuilder
            .fromHttpUrl(baseUrl)
            .path(apiPath)
            .path("/internal/analysis-datasets/")
            .path(datasetId.toString())
            .path("/versions/")
            .path(String.valueOf(version))
            .build(true)
            .toUri();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String serviceName = StringUtils.hasText(outboundProps.getServiceName())
            ? outboundProps.getServiceName().trim()
            : "dts-analytics";
        headers.set(SERVICE_HEADER, serviceName);
        if (StringUtils.hasText(outboundProps.getServiceToken())) {
            headers.set(SERVICE_TOKEN_HEADER, outboundProps.getServiceToken().trim());
        }
        return headers;
    }
}
