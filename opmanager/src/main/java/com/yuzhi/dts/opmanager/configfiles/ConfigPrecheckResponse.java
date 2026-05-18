package com.yuzhi.dts.opmanager.configfiles;

import java.util.List;

public record ConfigPrecheckResponse(String packageRegistrationId, String packageId, String targetStackDir, int total, int changed, int highRisk, List<ConfigFileReview> files) {}
