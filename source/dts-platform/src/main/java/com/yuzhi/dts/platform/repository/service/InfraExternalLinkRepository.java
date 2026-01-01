package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.InfraExternalLink;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraExternalLinkRepository extends JpaRepository<InfraExternalLink, UUID> {
    Optional<InfraExternalLink> findByEntryKeyIgnoreCase(String entryKey);
}

