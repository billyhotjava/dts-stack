package com.yuzhi.dts.analytics.service;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ScreenOwnershipService {

	private static final Logger LOG = LoggerFactory.getLogger(ScreenOwnershipService.class);
	private static final String SERVICE_HEADER = "X-DTS-Service";
	private static final String DEFAULT_BASE_URL = "http://dts-platform:8081";
	private static final String ASSET_TYPE = "SCREEN";

	private final RestTemplate restTemplate;
	private final String platformBaseUrl;
	private final String serviceName;

	public ScreenOwnershipService(
		RestTemplateBuilder builder,
		@Value("${dts.analytics.platform.base-url:}") String platformBaseUrl,
		@Value("${dts.analytics.platform.service-name:dts-analytics}") String serviceName,
		@Value("${dts.analytics.platform-ownership.connect-timeout-ms:2000}") long connectTimeoutMs,
		@Value("${dts.analytics.platform-ownership.read-timeout-ms:5000}") long readTimeoutMs
	) {
		this.restTemplate = builder
			.setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
			.setReadTimeout(Duration.ofMillis(readTimeoutMs))
			.build();
		this.platformBaseUrl = StringUtils.hasText(platformBaseUrl)
			? platformBaseUrl.trim() : DEFAULT_BASE_URL;
		this.serviceName = StringUtils.hasText(serviceName)
			? serviceName.trim() : "dts-analytics";
	}

	/**
	 * Register ownership for a screen asset on the platform side.
	 * POST /api/asset-ownership creates or upserts the ownership record.
	 * Failures are logged but do not propagate — the screen remains functional
	 * without an ownership record (dept-scoping is simply unavailable).
	 */
	public void registerOwnership(Long screenId, String ownerUsername, String ownerDeptCode) {
		if (screenId == null) {
			LOG.warn("registerOwnership called with null screenId, skipping");
			return;
		}

		try {
			URI uri = buildUri("/api/asset-ownership");

			Map<String, Object> body = new LinkedHashMap<>();
			body.put("assetType", ASSET_TYPE);
			body.put("assetId", String.valueOf(screenId));
			body.put("ownerDeptCode", ownerDeptCode != null ? ownerDeptCode : "");
			body.put("assignedBy", ownerUsername != null ? ownerUsername : "SYSTEM");

			restTemplate.exchange(
				uri, HttpMethod.POST,
				new HttpEntity<>(body, buildHeaders()),
				Map.class
			);

			LOG.info("Registered ownership for screen {} owner={} dept={}", screenId, ownerUsername, ownerDeptCode);
		} catch (Exception ex) {
			LOG.warn("Failed to register ownership for screen {} owner={}: {}",
				screenId, ownerUsername, ex.getMessage());
		}
	}

	/**
	 * Remove the ownership record for a screen asset.
	 * Uses DELETE with query parameters. Failures are logged but not propagated.
	 */
	public void removeOwnership(Long screenId) {
		if (screenId == null) {
			LOG.warn("removeOwnership called with null screenId, skipping");
			return;
		}

		String assetId = String.valueOf(screenId);

		// 1. Remove all grants for this screen
		try {
			URI grantsUri = UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
				.path("/api/asset-grants/by-asset")
				.queryParam("assetType", ASSET_TYPE)
				.queryParam("assetId", assetId)
				.build(true)
				.toUri();

			restTemplate.exchange(
				grantsUri, HttpMethod.DELETE,
				new HttpEntity<>(buildHeaders()),
				Map.class
			);

			LOG.info("Removed grants for screen {}", screenId);
		} catch (Exception ex) {
			LOG.warn("Failed to remove grants for screen {}: {}", screenId, ex.getMessage());
		}

		// 2. Remove ownership record
		try {
			URI ownershipUri = UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
				.path("/api/asset-ownership")
				.queryParam("assetType", ASSET_TYPE)
				.queryParam("assetId", assetId)
				.build(true)
				.toUri();

			restTemplate.exchange(
				ownershipUri, HttpMethod.DELETE,
				new HttpEntity<>(buildHeaders()),
				Void.class
			);

			LOG.info("Removed ownership for screen {}", screenId);
		} catch (Exception ex) {
			LOG.warn("Failed to remove ownership for screen {}: {}", screenId, ex.getMessage());
		}
	}

	/**
	 * Transfer ownership of a screen to a new owner / department.
	 * Placeholder for future implementation — logs intent but performs no action.
	 */
	public void transferOwnership(Long screenId, String newOwnerUsername, String newDeptCode) {
		LOG.info("transferOwnership not yet implemented for screen {} -> owner={} dept={}",
			screenId, newOwnerUsername, newDeptCode);
	}

	/**
	 * List all grants for a screen asset.
	 * Proxies to GET {platformBaseUrl}/api/asset-grants?assetType=SCREEN&assetId={screenId}
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> listGrants(Long screenId) {
		URI uri = UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
			.path("/api/asset-grants")
			.queryParam("assetType", ASSET_TYPE)
			.queryParam("assetId", String.valueOf(screenId))
			.build().toUri();

		// Platform returns List<AssetGrant> directly (JSON array), so deserialize as List
		var response = restTemplate.exchange(uri, HttpMethod.GET,
			new HttpEntity<>(buildHeaders()), List.class);

		Object body = response.getBody();
		if (body instanceof List<?> list) {
			return list.stream()
				.filter(o -> o instanceof Map)
				.map(o -> (Map<String, Object>) o)
				.toList();
		}
		return List.of();
	}

	/**
	 * Create a grant for a screen asset.
	 * Proxies to POST {platformBaseUrl}/api/asset-grants
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createGrant(Long screenId, String granteeType, String granteeId,
											String permission, String grantedBy) {
		URI uri = UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
			.path("/api/asset-grants")
			.build().toUri();

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("assetType", ASSET_TYPE);
		body.put("assetId", String.valueOf(screenId));
		body.put("granteeType", granteeType);
		body.put("granteeId", granteeId);
		body.put("permission", permission);
		body.put("grantedBy", grantedBy);

		var response = restTemplate.exchange(uri, HttpMethod.POST,
			new HttpEntity<>(body, buildHeaders()), Map.class);

		Map<String, Object> result = response.getBody();
		return result != null ? new LinkedHashMap<>(result) : Map.of();
	}

	/**
	 * Revoke (delete) a specific grant by its ID.
	 * Proxies to DELETE {platformBaseUrl}/api/asset-grants/{grantId}
	 */
	public void revokeGrant(Long grantId) {
		URI uri = UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
			.path("/api/asset-grants/" + grantId)
			.build().toUri();

		restTemplate.exchange(uri, HttpMethod.DELETE,
			new HttpEntity<>(buildHeaders()), Map.class);
	}

	private URI buildUri(String path) {
		return UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
			.path(path)
			.build(true)
			.toUri();
	}

	private HttpHeaders buildHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(SERVICE_HEADER, serviceName);
		return headers;
	}
}
