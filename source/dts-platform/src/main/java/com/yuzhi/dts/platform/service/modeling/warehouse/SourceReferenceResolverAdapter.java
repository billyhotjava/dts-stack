package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetAvailabilityReadPort;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort;
import com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort.SourceSnapshot;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves planning references without copying source-system metadata into WarehousePlan. */
@Component
public class SourceReferenceResolverAdapter implements SourceReferenceResolver {

    private static final String DEFAULT_DBT_PROJECT = "default";

    private final CatalogSourceReferenceReadPort catalogSources;
    private final InfraExternalExchangeFileRepository fileRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final DbtManifestService dbtManifestService;
    private final CatalogAssetAvailabilityReadPort availability;

    public SourceReferenceResolverAdapter(
        CatalogSourceReferenceReadPort catalogSources,
        InfraExternalExchangeFileRepository fileRepository,
        InfraDataSourceRepository dataSourceRepository,
        DbtManifestService dbtManifestService,
        CatalogAssetAvailabilityReadPort availability
    ) {
        this.catalogSources = catalogSources;
        this.fileRepository = fileRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dbtManifestService = dbtManifestService;
        this.availability = availability;
    }

    @Override
    public ResolvedSource resolve(SourceType sourceType, SourceLocator locator, AccessContext accessContext) {
        return resolve(sourceType, locator, accessContext, false);
    }

    @Override
    public ResolvedSource resolveForExecution(
        SourceType sourceType,
        SourceLocator locator,
        AccessContext accessContext
    ) {
        return resolve(sourceType, locator, accessContext, true);
    }

    private ResolvedSource resolve(
        SourceType sourceType,
        SourceLocator locator,
        AccessContext accessContext,
        boolean backgroundExecution
    ) {
        if (sourceType == null || locator == null) {
            return ResolvedSource.providerError();
        }
        try {
            return switch (sourceType) {
                case CATALOG_TABLE -> resolveCatalogTable(locator, accessContext, backgroundExecution);
                case EXCEL_FILE -> resolveExcelFile(locator, accessContext);
                case CONNECTION_TABLE -> resolveConnectionTable(locator, accessContext, backgroundExecution);
                case DBT_NODE -> resolveDbtNode(locator);
            };
        } catch (RuntimeException exception) {
            return ResolvedSource.providerError();
        }
    }

    @Override
    public java.util.List<com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort.SourceField> readFields(
        SourceType type, SourceLocator locator, AccessContext context, String expectedVersion) {
        if (type != SourceType.CATALOG_TABLE && type != SourceType.CONNECTION_TABLE) return java.util.List.of();
        ResolvedSource before = resolve(type, locator, context);
        if (before.status() != ResolutionStatus.AVAILABLE || !java.util.Objects.equals(expectedVersion, before.resolvedVersion())) return java.util.List.of();
        var fields = catalogSources.readFields(locator.assetId(), locator.connectionId(), locator.namespace(), locator.objectName(), actorDepartment(context));
        ResolvedSource after = resolve(type, locator, context);
        return after.status() == ResolutionStatus.AVAILABLE && java.util.Objects.equals(expectedVersion, after.resolvedVersion()) ? fields : java.util.List.of();
    }

    private ResolvedSource resolveCatalogTable(
        SourceLocator locator,
        AccessContext accessContext,
        boolean backgroundExecution
    ) {
        if (locator.assetId() == null) {
            return ResolvedSource.providerError();
        }
        return fromCatalog(
            backgroundExecution
                ? catalogSources.resolveTableForExecution(locator.assetId())
                : catalogSources.resolveTable(locator.assetId(), actorDepartment(accessContext)),
            null
        );
    }

    private ResolvedSource resolveExcelFile(SourceLocator locator, AccessContext accessContext) {
        if (locator.fileId() == null) {
            return ResolvedSource.providerError();
        }
        InfraExternalExchangeFile file = fileRepository.findById(locator.fileId()).orElse(null);
        if (file == null || !Boolean.TRUE.equals(file.getEnabled())) {
            return ResolvedSource.missing();
        }
        if (!departmentAllowed(file.getOwnerDept(), accessContext)) {
            return ResolvedSource.forbidden();
        }
        if (isBlank(file.getChecksum())) {
            return ResolvedSource.providerError();
        }
        return ResolvedSource.available(file.getFileName(), file.getChecksum().trim());
    }

