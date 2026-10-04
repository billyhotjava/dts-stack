package com.yuzhi.dts.platform.repository.governance;

import com.yuzhi.dts.platform.domain.governance.GovCleansingFunction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GovCleansingFunctionRepository extends JpaRepository<GovCleansingFunction, UUID> {
    List<GovCleansingFunction> findByEnabledTrueOrderByDisplayOrderAsc();
    Optional<GovCleansingFunction> findByCode(String code);
}
