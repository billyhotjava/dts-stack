package com.yuzhi.dts.platform.repository.development;

import com.yuzhi.dts.platform.domain.development.DevScriptVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DevScriptVersionRepository extends JpaRepository<DevScriptVersion, UUID> {
    List<DevScriptVersion> findByAsset_IdOrderByVersionNoDesc(UUID assetId);

    Optional<DevScriptVersion> findTopByAsset_IdOrderByVersionNoDesc(UUID assetId);

    Optional<DevScriptVersion> findByAsset_IdAndVersionNo(UUID assetId, Integer versionNo);
}
