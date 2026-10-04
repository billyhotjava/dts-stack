package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;

public record ApiPolicyDto(String minLevel, List<String> maskedColumns, String rowFilter) {}
