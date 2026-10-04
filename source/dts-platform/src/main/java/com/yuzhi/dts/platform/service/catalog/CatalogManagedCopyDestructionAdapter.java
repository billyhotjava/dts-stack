package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.util.List;
import java.util.Map;

/**
 * Explicit physical-copy destruction boundary.
 *
 * <p>Adapters must only touch DTS-managed copies. External source adapters are required to report
 * {@code externalSourceTouched=false}; unsupported or ambiguous ownership must fail closed.
 */
public interface CatalogManagedCopyDestructionAdapter {
    boolean supports(CatalogDataset dataset);

    DestructionResult destroy(CatalogDataset dataset, String actionRef);

    record DestructionResult(
        boolean completed,
        String adapterCode,
        List<String> destroyedObjects,
        boolean externalSourceTouched,
        Map<String, Object> evidence,
        String failureMessage
    ) {
        public static DestructionResult blocked(String adapterCode, String message) {
            return new DestructionResult(false, adapterCode, List.of(), false, Map.of(), message);
        }
    }
}
