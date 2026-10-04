package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import com.yuzhi.dts.platform.domain.governance.GovDimensionItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovDimensionItemRepository extends JpaRepository<GovDimensionItem, UUID> {
    List<GovDimensionItem> findByDimensionOrderBySortOrderAscCreatedDateAsc(GovDimensionDictionary dimension);
    Optional<GovDimensionItem> findFirstByDimensionAndCodeIgnoreCase(GovDimensionDictionary dimension, String code);
}

