package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.governance.IndicatorReferenceService.ReferenceUpsertRequest;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ModelFieldIndicatorDraftService {

    private final ModelSpecApplicationService models;
    private final IndicatorService indicators;
    private final IndicatorReferenceService references;
    private final ObjectMapper objectMapper;

    public ModelFieldIndicatorDraftService(
        ModelSpecApplicationService models,
        IndicatorService indicators,
        IndicatorReferenceService references,
        ObjectMapper objectMapper
    ) {
        this.models = models;
        this.indicators = indicators;
        this.references = references;
        this.objectMapper = objectMapper;
    }

    public IndicatorDto create(String serverTenantId, String activeDept, CreateDraftRequest request) {
        if (
            request == null ||
            request.indicator() == null ||
            request.modelSpecId() == null ||
            request.modelRevision() < 1 ||
            !StringUtils.hasText(request.fieldName())
        ) {
            throw new IndicatorRequestException("指标草稿与模型字段锚点不能为空");
        }
        ModelSpecView model = models.get(serverTenantId, request.modelSpecId());
        validateModel(model, request);
        ModelField field = model
            .fields()
            .stream()
            .filter(candidate -> candidate != null && request.fieldName().trim().equals(candidate.name()))
            .findFirst()
            .orElseThrow(() -> new IndicatorRequestException("模型中不存在指定字段: " + request.fieldName()));
        if (field.role() != FieldRole.MEASURE) {
            throw new IndicatorRequestException("只能从 MEASURE 度量字段创建原子指标");
        }
        ModelSpecContract.StandardBinding binding = model
            .standardBindings()
            .stream()
            .filter(candidate -> candidate != null && field.name().equals(candidate.fieldName()))
            .findFirst()
            .orElse(null);
        UUID measurementUnitId = binding == null ? null : binding.measurementUnitId();
        Integer measurementUnitVersion = binding == null ? null : binding.measurementUnitVersion();
        if (
            !Objects.equals(measurementUnitId, request.measurementUnitId()) ||
            !Objects.equals(measurementUnitVersion, request.measurementUnitVersion())
        ) {
            throw new IndicatorConflictException("模型字段计量单位版本已变化，请刷新后重试");
        }

        IndicatorUpsertRequest indicator = request.indicator();
        indicator.setCategory("ATOMIC");
        indicator.setStatus("DRAFT");
        indicator.setVersion("v1");
        indicator.setMeasureField(field.name());
        indicator.setIsDerived(false);
        indicator.setTargetModelName(model.name());
        indicator.setSourceLayer(model.layer().name());
        indicator.setTargetLayer(model.layer().name());
        IndicatorDto created = indicators.create(indicator, activeDept);
        references.create(
            created.getId(),
            activeDept,
            new ReferenceUpsertRequest(
                "MODEL_SPEC_FIELD",
                model.id() + "@" + model.revision() + "#" + field.name(),
                model.name() + "." + field.name(),
                referenceNotes(model, field, measurementUnitId, measurementUnitVersion)
            )
        );
        return created;
    }

    private static void validateModel(ModelSpecView model, CreateDraftRequest request) {
        if (
            model.contractVersion() != ModelSpecContract.CONTRACT_VERSION ||
            model.compatibilityMode() != CompatibilityMode.CANONICAL ||
            ModelSpecContract.targetLayer(model.modelType()) != model.layer()
        ) {
            throw new IndicatorConflictException("只能从规范模型创建指标草稿");
        }
        if (model.status() != ModelStatus.PUBLISHED || model.revision() != request.modelRevision()) {
            throw new IndicatorConflictException("模型发布版本已变化，请刷新后重试");
        }
        if (
            model.modelType() != ModelType.FACT &&
            model.modelType() != ModelType.SUMMARY &&
            model.modelType() != ModelType.APPLICATION
        ) {
            throw new IndicatorRequestException("当前模型类型不支持生成指标");
        }
    }

    private String referenceNotes(
        ModelSpecView model,
        ModelField field,
        UUID measurementUnitId,
        Integer measurementUnitVersion
    ) {
        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("modelSpecId", model.id());
        notes.put("modelRevision", model.revision());
        notes.put("fieldName", field.name());
        notes.put("measurementUnitId", measurementUnitId);
        notes.put("measurementUnitVersion", measurementUnitVersion);
        try {
            return objectMapper.writeValueAsString(notes);
        } catch (JsonProcessingException ex) {
            throw new IndicatorRequestException("模型字段来源序列化失败");
        }
    }

    public record CreateDraftRequest(
        IndicatorUpsertRequest indicator,
        UUID modelSpecId,
        int modelRevision,
        String fieldName,
        UUID measurementUnitId,
        Integer measurementUnitVersion
    ) {}
}
