package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AuditResourceDictionary;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditResourceDictionaryRepository extends JpaRepository<AuditResourceDictionary, Long> {
    List<AuditResourceDictionary> findAllByEnabledTrueOrderByOrderValueAscResourceKeyAsc();
}
