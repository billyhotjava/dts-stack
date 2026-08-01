package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.common.net.IpAddressUtils;
import com.yuzhi.dts.platform.service.security.pki.PkiClientCert;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Pulls client-side network/PKI evidence off the current request and folds it into the audit
 * payload + extraTags maps. Lives outside {@link AuditService} so the same enrichment logic
 * can be reused (or unit-tested) without dragging the full pipeline along.
 */
@Component
public class PkiContextEnricher {

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return IpAddressUtils.resolveClientIp(request::getHeader, request.getRemoteAddr());
    }

    public void enrichWithPkiContext(HttpServletRequest request, Map<String, Object> payload, Map<String, Object> extraTags) {
        if (request == null) {
            return;
        }
        PkiClientCert cert = PkiClientCert.fromRequest(request);
        if (!cert.present()) {
            return;
        }
        payload.putIfAbsent("pkiCertPresent", true);
        payload.putIfAbsent("pkiCertVerified", cert.verified());
        if (StringUtils.hasText(cert.serial())) {
            payload.putIfAbsent("pkiCertSerial", cert.serial());
        }
        if (StringUtils.hasText(cert.subjectDn())) {
            payload.putIfAbsent("pkiCertSubjectDn", cert.subjectDn());
        }
        if (StringUtils.hasText(cert.issuerDn())) {
            payload.putIfAbsent("pkiCertIssuerDn", cert.issuerDn());
        }
        if (cert.notAfter() != null) {
            payload.putIfAbsent("pkiCertNotAfter", cert.notAfter().toString());
        }
        if (extraTags != null) {
            extraTags.putIfAbsent("pkiCertVerified", cert.verified());
            if (StringUtils.hasText(cert.serial())) {
                extraTags.putIfAbsent("pkiCertSerial", cert.serial());
            }
        }
    }
}
