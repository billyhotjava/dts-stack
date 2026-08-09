package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import java.util.List;
import java.util.UUID;

/** Read-only projection contract for the model workbench table. */
public final class ModelWorkbenchCatalogContract {

    private ModelWorkbenchCatalogContract() {}

    public enum EntryKind {
        DIMENSION_DEFINITION,
        MODEL_SPEC,
    }

    public record CatalogEntry(
        EntryKind kind,
        UUID id,
        String name,
        String code,
        UUID planId,
        UUID domainId,
        String objectType,
        String layer,
        String status,
        int revision
    ) {}

    public record CatalogQuery(
        int page,
        int size,
        String query,
        UUID planId,
        UUID domainId,
        String objectType,
        Layer layer,
        String status
    ) {}

    public record CatalogPage(
        List<CatalogEntry> content,
        long totalElements,
        int page,
        int size,
        int totalPages
    ) {
        public CatalogPage {
            content = content == null ? List.of() : List.copyOf(content);
        }

        public static CatalogPage empty(int page, int size) {
            return new CatalogPage(List.of(), 0, page, size, 0);
        }
    }
}
