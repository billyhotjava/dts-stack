package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsSetting;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsSettingRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SetupStateService {

    private static final String SETUP_COMPLETED_KEY = "setup_completed";
    private static final String SETUP_TOKEN_KEY = "setup_token";
    private static final String SITE_NAME_KEY = "site-name";

    private final AnalyticsSettingRepository settingRepository;

    public SetupStateService(AnalyticsSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public boolean isSetupCompleted() {
        return settingRepository.findById(SETUP_COMPLETED_KEY)
                .map(s -> "true".equalsIgnoreCase(s.getSettingValue()))
                .orElse(false);
    }

    public String getOrCreateSetupToken() {
        Optional<AnalyticsSetting> existing = settingRepository.findById(SETUP_TOKEN_KEY);
        if (existing.isPresent() && !existing.get().getSettingValue().isBlank()) {
            return existing.get().getSettingValue();
        }

        AnalyticsSetting setting = existing.orElseGet(AnalyticsSetting::new);
        setting.setSettingKey(SETUP_TOKEN_KEY);
        setting.setSettingValue(UUID.randomUUID().toString());
        return settingRepository.save(setting).getSettingValue();
    }

    public void markSetupCompleted() {
        AnalyticsSetting setting = settingRepository.findById(SETUP_COMPLETED_KEY).orElseGet(AnalyticsSetting::new);
        setting.setSettingKey(SETUP_COMPLETED_KEY);
        setting.setSettingValue("true");
        settingRepository.save(setting);
    }

    @Transactional(readOnly = true)
    public Optional<String> getSiteName() {
        return settingRepository.findById(SITE_NAME_KEY).map(AnalyticsSetting::getSettingValue).map(String::trim).filter(v -> !v.isEmpty());
    }

    public void setSiteName(String siteName) {
        AnalyticsSetting setting = settingRepository.findById(SITE_NAME_KEY).orElseGet(AnalyticsSetting::new);
        setting.setSettingKey(SITE_NAME_KEY);
        setting.setSettingValue(siteName);
        settingRepository.save(setting);
    }
}
