package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovDimensionDictionaryRepository extends JpaRepository<GovDimensionDictionary, UUID> {
    Optional<GovDimensionDictionary> findFirstByCodeIgnoreCase(String code);
}

