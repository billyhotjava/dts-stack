package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.etl.DbtManifestService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves planning references without copying source-system metadata into WarehousePlan. */
@Component
public class SourceReferenceResolverAdapter implements SourceReferenceResolver {

    private static final String DEFAULT_DBT_PROJECT = "default";

    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final InfraExternalExchangeFileRepository fileRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final DbtManifestService dbtManifestService;
    private final AccessChecker accessChecker;

    public SourceReferenceResolverAdapter(
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogDatasetRepository datasetRepository,
        InfraExternalExchangeFileRepository fileRepository,
        InfraDataSourceRepository dataSourceRepository,
        DbtManifestService dbtManifestService,
        AccessChecker accessChecker
    ) {
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.datasetRepository = datasetRepository;
        this.fileRepository = fileRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dbtManifestService = dbtManifestService;
        this.accessChecker = accessChecker;
    }

    @Override
    public ResolvedSource resolve(SourceType sourceType, SourceLocator locator, AccessContext accessContext) {
        if (sourceType == null || locator == null) {
            return ResolvedSource.providerError();
        }
        try {
            return switch (sourceType) {
                case CATALOG_TABLE -> resolveCatalogTable(locator, accessContext);
                case EXCEL_FILE -> resolveExcelFile(locator, accessContext);
                case CONNECTION_TABLE -> resolveConnectionTable(locator, accessContext);
                case DBT_NODE -> resolveDbtNode(locator);
            };
        } catch (RuntimeException exception) {
            return ResolvedSource.providerError();
        }
    }

    private ResolvedSource resolveCatalogTable(SourceLocator locator, AccessContext accessContext) {
        if (locator.assetId() == null) {
            return ResolvedSource.providerError();
        }
        CatalogTableSchema table = tableRepository.findById(locator.assetId()).orElse(null);
        if (table == null) {
            return ResolvedSource.missing();
        }
        CatalogDataset dataset = table.getDataset();
        if (!canRead(dataset, accessContext)) {
            return ResolvedSource.forbidden();
        }
        return ResolvedSource.available(table.getName(), schemaFingerprint(table));
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

    private ResolvedSource resolveConnectionTable(SourceLocator locator, AccessContext accessContext) {
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
        CatalogDataset dataset = datasetRepository
            .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(
                locator.connectionId(),
                locator.namespace(),
                locator.objectName()
            )
            .orElse(null);
        if (dataset == null) {
            return ResolvedSource.providerError();
        }
        if (!canRead(dataset, accessContext)) {
            return ResolvedSource.forbidden();
        }
        List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
        CatalogTableSchema table = tables
            .stream()
            .filter(candidate -> locator.objectName().equalsIgnoreCase(candidate.getName()))
            .findFirst()
            .orElseGet(() -> tables.size() == 1 ? tables.getFirst() : null);
        if (table == null) {
            return ResolvedSource.providerError();
        }
        String label = connection.getName() + " / " + locator.namespace() + "." + locator.objectName();
        return ResolvedSource.available(label, schemaFingerprint(table));
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
        return ResolvedSource.available(model.name(), sha256(artifact));
    }

    private boolean canRead(CatalogDataset dataset, AccessContext accessContext) {
        return dataset != null &&
        accessChecker.canRead(dataset) &&
        accessChecker.departmentAllowedExact(dataset, accessContext == null ? null : accessContext.actorDepartmentId());
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

    private String schemaFingerprint(CatalogTableSchema table) {
        List<CatalogColumnSchema> columns = columnRepository
            .findByTable(table)
            .stream()
            .sorted(Comparator.comparing(CatalogColumnSchema::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (columns.isEmpty()) {
            throw new IllegalStateException("Catalog schema has no columns");
        }
        StringBuilder canonical = new StringBuilder(safe(table.getName()));
        for (CatalogColumnSchema column : columns) {
            canonical
                .append('\u0000')
                .append(safe(column.getName()))
                .append('\u0000')
                .append(safe(column.getDataType()))
                .append('\u0000')
                .append(Boolean.TRUE.equals(column.getNullable()))
                .append('\u0000')
                .append(safe(column.getStatus()));
        }
        return sha256(canonical.toString());
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
