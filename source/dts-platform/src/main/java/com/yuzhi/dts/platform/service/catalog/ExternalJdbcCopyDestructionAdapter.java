package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Protects externally owned JDBC source tables. Permanent destruction revokes the DTS catalog
 * registration and access references only; it never issues DROP against the source connection.
 */
@Component
@Order(100)
public class ExternalJdbcCopyDestructionAdapter implements CatalogManagedCopyDestructionAdapter {

    private static final List<String> JDBC_TYPES = List.of(
        "jdbc",
        "postgresql",
        "postgres",
        "mysql",
        "oracle",
        "dameng",
        "dm",
        "inceptor"
    );

    @Override
    public boolean supports(CatalogDataset dataset) {
        String type = dataset == null || dataset.getType() == null
            ? ""
            : dataset.getType().trim().toLowerCase(Locale.ROOT);
        return JDBC_TYPES.contains(type) && !managed(dataset);
    }

    @Override
    public DestructionResult destroy(CatalogDataset dataset, String actionRef) {
        return new DestructionResult(
            true,
            "EXTERNAL_JDBC_DTS_REFERENCES_ONLY",
            List.of("catalog-registration:" + dataset.getId(), "dts-access-references:" + dataset.getId()),
            false,
            Map.of(
                "sourceTableProtected",
                true,
                "sourceId",
                String.valueOf(dataset.getSourceId()),
                "relation",
                String.valueOf(dataset.getHiveDatabase()) + "." + String.valueOf(dataset.getHiveTable()),
                "actionRef",
                actionRef
            ),
            null
        );
    }

    private boolean managed(CatalogDataset dataset) {
        return dataset.getTags() != null && dataset.getTags().toLowerCase(Locale.ROOT).contains("dts-managed-copy");
    }
}
