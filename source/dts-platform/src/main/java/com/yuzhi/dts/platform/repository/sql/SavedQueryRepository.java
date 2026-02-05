package com.yuzhi.dts.platform.repository.sql;

import com.yuzhi.dts.platform.domain.sql.SavedQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQuery, UUID> {

    List<SavedQuery> findByCreatedByOrderByUpdatedAtDesc(String createdBy);

    List<SavedQuery> findAllByOrderByUpdatedAtDesc();
}
