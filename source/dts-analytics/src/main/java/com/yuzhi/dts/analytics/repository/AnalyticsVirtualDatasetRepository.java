package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsVirtualDataset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsVirtualDatasetRepository extends JpaRepository<AnalyticsVirtualDataset, Long> {
    List<AnalyticsVirtualDataset> findAllByArchivedFalseOrderByUpdatedAtDescIdDesc();

    List<AnalyticsVirtualDataset> findAllByOwnerIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(Long ownerId);

    List<AnalyticsVirtualDataset> findAllByOwnerIdAndWorkspaceIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(Long ownerId, Long workspaceId);
}
