package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.PersonImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonImportBatchRepository extends JpaRepository<PersonImportBatch, Long> {
    Page<PersonImportBatch> findAllByOrderByIdDesc(Pageable pageable);
}
