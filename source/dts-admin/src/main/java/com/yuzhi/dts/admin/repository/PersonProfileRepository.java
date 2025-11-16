package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.PersonProfile;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonProfileRepository extends JpaRepository<PersonProfile, Long> {
    Optional<PersonProfile> findByPersonCodeIgnoreCase(String personCode);

    Optional<PersonProfile> findByExternalId(String externalId);

    Optional<PersonProfile> findByAccountIgnoreCase(String account);

    Optional<PersonProfile> findByNationalId(String nationalId);

    Page<PersonProfile> findByDeptCodeIgnoreCase(String deptCode, Pageable pageable);
}