    private ResolvedSource resolveConnectionTable(
        SourceLocator locator,
        AccessContext accessContext,
        boolean backgroundExecution
    ) {
        if (locator.connectionId() == null || isBlank(locator.namespace()) || isBlank(locator.objectName())) {
            return ResolvedSource.providerError();
        }
        InfraDataSource connection = dataSourceRepository.findById(locator.connectionId()).orElse(null);
        if (connection == null) {
            return ResolvedSource.missing();
        }
        if (!departmentAllowed(connection.getOwnerDept(), accessContext)) {
            return ResolvedSource.forbidden();
        }
        if (!"ACTIVE".equalsIgnoreCase(connection.getStatus()) || connection.getLastVerifiedAt() == null) {
            return ResolvedSource.providerError();
        }
        SourceSnapshot catalogSource = backgroundExecution
            ? catalogSources.resolveConnectionTableForExecution(
                locator.connectionId(),
                locator.namespace(),
                locator.objectName()
            )
            : catalogSources.resolveConnectionTable(
                locator.connectionId(),
                locator.namespace(),
                locator.objectName(),
                actorDepartment(accessContext)
            );
        String label = connection.getName() + " / " + locator.namespace() + "." + locator.objectName();
        return fromCatalog(catalogSource, label);
    }

    private ResolvedSource resolveDbtNode(SourceLocator locator) {
        if (!DEFAULT_DBT_PROJECT.equals(locator.projectKey()) || isBlank(locator.uniqueId())) {
            return ResolvedSource.providerError();
        }
        DbtManifestService.DbtModelResult result = dbtManifestService.listModels();
        if (result == null || !result.enabled() || !isBlank(result.message())) {
            return ResolvedSource.providerError();
        }
        DbtManifestService.DbtModelSummary model = result
            .models()
            .stream()
            .filter(candidate -> locator.uniqueId().equals(candidate.uniqueId()))
            .findFirst()
            .orElse(null);
        if (model == null) {
            return ResolvedSource.missing();
        }
        String artifact = String.join(
            "\u0000",
            safe(model.uniqueId()),
            safe(model.name()),
            safe(model.alias()),
            safe(model.database()),
            safe(model.schema()),
            safe(model.path())
        );
        return availableIfCurrent(
            CatalogAssetType.DBT_MODEL,
            CatalogAssetKey.dbtModel(model.uniqueId(), model.name()),
            model.name(),
            sha256(artifact)
        );
    }

    private ResolvedSource availableIfCurrent(
        CatalogAssetType assetType,
        String assetKey,
        String displayName,
        String baseVersion
    ) {
        CatalogAssetAvailabilityReadPort.Availability current = availability.read(assetType, assetKey);
        if (!current.isAvailable()) {
            return ResolvedSource.missing();
        }
        if (current.epoch() == 0L) {
            return ResolvedSource.available(displayName, baseVersion);
        }
        return ResolvedSource.available(
            displayName,
            sha256(baseVersion + "\u0000availability-epoch=" + current.epoch())
        );
    }

    private ResolvedSource fromCatalog(SourceSnapshot snapshot, String displayNameOverride) {
        return switch (snapshot.status()) {
            case AVAILABLE -> availableIfCurrent(
                snapshot.assetType(),
                snapshot.assetKey(),
                isBlank(displayNameOverride) ? snapshot.displayName() : displayNameOverride,
                snapshot.schemaFingerprint()
            );
            case MISSING -> ResolvedSource.missing();
            case FORBIDDEN -> ResolvedSource.forbidden();
            case PROVIDER_ERROR -> ResolvedSource.providerError();
        };
    }

    private static String actorDepartment(AccessContext accessContext) {
        return accessContext == null ? null : accessContext.actorDepartmentId();
    }

    private static boolean departmentAllowed(String ownerDepartmentId, AccessContext accessContext) {
        if (isBlank(ownerDepartmentId)) {
            return true;
        }
        if (accessContext == null) {
            return false;
        }
        String canonicalOwner = DepartmentUtils.normalize(ownerDepartmentId);
        String canonicalActor = DepartmentUtils.normalize(accessContext.actorDepartmentId());
        return !canonicalOwner.isEmpty() && canonicalOwner.equals(canonicalActor);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
