package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/domains")
public class CatalogDomainIndicatorStatsResource {

    private final CatalogDomainRepository domainRepository;
    private final GovIndicatorDefinitionRepository indicatorRepository;

    public CatalogDomainIndicatorStatsResource(
        CatalogDomainRepository domainRepository,
        GovIndicatorDefinitionRepository indicatorRepository
    ) {
        this.domainRepository = domainRepository;
        this.indicatorRepository = indicatorRepository;
    }

    @GetMapping("/{id}/indicator-stats")
    public ApiResponse<Map<String, Object>> getIndicatorStats(@PathVariable UUID id) {
        String domainCode = domainRepository.findById(id)
            .map(CatalogDomain::getCode)
            .orElse(null);

        Map<String, Object> stats = new LinkedHashMap<>();
        if (domainCode == null) {
            stats.put("total", 0L);
            stats.put("published", 0L);
            stats.put("draft", 0L);
            return ApiResponses.ok(stats);
        }

        long total = indicatorRepository.countByDomainIgnoreCase(domainCode);
        long published = indicatorRepository.countByDomainIgnoreCaseAndStatusUpper(domainCode, "PUBLISHED");
        long draft = indicatorRepository.countByDomainIgnoreCaseAndStatusUpper(domainCode, "DRAFT");

        stats.put("total", total);
        stats.put("published", published);
        stats.put("draft", draft);
        return ApiResponses.ok(stats);
    }
}
