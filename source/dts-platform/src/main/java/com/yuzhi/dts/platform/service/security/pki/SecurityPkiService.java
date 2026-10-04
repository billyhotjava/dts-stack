package com.yuzhi.dts.platform.service.security.pki;

import com.yuzhi.dts.platform.domain.security.SecurityPkiBinding;
import com.yuzhi.dts.platform.repository.security.SecurityPkiBindingRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional
public class SecurityPkiService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";

    private final SecurityPkiBindingRepository bindingRepository;

    public SecurityPkiService(SecurityPkiBindingRepository bindingRepository) {
        this.bindingRepository = bindingRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        PkiClientCert cert = readClientCert();

        result.put("certPresent", cert.present());
        result.put("certVerified", cert.verified());
        result.put("certSerial", cert.serial());
        result.put("certSubjectDn", cert.subjectDn());
        result.put("certIssuerDn", cert.issuerDn());
        result.put("certNotBefore", cert.notBefore());
        result.put("certNotAfter", cert.notAfter());

        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        SecurityPkiBinding currentBinding = resolveCurrentBinding(userId, username);
        if (currentBinding != null) {
            result.put("bindingId", currentBinding.getId() != null ? currentBinding.getId().toString() : null);
            result.put("bindingSerial", currentBinding.getCertSerial());
            result.put("bindingStatus", currentBinding.getStatus());
            result.put("bindingUpdatedAt", currentBinding.getLastModifiedDate());
        } else {
            result.put("bindingStatus", "UNBOUND");
        }

        if (cert.present() && cert.verified() && StringUtils.isNotBlank(cert.serial()) && currentBinding != null) {
            boolean match = StringUtils.equalsIgnoreCase(StringUtils.trimToNull(currentBinding.getCertSerial()), StringUtils.trimToNull(cert.serial()));
            result.put("bindingMatchesCert", match);
            if (!match) {
                result.put("warning", "当前登录账号已绑定其他证书序列号");
            }
        }
        if (cert.present() && !cert.verified()) {
            result.put("warning", "检测到证书信息，但未通过网关验证");
        }
        return result;
    }

    public Map<String, Object> bindCurrent() {
        PkiClientCert cert = readClientCert();
        if (!cert.present()) {
            throw new IllegalStateException("未检测到客户端证书");
        }
        if (!cert.verified()) {
            throw new IllegalStateException("客户端证书未通过网关验证");
        }
        String serial = StringUtils.trimToNull(cert.serial());
        if (!StringUtils.isNotBlank(serial)) {
            throw new IllegalStateException("证书序列号为空");
        }
        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (!StringUtils.isNotBlank(userId) && !StringUtils.isNotBlank(username)) {
            throw new IllegalStateException("无法识别当前登录账号");
        }

        bindingRepository
            .findFirstByCertSerialIgnoreCaseAndStatusIgnoreCase(serial, STATUS_ACTIVE)
            .ifPresent(existing -> {
                if (!matchesUser(existing, userId, username)) {
                    throw new IllegalStateException("该证书已绑定到其他账号");
                }
            });

        SecurityPkiBinding binding = resolveCurrentBinding(userId, username);
        if (binding == null) {
            binding = new SecurityPkiBinding();
        }
        binding.setUserId(StringUtils.trimToNull(userId));
        binding.setUsername(StringUtils.trimToNull(username));
        binding.setCertSerial(serial);
        binding.setSubjectDn(StringUtils.trimToNull(cert.subjectDn()));
        binding.setIssuerDn(StringUtils.trimToNull(cert.issuerDn()));
        binding.setNotBefore(cert.notBefore());
        binding.setNotAfter(cert.notAfter());
        binding.setStatus(STATUS_ACTIVE);
        binding.setLastSeenAt(Instant.now());
        bindingRepository.save(binding);

        return status();
    }

    public Map<String, Object> unbindCurrent() {
        String userId = SecurityUtils.getCurrentUserId().orElse(null);
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        SecurityPkiBinding binding = resolveCurrentBinding(userId, username);
        if (binding == null) {
            throw new EntityNotFoundException("当前账号未绑定证书");
        }
        binding.setStatus(STATUS_REVOKED);
        binding.setLastSeenAt(Instant.now());
        bindingRepository.save(binding);
        return status();
    }

    private SecurityPkiBinding resolveCurrentBinding(String userId, String username) {
        if (StringUtils.isNotBlank(userId)) {
            return bindingRepository.findFirstByUserIdIgnoreCaseAndStatusIgnoreCase(userId.trim(), STATUS_ACTIVE).orElse(null);
        }
        if (StringUtils.isNotBlank(username)) {
            return bindingRepository.findFirstByUsernameIgnoreCaseAndStatusIgnoreCase(username.trim(), STATUS_ACTIVE).orElse(null);
        }
        return null;
    }

    private boolean matchesUser(SecurityPkiBinding binding, String userId, String username) {
        if (binding == null) {
            return false;
        }
        if (StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(binding.getUserId())) {
            return StringUtils.equalsIgnoreCase(binding.getUserId().trim(), userId.trim());
        }
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(binding.getUsername())) {
            return StringUtils.equalsIgnoreCase(binding.getUsername().trim(), username.trim());
        }
        return false;
    }

    public PkiClientCert readClientCert() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null || attrs.getRequest() == null) {
                return PkiClientCert.absent();
            }
            return PkiClientCert.fromRequest(attrs.getRequest());
        } catch (Exception ignored) {
            return PkiClientCert.absent();
        }
    }
}
