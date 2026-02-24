package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovReferenceImportRunRepository extends JpaRepository<GovReferenceImportRun, UUID> {
    List<GovReferenceImportRun> findByCodeTypeIdOrderByCreatedDateDesc(String codeTypeId);

    List<GovReferenceImportRun> findByCodeTypeIdInAndCreatedDateGreaterThanEqualOrderByCreatedDateDesc(
        List<String> codeTypeIds,
        Instant createdDate
    );
}
