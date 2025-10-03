package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.SvcApi;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SvcApiRepository extends JpaRepository<SvcApi, UUID> {
    Optional<SvcApi> findByCode(String code);
    List<SvcApi> findByStatusIgnoreCase(String status);
}
