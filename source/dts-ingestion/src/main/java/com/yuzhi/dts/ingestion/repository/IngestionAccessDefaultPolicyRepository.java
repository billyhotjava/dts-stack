package com.yuzhi.dts.ingestion.repository;

import com.yuzhi.dts.ingestion.domain.IngestionAccessDefaultPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionAccessDefaultPolicyRepository extends JpaRepository<IngestionAccessDefaultPolicy, Long> {
    Optional<IngestionAccessDefaultPolicy> findFirstByPolicyKeyAndStatusOrderByVersionDesc(String policyKey, String status);
}
