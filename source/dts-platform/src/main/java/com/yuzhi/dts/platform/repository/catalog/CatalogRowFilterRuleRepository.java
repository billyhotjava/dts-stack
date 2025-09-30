package com.yuzhi.dts.platform.repository.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogRowFilterRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogRowFilterRuleRepository extends JpaRepository<CatalogRowFilterRule, UUID> {
    List<CatalogRowFilterRule> findByDataset(CatalogDataset dataset);
}

