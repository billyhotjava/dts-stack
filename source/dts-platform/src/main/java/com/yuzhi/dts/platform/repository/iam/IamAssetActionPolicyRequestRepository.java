package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IamAssetActionPolicyRequestRepository
    extends JpaRepository<IamAssetActionPolicyRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from IamAssetActionPolicyRequest request where request.id = :id")
    Optional<IamAssetActionPolicyRequest> findForUpdate(@Param("id") UUID id);

    List<IamAssetActionPolicyRequest> findByStatusIgnoreCaseOrderByCreatedDateDesc(String status);

    Optional<IamAssetActionPolicyRequest> findFirstBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndStatusIgnoreCaseOrderByCreatedDateDesc(
        String subjectType,
        String subjectId,
        String resourceType,
        String resourceId,
        String status
    );

    boolean existsBySubjectTypeIgnoreCaseAndSubjectIdAndResourceTypeIgnoreCaseAndResourceIdAndStatusIgnoreCase(
        String subjectType,
        String subjectId,
        String resourceType,
        String resourceId,
        String status
    );
}
