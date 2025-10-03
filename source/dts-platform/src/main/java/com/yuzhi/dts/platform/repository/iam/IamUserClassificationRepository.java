package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamUserClassification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamUserClassificationRepository extends JpaRepository<IamUserClassification, UUID> {
    List<IamUserClassification> findTop20ByUsernameIgnoreCaseContainingOrDisplayNameIgnoreCaseContaining(String username, String displayName);
}
