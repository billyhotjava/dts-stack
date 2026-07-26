package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * dbt materializations use the same guarded PostgreSQL physical executor, but require an explicit
 * dbt-materialization ownership tag in addition to dts-managed-copy.
 */
@Component
@Order(5)
public class ManagedDbtCopyDestructionAdapter implements CatalogManagedCopyDestructionAdapter {

    private final ManagedPostgresCopyDestructionAdapter delegate;

    public ManagedDbtCopyDestructionAdapter(
        javax.sql.DataSource dataSource,
        @Value("${dts.lifecycle.destruction.postgres-enabled:false}") boolean enabled
    ) {
        this.delegate = new ManagedPostgresCopyDestructionAdapter(dataSource, enabled);
    }

    @Override
    public boolean supports(CatalogDataset dataset) {
        String tags = dataset == null || dataset.getTags() == null
            ? ""
            : dataset.getTags().toLowerCase(Locale.ROOT);
        return tags.contains("dts-managed-copy") && tags.contains("dbt-materialization");
    }

    @Override
    public DestructionResult destroy(CatalogDataset dataset, String actionRef) {
        DestructionResult result = delegate.destroy(dataset, actionRef);
        if (!result.completed()) {
            return DestructionResult.blocked("DTS_DBT_MATERIALIZATION", result.failureMessage());
        }
        return new DestructionResult(
            true,
            "DTS_DBT_MATERIALIZATION",
            result.destroyedObjects(),
            false,
            result.evidence(),
            null
        );
    }
}
