package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.SvcDataProductDataset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SvcDataProductDatasetRepository extends JpaRepository<SvcDataProductDataset, UUID> {
    List<SvcDataProductDataset> findByProductId(UUID productId);
}
