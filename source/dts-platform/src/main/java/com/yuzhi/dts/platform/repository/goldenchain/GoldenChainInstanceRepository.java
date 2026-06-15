package com.yuzhi.dts.platform.repository.goldenchain;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainInstance;
import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GoldenChainInstanceRepository extends JpaRepository<GoldenChainInstance, UUID> {
    List<GoldenChainInstance> findByEnabledTrueOrderByLastModifiedDateDesc();

    Optional<GoldenChainInstance> findFirstByChainKey(String chainKey);

    Optional<GoldenChainInstance> findFirstByChainKeyAndEnabledTrue(String chainKey);

    Optional<GoldenChainInstance> findFirstBySourceKindAndSourceRefTypeAndSourceRefId(
        GoldenChainSourceKind sourceKind,
        String sourceRefType,
        String sourceRefId
    );
}
