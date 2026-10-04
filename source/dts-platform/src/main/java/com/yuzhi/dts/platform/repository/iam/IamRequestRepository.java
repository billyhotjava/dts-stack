package com.yuzhi.dts.platform.repository.iam;

import com.yuzhi.dts.platform.domain.iam.IamRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IamRequestRepository extends JpaRepository<IamRequest, UUID> {}

