package com.yuzhi.dts.platform.repository.visualization;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiReportLinkRepository extends JpaRepository<BiReportLink, UUID> {
    List<BiReportLink> findByEnabledTrueOrderBySortOrderAscLastModifiedDateDesc();

    Optional<BiReportLink> findFirstByCodeIgnoreCase(String code);
}

