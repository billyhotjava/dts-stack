package com.yuzhi.dts.copilot.analytics.repository;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyticsReportTemplateRepository extends JpaRepository<AnalyticsReportTemplate, Long> {

    List<AnalyticsReportTemplate> findAllByArchivedFalseOrderByUpdatedAtDesc(Pageable pageable);

    @Query("""
            select t
            from AnalyticsReportTemplate t
            where t.archived = false
              and t.published = true
              and lower(t.certificationStatus) = 'certified'
              and (:domain is null or lower(t.domain) = :domain)
              and (:category is null or lower(t.category) = :category)
            order by t.updatedAt desc
            """)
    List<AnalyticsReportTemplate> findCatalogTemplates(
            @Param("domain") String domain,
            @Param("category") String category,
            Pageable pageable);

    @Query("""
            select t
            from AnalyticsReportTemplate t
            where t.archived = false
              and t.published = true
              and lower(t.certificationStatus) = 'certified'
              and lower(t.templateCode) = :templateCode
            order by t.updatedAt desc
            """)
    List<AnalyticsReportTemplate> findRunnableTemplatesByTemplateCode(
            @Param("templateCode") String templateCode, Pageable pageable);

    default Optional<AnalyticsReportTemplate> findLatestRunnableTemplateByTemplateCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return Optional.empty();
        }
        return findRunnableTemplatesByTemplateCode(
                        templateCode.trim().toLowerCase(Locale.ROOT), PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }
}
