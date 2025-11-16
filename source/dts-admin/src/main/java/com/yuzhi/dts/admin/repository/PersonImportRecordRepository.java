package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.PersonImportRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonImportRecordRepository extends JpaRepository<PersonImportRecord, Long> {
    List<PersonImportRecord> findByBatchIdOrderByIdAsc(Long batchId);
}
