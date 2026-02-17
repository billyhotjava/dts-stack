package com.yuzhi.dts.platform.repository.development;

import com.yuzhi.dts.platform.domain.development.DevScriptRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DevScriptRunRepository extends JpaRepository<DevScriptRun, UUID> {
    List<DevScriptRun> findTop50ByAsset_IdOrderByCreatedDateDesc(UUID assetId);
}
