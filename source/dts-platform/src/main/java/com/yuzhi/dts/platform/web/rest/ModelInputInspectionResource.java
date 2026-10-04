package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.*;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.*;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Read-only helpers for the existing model workbench. Authorization is applied by the model/source owners. */
@RestController
@RequestMapping("/api/modeling/model-specs")
public class ModelInputInspectionResource {
    private final ModelInputInspectionService inspection;
    private final ModelSourceFieldsService fields;
    private final ModelSpecApplicationService models;
    private final ObjectMapper mapper;
    private final String tenant;

    public ModelInputInspectionResource(ModelInputInspectionService inspection, ModelSourceFieldsService fields,
        ModelSpecApplicationService models, ObjectMapper mapper,
        @Value("${dts.platform.modeling.default-tenant-id:default}") String tenant) {
        this.inspection = inspection;
        this.fields = fields;
        this.models = models;
        this.mapper = mapper;
        this.tenant = tenant;
    }
    @PostMapping("/upstream-availability")
    public ApiResponse<ModelInputInspectionContract.View> availability(@RequestBody ModelInputInspectionContract.Request request) {
        return ApiResponses.ok(inspection.inspect(tenant, request));
    }
    public record FieldsRequest(UUID ownerModelSpecId, int ownerRevision, String ownerChecksum, InputMode inputMode, List<JsonNode> inputs) {}
    public record FieldsView(UUID ownerModelSpecId, int ownerRevision, String ownerChecksum,
                             List<ModelSourceFieldsService.Source> sources, List<ModelSourceFieldsService.FieldIssue> issues) {}
    @PostMapping("/upstream-fields")
    public ApiResponse<FieldsView> fields(@RequestBody FieldsRequest request) {
        if (request == null || request.ownerModelSpecId() == null || request.inputMode() == null || request.inputs() == null || request.inputs().size() > 200) {
            throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_REQUEST_INVALID", "来源请求不完整或超过 200 项", ModelSpecException.Kind.BAD_REQUEST);
        }
        var owner = models.get(tenant, request.ownerModelSpecId());
        if (owner.revision() != request.ownerRevision() || !owner.checksum().equals(request.ownerChecksum())) {
            throw new ModelSpecException("MODEL_SPEC_REVISION_CONFLICT", "模型版本已变化，请保留草稿并刷新", ModelSpecException.Kind.CONFLICT);
        }
        List<ImplementationInput> inputs;
        try {
            inputs = request.inputs().stream().map(node -> (ImplementationInput) switch (request.inputMode()) {
                case UPSTREAM_MODEL -> mapper.convertValue(node, UpstreamModelInput.class);
                case PHYSICAL_ASSET -> mapper.convertValue(node, PhysicalAssetInput.class);
                case GENERATED -> mapper.convertValue(node, GeneratedInput.class);
            }).toList();
        } catch (IllegalArgumentException malformed) {
            throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_REQUEST_INVALID", "来源版本格式不正确", ModelSpecException.Kind.BAD_REQUEST);
        }
        var result = fields.directory(tenant, owner, request.inputMode(), inputs);
        return ApiResponses.ok(new FieldsView(owner.id(), owner.revision(), owner.checksum(), result.sources(), result.issues()));
    }
    @ExceptionHandler(ModelSpecException.class)
    public ResponseEntity<ApiResponse<Object>> error(ModelSpecException exception) {
        HttpStatus status = switch (exception.kind()) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case PRECONDITION_REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
        };
        return ResponseEntity.status(status).body(new ApiResponse<>(ResultStatus.ERROR.getCode(), exception.getMessage(), exception.code(), exception.details()));
    }
}
