package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.service.mdm.MdmGatewayService;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mdm")
public class MdmGatewayResource {

    private static final Logger LOG = LoggerFactory.getLogger(MdmGatewayResource.class);

    private final MdmGatewayService gatewayService;
    private final MdmGatewayProperties properties;

    public MdmGatewayResource(MdmGatewayService gatewayService, MdmGatewayProperties properties) {
        this.gatewayService = gatewayService;
        this.properties = properties;
    }

    @PostMapping("/pull-requests")
    public ResponseEntity<ApiResponse<MdmGatewayService.PullResult>> createPullRequest(@RequestBody(required = false) Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MDM 网关未启用"));
        }
        MdmGatewayService.PullResult result = gatewayService.triggerUpstreamPull(payload == null ? Map.of() : payload);
        // 记录手工触发的拉取操作
        LOG.info("mdm.manual.pull requested payloadKeys={} upstreamStatus={} requestId={}", payload == null ? 0 : payload.keySet(), result.upstreamStatus, result.requestId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping(
        value = "/receive",
        consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE }
    )
    public ResponseEntity<ApiResponse<MdmGatewayService.CallbackResult>> receiveCallback(
        @RequestParam Map<String, String> params,
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "dataType", required = false) String dataType,
        @RequestParam(value = "payload", required = false) String payloadField,
        @RequestParam(value = "body", required = false) String bodyField,
        HttpServletRequest request
    ) throws Exception {
        if (!properties.isEnabled()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MDM 网关未启用"));
        }
        // 兼容：优先 dataType 参数，其次 type。兼容非 multipart 的 body/payload。
        String dt = StringUtils.defaultIfBlank(dataType, type);
        String body = StringUtils.firstNonBlank(payloadField, bodyField, "");
        MdmGatewayService.CallbackResult result = gatewayService.handleReceive(params, dt, file, body, request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/handshake")
    public ResponseEntity<ApiResponse<MdmGatewayService.PullResult>> handshake(@RequestBody(required = false) Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("MDM 网关未启用"));
        }
        // 复用上游拉取逻辑，表示手动触发一次推送请求
        MdmGatewayService.PullResult result = gatewayService.triggerUpstreamPull(payload == null ? Map.of() : payload);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
