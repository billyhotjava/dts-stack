package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.Constants;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.topic.TopicBindingService;
import com.yuzhi.dts.platform.service.topic.TopicBindingRuntimeService;
import com.yuzhi.dts.platform.service.topic.TopicTemplateQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topic-bindings")
public class TopicBindingResource {

    private static final String TOPIC_BINDING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final TopicTemplateQueryService topicTemplateQueryService;
    private final TopicBindingService topicBindingService;
    private final TopicBindingRuntimeService topicBindingRuntimeService;

    public TopicBindingResource(
        TopicTemplateQueryService topicTemplateQueryService,
        TopicBindingService topicBindingService,
        TopicBindingRuntimeService topicBindingRuntimeService
    ) {
        this.topicTemplateQueryService = topicTemplateQueryService;
        this.topicBindingService = topicBindingService;
        this.topicBindingRuntimeService = topicBindingRuntimeService;
    }

    @GetMapping("/templates")
    @PreAuthorize(TOPIC_BINDING_MAINTAINER_EXPRESSION)
    public ApiResponse<List<TopicTemplateQueryService.TopicTemplateView>> listTemplates() {
        return ApiResponses.ok(topicTemplateQueryService.listTemplates());
    }

    @GetMapping("/status")
    @PreAuthorize(TOPIC_BINDING_MAINTAINER_EXPRESSION)
    public ApiResponse<TopicBindingRuntimeService.BindingDiagnostics> status(@RequestParam(required = false) String selector) {
        return ApiResponses.ok(topicBindingRuntimeService.diagnose(selector));
    }

    @PostMapping("/ods")
    @PreAuthorize(TOPIC_BINDING_MAINTAINER_EXPRESSION)
    public ApiResponse<TopicBindingView> bindOdsTable(@RequestBody BindOdsTableRequest request) {
        String operator = SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM);
        var binding = topicBindingService.bindOdsTable(
            new TopicBindingService.BindOdsTableRequest(
                request.templateCode(),
                request.entityCode(),
                request.dataSourceId(),
                request.schemaName(),
                request.tableName(),
                request.odsMappingId(),
                request.batchId(),
                operator,
                request.notes()
            )
        );
        return ApiResponses.ok(
            new TopicBindingView(
                binding.getId(),
                request.templateCode(),
                request.entityCode(),
                binding.getBindingMode(),
                binding.getScopeKey(),
                binding.getSchemaName(),
                binding.getTableName(),
                binding.getStatus()
            )
        );
    }

    public record BindOdsTableRequest(
        String templateCode,
        String entityCode,
        UUID dataSourceId,
        String schemaName,
        String tableName,
        UUID odsMappingId,
        UUID batchId,
        String notes
    ) {}

    public record TopicBindingView(
        UUID id,
        String templateCode,
        String entityCode,
        String bindingMode,
        String scopeKey,
        String schemaName,
        String tableName,
        String status
    ) {}
}
