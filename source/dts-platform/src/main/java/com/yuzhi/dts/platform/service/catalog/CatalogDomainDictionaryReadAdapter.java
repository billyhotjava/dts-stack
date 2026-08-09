package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Catalog-owned JPA adapter; consumers only receive immutable dictionary projections. */
@Component
@Transactional(readOnly = true)
public class CatalogDomainDictionaryReadAdapter implements CatalogDomainDictionaryReadPort {

    private final CatalogDomainRepository repository;

    public CatalogDomainDictionaryReadAdapter(CatalogDomainRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCode(String code) {
        return code != null && !code.isBlank() && repository.existsByCodeIgnoreCase(code);
    }

    @Override
    public List<DomainRecord> findByCodeCandidates(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return repository.findByCodeLowerIn(codes).stream().map(CatalogDomainDictionaryReadAdapter::toRecord).toList();
    }

    private static DomainRecord toRecord(CatalogDomain domain) {
        Instant version = domain.getLastModifiedDate() != null
            ? domain.getLastModifiedDate()
            : domain.getCreatedDate();
        return new DomainRecord(
            domain.getId(),
            domain.getCode(),
            domain.getName(),
            domain.getOwner(),
            domain.getParent() == null ? null : domain.getParent().getId(),
            domain.getLifecycleStatus(),
            version
        );
    }
}
