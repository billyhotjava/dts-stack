package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsPublicLinkRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
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

    public PublicLinkService(AnalyticsPublicLinkRepository publicLinkRepository) {
        this.publicLinkRepository = publicLinkRepository;
    }

    public Optional<String> publicUuidFor(String model, long modelId) {
        return publicLinkRepository.findByModelAndModelId(model, modelId).map(AnalyticsPublicLink::getPublicUuid);
    }

    public String getOrCreate(String model, long modelId, Long creatorId) {
        return getOrCreateScoped(model, modelId, creatorId, null, null);
    }

    public String getOrCreateScoped(String model, long modelId, Long creatorId, String dept, String classification) {
        String normalizedDept = normalizeScopeValue(dept);
        String normalizedClassification = normalizeScopeValue(classification);

        Optional<AnalyticsPublicLink> existing = publicLinkRepository.findByModelAndModelId(model, modelId);
        if (existing.isPresent()) {
            AnalyticsPublicLink link = existing.get();
            String linkDept = normalizeScopeValue(link.getDept());
            String linkClassification = normalizeScopeValue(link.getClassification());
            if (!scopeMatches(linkDept, normalizedDept) || !scopeMatches(linkClassification, normalizedClassification)) {
                throw new IllegalStateException("Public link scope mismatch");
            }
            return link.getPublicUuid();
        }

        AnalyticsPublicLink link = new AnalyticsPublicLink();
        link.setModel(model);
        link.setModelId(modelId);
        link.setCreatorId(creatorId);
        link.setDept(normalizedDept);
        link.setClassification(normalizedClassification);
        link.setDisabled(false);
        link.setPublicUuid(UUID.randomUUID().toString());
        return publicLinkRepository.save(link).getPublicUuid();
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
            return false;
        }
        if (link.getExpireAt() != null && Instant.now().isAfter(link.getExpireAt())) {
            return false;
        }

        String linkDept = normalizeScopeValue(link.getDept());
        String linkClassification = normalizeScopeValue(link.getClassification());
        String normalizedDept = normalizeScopeValue(dept);
        String normalizedClassification = normalizeScopeValue(classification);

        if (!scopeMatches(linkDept, normalizedDept) || !scopeMatches(linkClassification, normalizedClassification)) {
            return false;
        }

        if (!passwordMatches(link.getPasswordHash(), plainPassword)) {
            return false;
        }

        return ipAllowed(link.getIpAllowlist(), clientIp);
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
}
