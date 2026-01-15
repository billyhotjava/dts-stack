package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.repository.AnalyticsPublicLinkRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicLinkService {

    public static final String MODEL_CARD = "card";
    public static final String MODEL_DASHBOARD = "dashboard";

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
        link.setPublicUuid(UUID.randomUUID().toString());
        return publicLinkRepository.save(link).getPublicUuid();
    }

    public void delete(String model, long modelId) {
        publicLinkRepository.findByModelAndModelId(model, modelId).ifPresent(publicLinkRepository::delete);
    }

    public Optional<AnalyticsPublicLink> findByPublicUuid(String publicUuid) {
        return publicLinkRepository.findByPublicUuid(publicUuid);
    }

    public boolean canAccess(AnalyticsPublicLink link, String dept, String classification) {
        if (link == null) {
            return false;
        }
        String linkDept = normalizeScopeValue(link.getDept());
        String linkClassification = normalizeScopeValue(link.getClassification());
        String normalizedDept = normalizeScopeValue(dept);
        String normalizedClassification = normalizeScopeValue(classification);
        return scopeMatches(linkDept, normalizedDept) && scopeMatches(linkClassification, normalizedClassification);
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
