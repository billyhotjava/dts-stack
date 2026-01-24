package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.service.etl.AirbyteClient;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/infra/settings/airbyte")
public class IngestionSettingsResource {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionSettingsResource.class);

    private final AirbyteClient airbyteClient;
    private final IngestionSettingsService settingsService;

    public IngestionSettingsResource(AirbyteClient airbyteClient, IngestionSettingsService settingsService) {
        this.airbyteClient = airbyteClient;
        this.settingsService = settingsService;
    }

    public record DestinationDefinitionRequest(String name, String dockerRepository, String dockerImageTag) {}
    public record DefaultDestinationRequest(
        String destinationId,
        String destinationDefinitionId,
        String destinationName,
        Map<String, Object> destinationConfig
    ) {}

    @PostMapping("/destination-definitions/register")
    public ApiResponse<Map<String, Object>> registerDestinationDefinition(@RequestBody DestinationDefinitionRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (!StringUtils.hasText(request.dockerRepository()) || !StringUtils.hasText(request.dockerImageTag())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写目标端镜像仓库和标签");
        }
        try {
            return airbyteClient
                .createCustomDestinationDefinition(request.name(), request.dockerRepository(), request.dockerImageTag())
                .map(ApiResponses::<Map<String, Object>>ok)
                .orElseGet(() -> ApiResponses.error(HttpStatus.BAD_GATEWAY.value(), "注册目标端失败"));
        } catch (Exception ex) {
            LOG.warn("Failed to register destination definition: {}", ex.getMessage());
            return ApiResponses.error(HttpStatus.BAD_GATEWAY.value(), "注册目标端失败");
        }
    }

    @PostMapping("/default-destination")
    public ApiResponse<Map<String, Object>> upsertDefaultDestination(@RequestBody DefaultDestinationRequest request) {
        Map<String, Object> settings = new LinkedHashMap<>();
        if (request != null) {
            if (StringUtils.hasText(request.destinationId())) {
                settings.put("defaultDestinationId", request.destinationId().trim());
            }
            if (StringUtils.hasText(request.destinationDefinitionId())) {
                settings.put("defaultDestinationDefinitionId", request.destinationDefinitionId().trim());
            }
            if (StringUtils.hasText(request.destinationName())) {
                settings.put("defaultDestinationName", request.destinationName().trim());
            }
            if (request.destinationConfig() != null && !request.destinationConfig().isEmpty()) {
                settings.put("defaultDestinationConfig", new LinkedHashMap<>(request.destinationConfig()));
            }
        }
        if (settings.isEmpty()) {
            return ApiResponses.error(HttpStatus.BAD_REQUEST.value(), "缺少目标端配置");
        }
        settingsService.upsertSettings(IngestionSettingsService.SERVICE_AIRBYTE, settings, "platform");
        return ApiResponses.ok(settings);
    }
}
