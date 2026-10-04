package com.yuzhi.dts.platform.repository.workbench;

import com.yuzhi.dts.platform.domain.workbench.WorkbenchUserPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkbenchUserPreferenceRepository extends JpaRepository<WorkbenchUserPreference, UUID> {
    Optional<WorkbenchUserPreference> findByUsername(String username);
}
