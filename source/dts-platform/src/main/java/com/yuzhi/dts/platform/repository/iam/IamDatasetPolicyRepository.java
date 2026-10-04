package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamDatasetPolicyRepository extends JpaRepository<IamDatasetPolicy, UUID> {
    List<IamDatasetPolicy> findByDatasetId(UUID datasetId);
    List<IamDatasetPolicy> findBySubjectTypeIgnoreCaseAndSubjectId(String subjectType, String subjectId);
    List<IamDatasetPolicy> findByDatasetIdAndSubjectTypeIgnoreCaseAndSubjectId(UUID datasetId, String subjectType, String subjectId);
}
