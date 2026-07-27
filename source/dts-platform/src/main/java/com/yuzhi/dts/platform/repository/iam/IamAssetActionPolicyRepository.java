package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IamAssetActionPolicyRepository extends JpaRepository<IamAssetActionPolicy, UUID> {
    @Query(
        """
        select policy
          from IamAssetActionPolicy policy
         where upper(policy.resourceType) = upper(:resourceType)
           and policy.resourceId = :resourceId
           and upper(policy.action) = upper(:action)
           and (policy.validFrom is null or policy.validFrom <= :decisionTime)
           and (policy.validTo is null or policy.validTo >= :decisionTime)
        """
    )
    List<IamAssetActionPolicy> findEffective(
        @Param("resourceType") String resourceType,
        @Param("resourceId") String resourceId,
        @Param("action") String action,
        @Param("decisionTime") Instant decisionTime
    );

    List<IamAssetActionPolicy> findBySubjectTypeIgnoreCaseAndSubjectIdOrderByResourceTypeAscResourceIdAscActionAsc(
        String subjectType,
        String subjectId
    );

    Optional<IamAssetActionPolicy> findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndActionIgnoreCase(
        String subjectType,
        String subjectId,
        String resourceType,
        String resourceId,
        String action
    );

    List<IamAssetActionPolicy> findBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdOrderByActionAsc(
        String subjectType,
        String subjectId,
        String resourceType,
        String resourceId
    );
}
