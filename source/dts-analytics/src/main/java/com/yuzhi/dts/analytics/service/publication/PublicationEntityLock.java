package com.yuzhi.dts.analytics.service.publication;

import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.service.analysis.AnalysisNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

@Component
public class PublicationEntityLock {

    private final EntityManager entityManager;

    public PublicationEntityLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public AnalyticsCard analysis(long id) {
        AnalyticsCard card = entityManager.find(AnalyticsCard.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (card == null || !"analysis".equals(card.getCardType())) {
            throw new AnalysisNotFoundException("analysis does not exist");
        }
        return card;
    }

    public AnalyticsDashboard dashboard(long id) {
        AnalyticsDashboard dashboard = entityManager.find(AnalyticsDashboard.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (dashboard == null) {
            throw new AnalysisNotFoundException("dashboard does not exist");
        }
        return dashboard;
    }
}
