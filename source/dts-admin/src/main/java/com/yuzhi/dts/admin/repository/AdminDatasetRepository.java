package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminDataset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminDatasetRepository extends JpaRepository<AdminDataset, Long> {
    Optional<AdminDataset> findByBusinessCode(String businessCode);
}

