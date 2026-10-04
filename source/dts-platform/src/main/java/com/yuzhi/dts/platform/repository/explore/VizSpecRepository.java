package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.VizSpec;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VizSpecRepository extends JpaRepository<VizSpec, UUID> {}

