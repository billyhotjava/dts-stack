package com.yuzhi.dts.platform.repository.sql;

import com.yuzhi.dts.platform.domain.sql.SqlIdeTab;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SqlIdeTabRepository extends JpaRepository<SqlIdeTab, UUID> {
    List<SqlIdeTab> findByUserLoginOrderBySortOrderAsc(String userLogin);
    long countByUserLogin(String userLogin);
}
