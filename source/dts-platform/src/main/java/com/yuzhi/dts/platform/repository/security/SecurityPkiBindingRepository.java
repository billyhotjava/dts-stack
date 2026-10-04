package com.yuzhi.dts.platform.repository.security;

import com.yuzhi.dts.platform.domain.security.SecurityPkiBinding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityPkiBindingRepository extends JpaRepository<SecurityPkiBinding, UUID> {
    Optional<SecurityPkiBinding> findFirstByUserIdIgnoreCaseAndStatusIgnoreCase(String userId, String status);
    Optional<SecurityPkiBinding> findFirstByUsernameIgnoreCaseAndStatusIgnoreCase(String username, String status);
    Optional<SecurityPkiBinding> findFirstByCertSerialIgnoreCaseAndStatusIgnoreCase(String certSerial, String status);
}

