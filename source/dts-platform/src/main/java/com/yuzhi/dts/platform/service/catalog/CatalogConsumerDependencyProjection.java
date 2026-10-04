package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class CatalogConsumerDependencyProjection implements CatalogClassificationProjection {

    private final CatalogConsumerClassificationService consumerClassificationService;

    public CatalogConsumerDependencyProjection(
        @Lazy CatalogConsumerClassificationService consumerClassificationService
    ) {
        this.consumerClassificationService = consumerClassificationService;
    }

    @Override
    public boolean supports(CatalogClassificationSnapshot snapshot) {
        return snapshot != null;
    }

    @Override
    public void project(CatalogClassificationSnapshot snapshot) {
        consumerClassificationService.onUpstreamChanged(snapshot);
    }
}
