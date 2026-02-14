package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenAcl;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenAclRepository extends JpaRepository<AnalyticsScreenAcl, Long> {

    List<AnalyticsScreenAcl> findAllByScreenIdOrderByIdAsc(Long screenId);

    boolean existsByScreenIdAndSubjectTypeAndSubjectIdAndPerm(
            Long screenId, String subjectType, String subjectId, String perm);

    void deleteAllByScreenId(Long screenId);
}
