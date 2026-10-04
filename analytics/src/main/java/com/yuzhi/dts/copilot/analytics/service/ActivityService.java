package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsActivity;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsActivityRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final AnalyticsActivityRepository activityRepository;

    public ActivityService(AnalyticsActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Transactional
    public void recordView(long userId, String model, long modelId) {
        String normalizedModel = normalizeModel(model);
        if (normalizedModel == null) {
            return;
        }
        if (userId <= 0 || modelId <= 0) {
            return;
        }
        AnalyticsActivity activity = new AnalyticsActivity();
        activity.setUserId(userId);
        activity.setAction("view");
        activity.setModel(normalizedModel);
        activity.setModelId(modelId);
        activityRepository.save(activity);
    }

    private static String normalizeModel(String model) {
        if (model == null) {
            return null;
        }
        String m = model.trim().toLowerCase(Locale.ROOT);
        return switch (m) {
            case "card", "dashboard", "collection" -> m;
            default -> null;
        };
    }
}
