package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSynonym;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsSynonymRepository extends JpaRepository<AnalyticsSynonym, Long> {

    List<AnalyticsSynonym> findByDatabaseIdOrDatabaseIdIsNull(Long databaseId);

    long deleteAllByDatabaseId(Long databaseId);
}
