package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCollection;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCollectionRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CollectionService {

    private final AnalyticsCollectionRepository collectionRepository;
    private final EntityIdGenerator entityIdGenerator;

    public CollectionService(AnalyticsCollectionRepository collectionRepository, EntityIdGenerator entityIdGenerator) {
        this.collectionRepository = collectionRepository;
        this.entityIdGenerator = entityIdGenerator;
    }

    public AnalyticsCollection ensurePersonalCollection(AnalyticsUser user) {
        return collectionRepository.findByPersonalOwnerId(user.getId()).orElseGet(() -> {
            AnalyticsCollection collection = new AnalyticsCollection();
            collection.setEntityId(entityIdGenerator.newEntityId());
            collection.setName(buildPersonalCollectionName(user));
            collection.setSlug(buildSlug(collection.getName()));
            collection.setColor("#31698A");
            collection.setLocation("/");
            collection.setPersonalOwnerId(user.getId());
            collection.setArchived(false);
            return collectionRepository.save(collection);
        });
    }

    private static String buildPersonalCollectionName(AnalyticsUser user) {
        String commonName = ("%s %s".formatted(nullToEmpty(user.getFirstName()), nullToEmpty(user.getLastName()))).trim();
        if (commonName.isBlank()) {
            commonName = "User";
        }
        return commonName + "'s Personal Collection";
    }

    private static String buildSlug(String name) {
        if (name == null) {
            return null;
        }
        String slug = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            return null;
        }
        return slug;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

