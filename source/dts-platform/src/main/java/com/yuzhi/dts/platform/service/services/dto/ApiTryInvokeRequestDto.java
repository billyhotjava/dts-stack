package com.yuzhi.dts.platform.service.services.dto;

import java.util.Map;

public record ApiTryInvokeRequestDto(Map<String, Object> params, SimulationIdentity identity) {

    public record SimulationIdentity(String type, String id, String level) {}
}
