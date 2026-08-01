package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.governance.IndicatorService;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.MetricRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned handoff from a published metric model to the professional indicator owner. */
@Service
public class ModelSpecMetricReferenceService {

    private static final Pattern INDICATOR_VERSION = Pattern.compile("(?i)^v?([1-9][0-9]*)$");

    private final ModelSpecRepository repository;
    private final ModelSpecReader reader;
    private final ModelSpecSnapshotCodec codec;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final IndicatorService indicators;
    private final ModelSpecFeatureFlags featureFlags;
    private final Clock clock;

    @Autowired
    public ModelSpecMetricReferenceService(
        ModelSpecRepository repository,
        ModelSpecReader reader,
        ModelSpecSnapshotCodec codec,
        ModelSpecPlanWriteAccessPort writeAccess,
        IndicatorService indicators,
        ModelSpecFeatureFlags featureFlags
    ) {
        this(repository, reader, codec, writeAccess, indicators, featureFlags, Clock.systemUTC());
    }

    ModelSpecMetricReferenceService(
        ModelSpecRepository repository,
        ModelSpecReader reader,
        ModelSpecSnapshotCodec codec,
        ModelSpecPlanWriteAccessPort writeAccess,
        IndicatorService indicators,
        ModelSpecFeatureFlags featureFlags,
        Clock clock
    ) {
        this.repository = repository;
        this.reader = reader;
        this.codec = codec;
        this.writeAccess = writeAccess;
        this.indicators = indicators;
        this.featureFlags = featureFlags;
        this.clock = clock;
    }

    @Transactional
    public ModelSpecView bind(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        BindMetricReferenceCommand command
    ) {
        requireContext(tenantId, actorId, modelSpecId, expected, command);
        if (!featureFlags.canonicalWriteEnabled()) {
            throw error("MODEL_SPEC_CANONICAL_WRITE_DISABLED", "Canonical ModelSpec writes are disabled", ModelSpecException.Kind.CONFLICT);
        }
        StoredModelSpec stored = repository.findCurrent(tenantId, modelSpecId).orElseThrow(() -> notFound(modelSpecId));
        ModelSpecView current = reader.read(stored);
        requireExpected(current, expected);
        if (
            current.contractVersion() != ModelSpecContract.CONTRACT_VERSION ||
            current.status() != ModelStatus.PUBLISHED ||
            !isMetricModel(current.modelType())
        ) {
            throw error(
                "MODEL_METRIC_HANDOFF_MODEL_INELIGIBLE",
                "Only published fact, summary or application ModelSpecs can bind indicator versions",
                ModelSpecException.Kind.CONFLICT
            );
        }
        PlanState plan = repository.lockPlan(tenantId, current.planId()).orElseThrow(() -> notFound(modelSpecId));
        if ("PUBLISHED".equals(plan.lifecycleStatus()) || "ARCHIVED".equals(plan.lifecycleStatus())) {
            throw error("MODEL_SPEC_PLAN_READONLY", "Warehouse plan lifecycle does not allow metric handoff", ModelSpecException.Kind.CONFLICT);
        }
        if (!writeAccess.canMaintain(tenantId, current.planId(), actorId)) {
            throw error("MODEL_SPEC_PLAN_FORBIDDEN", "Warehouse plan is not available for maintenance", ModelSpecException.Kind.FORBIDDEN);
        }

        IndicatorDto indicator = indicators.get(command.metricId(), null);
        Integer ownerVersion = indicator == null ? null : parseVersion(indicator.getVersion());
        if (
            indicator == null ||
            !"PUBLISHED".equalsIgnoreCase(indicator.getStatus()) ||
            ownerVersion == null ||
            ownerVersion != command.version()
        ) {
            throw error(
                "MODEL_METRIC_REF_NOT_CURRENT",
                "Metric reference must match the current published indicator owner version",
                ModelSpecException.Kind.UNPROCESSABLE
            );
        }

        MetricRef reference = new MetricRef(command.metricId().toString(), command.version());
        if (current.metricRefs().contains(reference)) return current;
        List<MetricRef> refs = new ArrayList<>(current.metricRefs());
        refs.removeIf(item -> item != null && Objects.equals(item.metricId(), reference.metricId()));
        refs.add(reference);
        UpdateModelSpecCommand update = toUpdate(current, List.copyOf(refs));
        ModelSpecView replacement = codec.toUpdatedView(current, update, current.revision() + 1, clock.instant());
        String snapshot = codec.write(replacement);
        int changed = repository.compareAndSetPublishedMetricRefs(
            tenantId,
            actorId,
            current.revision(),
            current.checksum(),
            replacement,
            snapshot
        );
        if (changed == 0) {
            ModelSpecView latest = repository.findCurrent(tenantId, modelSpecId).map(reader::read).orElseThrow(() -> notFound(modelSpecId));
            throw conflict(latest);
        }
        repository.insertV2Revision(tenantId, actorId, replacement, snapshot);
        return replacement;
    }

