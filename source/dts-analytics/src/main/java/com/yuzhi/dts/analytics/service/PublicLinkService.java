package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.repository.AnalyticsPublicLinkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicLinkService {

    public static final String MODEL_CARD = "card";
    public static final String MODEL_DASHBOARD = "dashboard";
    public static final String MODEL_SCREEN = "screen";

    private final AnalyticsPublicLinkRepository publicLinkRepository;
    private final AnalyticsConsumerClassificationService classificationService;
    private final ScreenAuditService screenAuditService;

    public PublicLinkService(
        AnalyticsPublicLinkRepository publicLinkRepository,
        AnalyticsConsumerClassificationService classificationService,
        ScreenAuditService screenAuditService
    ) {
        this.publicLinkRepository = publicLinkRepository;
        this.classificationService = classificationService;
        this.screenAuditService = screenAuditService;
    }

    public Optional<String> publicUuidFor(String model, long modelId) {
        return publicLinkRepository.findByModelAndModelId(model, modelId).map(AnalyticsPublicLink::getPublicUuid);
    }

    public String getOrCreate(String model, long modelId, Long creatorId) {
        return getOrCreateScoped(model, modelId, creatorId, null, null);
    }

    public String getOrCreateScoped(String model, long modelId, Long creatorId, String dept, String classification) {
        String normalizedDept = normalizeScopeValue(dept);
        String effectiveClassification;
        if (isClassificationManagedModel(model)) {
            var derived = classificationService.ensurePublicConsumer(model, modelId);
            effectiveClassification = normalizeScopeValue(derived.effectiveLevel());
        } else {
            // Legacy public-link models (for example explore_session) keep their
            // existing scoped classification until they receive a canonical
            // consumer derivation contract of their own.
            effectiveClassification = normalizeScopeValue(classification);
        }

        Optional<AnalyticsPublicLink> existing = publicLinkRepository.findByModelAndModelId(model, modelId);
        if (existing.isPresent()) {
            AnalyticsPublicLink link = existing.get();
            String linkDept = normalizeScopeValue(link.getDept());
            if (!scopeMatches(linkDept, normalizedDept)) {
                throw new IllegalStateException("Public link scope mismatch");
            }
            link.setClassification(effectiveClassification);
            publicLinkRepository.save(link);
            if (isClassificationManagedModel(model)) {
                classificationService.bindPublicLink(
                    link.getPublicUuid(),
                    model,
                    modelId,
                    link.getExpireAt()
                );
            }
            return link.getPublicUuid();
        }

        AnalyticsPublicLink link = new AnalyticsPublicLink();
        link.setModel(model);
        link.setModelId(modelId);
        link.setCreatorId(creatorId);
        link.setDept(normalizedDept);
        link.setClassification(effectiveClassification);
        link.setDisabled(false);
        link.setPublicUuid(UUID.randomUUID().toString());
        AnalyticsPublicLink saved = publicLinkRepository.save(link);
        if (isClassificationManagedModel(model)) {
            classificationService.bindPublicLink(
                saved.getPublicUuid(),
                model,
                modelId,
                saved.getExpireAt()
            );
        }
        return saved.getPublicUuid();
    }

    public void delete(String model, long modelId) {
        publicLinkRepository.findByModelAndModelId(model, modelId).ifPresent(publicLinkRepository::delete);
    }

    public Optional<AnalyticsPublicLink> findByPublicUuid(String publicUuid) {
        return publicLinkRepository.findByPublicUuid(publicUuid);
    }

    public Optional<AnalyticsPublicLink> findByModelAndModelId(String model, long modelId) {
        return publicLinkRepository.findByModelAndModelId(model, modelId);
    }

    public AnalyticsPublicLink save(AnalyticsPublicLink link) {
        return publicLinkRepository.save(link);
    }

    public boolean canAccess(AnalyticsPublicLink link, String dept, String classification) {
        return canAccess(link, dept, classification, null, null);
    }

    public boolean canAccess(
            AnalyticsPublicLink link,
            String dept,
            String classification,
            String clientIp,
            String plainPassword) {
        if (link == null) {
            return false;
        }
        if (link.isDisabled()) {
            return deny(link, "DISABLED");
        }
        if (link.getExpireAt() != null && Instant.now().isAfter(link.getExpireAt())) {
            return deny(link, "EXPIRED");
        }
        if (isClassificationManagedModel(link.getModel())) {
            try {
                classificationService.requireCurrentPublicLink(link.getPublicUuid());
            } catch (RuntimeException ex) {
                return deny(link, "CLASSIFICATION_SNAPSHOT_STALE");
            }
        }

        String linkDept = normalizeScopeValue(link.getDept());
        String linkClassification = normalizeScopeValue(link.getClassification());
        String normalizedDept = normalizeScopeValue(dept);
        String normalizedClassification = normalizeScopeValue(classification);

        if (
            !scopeMatches(linkDept, normalizedDept) ||
            !classificationAllows(normalizedClassification, linkClassification)
        ) {
            return deny(link, "SCOPE_OR_CLASSIFICATION_BLOCKED");
        }

        if (!passwordMatches(link.getPasswordHash(), plainPassword)) {
            return deny(link, "PASSWORD_BLOCKED");
        }

        return ipAllowed(link.getIpAllowlist(), clientIp) || deny(link, "IP_BLOCKED");
    }

    public static String hashPassword(String plainPassword) {
        if (plainPassword == null) {
            return null;
        }
        String trimmed = plainPassword.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    private static boolean passwordMatches(String passwordHash, String plainPassword) {
        if (passwordHash == null || passwordHash.isBlank()) {
            return true;
        }
        String hashed = hashPassword(plainPassword);
        if (hashed == null) {
            return false;
        }
        return passwordHash.equals(hashed);
    }

    private static boolean ipAllowed(String allowlist, String clientIp) {
        if (allowlist == null || allowlist.isBlank()) {
            return true;
        }
        if (clientIp == null || clientIp.isBlank()) {
            return false;
        }
        String normalizedClientIp = clientIp.trim();
        String[] tokens = allowlist.split("[,\\n\\r\\t ]+");
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String ip = token.trim();
            if (!ip.isBlank() && ip.equals(normalizedClientIp)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeScopeValue(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isBlank() ? null : t;
    }

    private static boolean scopeMatches(String stored, String current) {
        if (stored == null || stored.isBlank()) {
            return true;
        }
        if (current == null || current.isBlank()) {
            return false;
        }
        return stored.equals(current);
    }

    private static boolean classificationAllows(String caller, String asset) {
        SecurityLevelCatalog.DataSecurityLevel assetLevel =
            SecurityLevelCatalog.DataSecurityLevel.parse(asset);
        if (assetLevel == null) {
            return false;
        }
        if (assetLevel == SecurityLevelCatalog.DataSecurityLevel.PUBLIC) {
            return true;
        }
        SecurityLevelCatalog.DataSecurityLevel callerLevel =
            SecurityLevelCatalog.parseMaxDataLevel(caller);
        return callerLevel != null && callerLevel.number() >= assetLevel.number();
    }

    private static boolean isClassificationManagedModel(String model) {
        String normalized = model == null ? "" : model.trim().toLowerCase();
        return MODEL_CARD.equals(normalized) ||
            MODEL_DASHBOARD.equals(normalized) ||
            MODEL_SCREEN.equals(normalized);
    }

    private boolean deny(AnalyticsPublicLink link, String reason) {
        if (
            screenAuditService != null &&
            link != null &&
            MODEL_SCREEN.equalsIgnoreCase(link.getModel()) &&
            link.getModelId() != null
        ) {
            screenAuditService.log(
                link.getModelId(),
                null,
                "screen.public_link.access.denied",
                null,
                Map.of(
                    "publicUuid", link.getPublicUuid() == null ? "" : link.getPublicUuid(),
                    "reason", reason
                ),
                null
            );
        }
        return false;
    }
}
