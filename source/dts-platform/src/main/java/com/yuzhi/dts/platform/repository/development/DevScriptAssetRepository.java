package com.yuzhi.dts.platform.repository.development;

import com.yuzhi.dts.platform.domain.development.DevScriptAsset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DevScriptAssetRepository extends JpaRepository<DevScriptAsset, UUID> {
    List<DevScriptAsset> findByEnabledTrueOrderByLastModifiedDateDesc();
}