    private static UpdateModelSpecCommand toUpdate(ModelSpecView view, List<MetricRef> metricRefs) {
        return new UpdateModelSpecCommand(
            view.planId(),
            view.domainId(),
            view.modelType(),
            view.layer(),
            view.name(),
            view.description(),
            view.implementationMode(),
            view.materialization(),
            view.businessActivityRef(),
            view.consumptionScenario(),
            view.grain(),
            view.factShape(),
            view.timeSemantics(),
            view.fields(),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            metricRefs,
            view.standardBindings(),
            view.generationStrategy(),
            view.dimensionProfile()
        );
    }

    private static void requireContext(
        String tenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        BindMetricReferenceCommand command
    ) {
        if (tenantId == null || tenantId.isBlank() || actorId == null || actorId.isBlank()) {
            throw error("MODEL_SPEC_ACTOR_REQUIRED", "Authenticated server context is required", ModelSpecException.Kind.FORBIDDEN);
        }
        if (
            modelSpecId == null ||
            expected == null ||
            !modelSpecId.equals(expected.modelSpecId()) ||
            command == null ||
            command.metricId() == null ||
            command.version() < 1
        ) {
            throw error("MODEL_METRIC_REF_INVALID", "A valid model, metric and version are required", ModelSpecException.Kind.BAD_REQUEST);
        }
    }

    private static void requireExpected(ModelSpecView current, ExpectedVersion expected) {
        if (current.revision() != expected.revision() || !Objects.equals(current.checksum(), expected.checksum())) {
            throw conflict(current);
        }
    }

    private static boolean isMetricModel(ModelType type) {
        return type == ModelType.FACT || type == ModelType.SUMMARY || type == ModelType.APPLICATION;
    }

    private static Integer parseVersion(String value) {
        if (value == null) return null;
        Matcher matcher = INDICATOR_VERSION.matcher(value.trim());
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static ModelSpecException conflict(ModelSpecView current) {
        return new ModelSpecException(
            "MODEL_SPEC_REVISION_CONFLICT",
            "ModelSpec was changed by another operation",
            ModelSpecException.Kind.CONFLICT,
            Map.of(
                "currentRevision",
                current.revision(),
                "currentChecksum",
                current.checksum(),
                "currentEtag",
                ModelSpecApplicationService.etag(current)
            )
        );
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "MODEL_SPEC_NOT_FOUND",
            "ModelSpec was not found",
            ModelSpecException.Kind.NOT_FOUND,
            id == null ? Map.of() : Map.of("modelSpecId", id)
        );
    }

    private static ModelSpecException error(String code, String message, ModelSpecException.Kind kind) {
        return new ModelSpecException(code, message, kind);
    }

    public record BindMetricReferenceCommand(UUID metricId, int version) {}
}
