package com.yuzhi.dts.platform.service.catalog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CatalogExternalAssetIdentityRegistry {

    private static final int MAX_REGISTRATIONS = 500;
    private static final Pattern METRIC_KEY = Pattern.compile(
        "^tenant:([^/]+)/env:prod/dialect:generic/metric-pack:([^/]+)/metric:([^/]+)$"
    );
    private static final Pattern METRIC_PACK_KEY = Pattern.compile(
        "^tenant:([^/]+)/env:prod/dialect:generic/metric-pack:([^/]+)/version:([^/]+)$"
    );
    private static final String UPSERT_SQL =
        """
        insert into catalog_external_asset_identity (
            id,
            asset_type,
            canonical_asset_key,
            remote_asset_id,
            source_service,
            registration_scope,
            sync_run_id,
            owner_dept,
            active,
            created_by,
            created_date,
            last_modified_by,
            last_modified_date
        ) values (?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?)
        on conflict (asset_type, canonical_asset_key) do update
           set remote_asset_id = excluded.remote_asset_id,
               source_service = excluded.source_service,
               registration_scope = excluded.registration_scope,
               sync_run_id = excluded.sync_run_id,
               owner_dept = excluded.owner_dept,
               active = true,
               last_modified_by = excluded.last_modified_by,
               last_modified_date = excluded.last_modified_date
        """;
    private static final String DEACTIVATE_STALE_SQL =
        """
        update catalog_external_asset_identity
           set active = false,
               last_modified_by = ?,
               last_modified_date = ?
         where source_service = ?
           and registration_scope = ?
           and active = true
           and sync_run_id <> ?
        returning asset_type, canonical_asset_key, remote_asset_id
        """;

    private final JdbcTemplate jdbcTemplate;
    private final CodeAssetGrantWriter grantWriter;

    public CatalogExternalAssetIdentityRegistry(
        JdbcTemplate jdbcTemplate,
        CodeAssetGrantWriter grantWriter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.grantWriter = grantWriter;
    }

    @Transactional
    public RegistrationResult register(
        String sourceService,
        List<Registration> registrations
    ) {
        return register(
            sourceService,
            null,
            null,
            false,
            registrations
        );
    }

    @Transactional
    public RegistrationResult register(
        String sourceService,
        String registrationScope,
        UUID syncRunId,
        boolean complete,
        List<Registration> registrations
    ) {
        String source = requireText(
            sourceService,
            64,
            "source service is required"
        );
        String scope = optionalText(registrationScope, 512);
        if (scope != null) {
            scope = scope.toLowerCase(Locale.ROOT);
        }
        if ((scope == null) != (syncRunId == null)) {
            throw new IllegalArgumentException(
                "registration scope and sync run id must be provided together"
            );
        }
        if (complete && scope == null) {
            throw new IllegalArgumentException(
                "completed registration requires a synchronization scope"
            );
        }
        if (
            (registrations == null || registrations.isEmpty()) &&
            !complete
        ) {
            return new RegistrationResult(0, List.of());
        }
        List<Registration> safeRegistrations = registrations == null
            ? List.of()
            : registrations;
        if (safeRegistrations.size() > MAX_REGISTRATIONS) {
            throw new IllegalArgumentException(
                "at most 500 external asset identities can be registered"
            );
        }
        Map<String, NormalizedRegistration> unique = new LinkedHashMap<>();
        for (Registration registration : safeRegistrations) {
            NormalizedRegistration item = normalize(registration);
            if (
                scope != null &&
                !item.canonicalAssetKey().startsWith(scope + "/")
            ) {
                throw new IllegalArgumentException(
                    "external asset identity is outside the synchronization scope"
                );
            }
            String identityKey =
                item.type().name() + "\u0000" + item.canonicalAssetKey();
            NormalizedRegistration previous = unique.putIfAbsent(
                identityKey,
                item
            );
            if (previous != null && !previous.equals(item)) {
                throw new IllegalArgumentException(
                    "conflicting duplicate external asset identity"
                );
            }
        }
        List<NormalizedRegistration> normalized = List.copyOf(unique.values());
        Map<String, String> remoteIdentities = new LinkedHashMap<>();
        for (NormalizedRegistration item : normalized) {
            String remoteIdentityKey =
                item.type().name() + "\u0000" + item.remoteAssetId();
            String previousCanonicalKey = remoteIdentities.putIfAbsent(
                remoteIdentityKey,
                item.canonicalAssetKey()
            );
            if (
                previousCanonicalKey != null &&
                !previousCanonicalKey.equals(item.canonicalAssetKey())
            ) {
                throw new IllegalArgumentException(
                    "conflicting duplicate remote asset identity"
                );
            }
        }
        Instant now = Instant.now();
        List<Object[]> arguments = new ArrayList<>(normalized.size());
        for (NormalizedRegistration item : normalized) {
            arguments.add(
                new Object[] {
                    UUID.randomUUID(),
                    item.type().name(),
                    item.canonicalAssetKey(),
                    item.remoteAssetId(),
                    source,
                    scope,
                    syncRunId,
                    item.ownerDept(),
                    source,
                    Timestamp.from(now),
                    source,
                    Timestamp.from(now),
                }
            );
        }
        if (!arguments.isEmpty()) {
            jdbcTemplate.batchUpdate(UPSERT_SQL, arguments);
        }
        for (NormalizedRegistration item : normalized) {
            grantWriter.synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    item.type(),
                    item.canonicalAssetKey(),
                    item.remoteAssetId(),
                    source + ":" + item.remoteAssetId()
                ),
                item.ownerDept(),
                source,
                "INTERNAL",
                "ACTIVE"
            );
        }
        if (complete) {
            deactivateStaleIdentities(
                source,
                scope,
                syncRunId,
                now
            );
        }
        return new RegistrationResult(
            normalized.size(),
            normalized
                .stream()
                .map(NormalizedRegistration::canonicalAssetKey)
                .toList()
        );
    }

    private void deactivateStaleIdentities(
        String source,
        String scope,
        UUID syncRunId,
        Instant now
    ) {
        List<StaleIdentity> stale = jdbcTemplate.query(
            DEACTIVATE_STALE_SQL,
            (resultSet, rowNumber) ->
                new StaleIdentity(
                    CatalogAssetType.from(
                        resultSet.getString("asset_type")
                    ),
                    resultSet.getString("canonical_asset_key"),
                    resultSet.getString("remote_asset_id")
                ),
            source,
            Timestamp.from(now),
            source,
            scope,
            syncRunId
        );
        for (StaleIdentity item : stale) {
            grantWriter.synchronizeExternalCodeAsset(
                new CatalogAssetIdentity(
                    item.type(),
                    item.canonicalAssetKey(),
                    item.remoteAssetId(),
                    source + ":" + item.remoteAssetId()
                ),
                null,
                source,
                "INTERNAL",
                "INACTIVE"
            );
        }
    }

    private NormalizedRegistration normalize(Registration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration is required");
        }
        CatalogAssetType type = CatalogAssetType.from(registration.assetType());
        String key = requireText(
            registration.canonicalAssetKey(),
            512,
            "canonical asset key is required"
        ).toLowerCase(Locale.ROOT);
        String remoteAssetId = requireText(
            registration.remoteAssetId(),
            128,
            "remote asset id is required"
        );
        String canonical = switch (type) {
            case METRIC -> canonicalMetricKey(key);
            case METRIC_PACK -> canonicalMetricPackKey(key);
            default ->
                throw new IllegalArgumentException(
                    "external identity registration only supports METRIC and METRIC_PACK"
                );
        };
        if (!canonical.equals(key)) {
            throw new IllegalArgumentException(
                "canonical asset key did not round-trip"
            );
        }
        return new NormalizedRegistration(
            type,
            canonical,
            remoteAssetId,
            optionalText(registration.ownerDept(), 128)
        );
    }

    private String canonicalMetricKey(String key) {
        Matcher matcher = METRIC_KEY.matcher(key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid metric asset key");
        }
        return CatalogAssetKey.metric(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3)
        );
    }

    private String canonicalMetricPackKey(String key) {
        Matcher matcher = METRIC_PACK_KEY.matcher(key);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid metric pack asset key");
        }
        return CatalogAssetKey.metricPack(
            matcher.group(1),
            matcher.group(2),
            matcher.group(3)
        );
    }

    private String requireText(String value, int maxLength, String message) {
        String normalized = optionalText(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                "value exceeds " + maxLength + " characters"
            );
        }
        return normalized;
    }

    public record Registration(
        String assetType,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept
    ) {}

    public record RegistrationResult(
        int registered,
        List<String> canonicalAssetKeys
    ) {}

    private record NormalizedRegistration(
        CatalogAssetType type,
        String canonicalAssetKey,
        String remoteAssetId,
        String ownerDept
    ) {}

    private record StaleIdentity(
        CatalogAssetType type,
        String canonicalAssetKey,
        String remoteAssetId
    ) {}
}
