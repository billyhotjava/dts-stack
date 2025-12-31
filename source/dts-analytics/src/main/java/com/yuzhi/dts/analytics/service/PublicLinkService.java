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
        return publicLinkRepository.findByModelAndModelId(model, modelId).map(AnalyticsPublicLink::getPublicUuid).orElseGet(() -> {
            AnalyticsPublicLink link = new AnalyticsPublicLink();
            link.setModel(model);
            link.setModelId(modelId);
            link.setCreatorId(creatorId);
            link.setPublicUuid(UUID.randomUUID().toString());
            return publicLinkRepository.save(link).getPublicUuid();
        });
    }

    public void delete(String model, long modelId) {
        publicLinkRepository.findByModelAndModelId(model, modelId).ifPresent(publicLinkRepository::delete);
    }

    public Optional<AnalyticsPublicLink> findByPublicUuid(String publicUuid) {
        return publicLinkRepository.findByPublicUuid(publicUuid);
    }
}

