package com.yuzhi.dts.platform.repository.explore;

import com.yuzhi.dts.platform.domain.explore.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResultSetRepository extends JpaRepository<ResultSet, UUID> {
    List<ResultSet> findByExpiresAtBefore(Instant cutOff);

    List<ResultSet> findByCreatedByOrderByCreatedDateDesc(String createdBy);
}
