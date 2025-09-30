package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.SvcToken;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SvcTokenRepository extends JpaRepository<SvcToken, UUID> {
    List<SvcToken> findByCreatedBy(String createdBy);
}

