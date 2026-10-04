package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.SvcDataProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SvcDataProductRepository extends JpaRepository<SvcDataProduct, UUID> {
    Optional<SvcDataProduct> findByCode(String code);
    List<SvcDataProduct> findByProductTypeIgnoreCase(String productType);
}
