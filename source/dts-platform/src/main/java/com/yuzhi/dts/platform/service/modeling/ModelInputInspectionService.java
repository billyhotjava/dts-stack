package com.yuzhi.dts.platform.service.modeling;

import static com.yuzhi.dts.platform.service.modeling.ModelInputInspectionContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ModelInputInspectionService {
    private final ModelInputContextLoader loader;
    public ModelInputInspectionService(ModelInputContextLoader loader) { this.loader = loader; }

    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, timeout = 3)
    public View inspect(String tenantId, Request request) {
        if (request == null || request.ownerModelSpecId() == null || request.ownerRevision() < 1 || request.ownerChecksum() == null) {
            throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_REQUEST_INVALID", "缺少当前模型版本", ModelSpecException.Kind.BAD_REQUEST);
        }
        List<UpstreamModelInput> pins = request.selectedInputs() == null ? List.of() : request.selectedInputs();
        if (pins.stream().anyMatch(pin -> pin == null || !pin.implementationPinned())) {
            throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_PIN_REQUIRED", "已选引用必须包含完整版本", ModelSpecException.Kind.BAD_REQUEST);
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>(request.modelSpecIds() == null ? List.of() : request.modelSpecIds());
        pins.forEach(pin -> ids.add(pin.modelSpecId()));
        if (ids.contains(null) || ids.size() > 200) throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_WINDOW_INVALID",
            "一次最多检查 200 个上游", ModelSpecException.Kind.BAD_REQUEST);
        boolean requestedSelf = ids.contains(request.ownerModelSpecId());
        ids.add(request.ownerModelSpecId());
        Context context = loader.load(tenantId, ids, pins.stream().map(pin -> new ModelRevisionRef(pin.modelSpecId(), pin.revision())).toList());
        ModelSpecView owner = context.heads().get(request.ownerModelSpecId());
        if (owner == null) throw new ModelSpecException("MODEL_SPEC_NOT_FOUND", "模型不存在或不可读取", ModelSpecException.Kind.NOT_FOUND);
        if (owner.revision() != request.ownerRevision() || !owner.checksum().equals(request.ownerChecksum())) {
            throw new ModelSpecException("MODEL_SPEC_REVISION_CONFLICT", "当前模型已更新，请保留草稿并重新读取模型", ModelSpecException.Kind.CONFLICT);
        }
        if (request.selectedInputs() == null && context.implementations().containsKey(owner.id())) {
            pins = context.implementations().get(owner.id()).inputs().stream().filter(UpstreamModelInput.class::isInstance)
                .map(UpstreamModelInput.class::cast).toList();
            // Include persisted pins even if callers only requested visible candidates.
            return inspect(tenantId, new Request(owner.id(), owner.revision(), owner.checksum(), request.modelSpecIds(), pins));
        }
        if (!requestedSelf) ids.remove(owner.id());
        List<Item> items = new ArrayList<>();
        for (UUID id : ids.stream().sorted().toList()) {
            ModelSpecView model = context.heads().get(id);
            UpstreamModelInput selected = pins.stream().filter(pin -> pin.modelSpecId().equals(id)).findFirst().orElse(null);
            UpstreamModelInput current = ModelImplementationInputPolicy.currentPin(model, context.implementations().get(id));
            String reason = ModelImplementationInputPolicy.inspectUpstream(owner, context, id, null);
            String referenceReason = selected == null ? null : ModelImplementationInputPolicy.inspectUpstream(owner, context, id, selected);
            String state = selected == null ? "UNSELECTED" : referenceReason == null ? "CURRENT"
                : referenceReason.startsWith("DESIGN_") ? "DESIGN_DRIFT"
                : referenceReason.startsWith("IMPLEMENTATION_REVISION") || referenceReason.startsWith("IMPLEMENTATION_CHECKSUM") || referenceReason.equals("DBT_ID_DRIFT")
                    ? "IMPLEMENTATION_DRIFT" : "UNAVAILABLE";
            var implementation = context.implementations().get(id);
            String implementationState = model == null ? "UNKNOWN" : implementation == null ? "NONE"
                : "ACTIVE".equals(implementation.status()) ? "ACTIVE" : "INACTIVE".equals(implementation.status()) ? "INACTIVE" : "UNKNOWN";
            items.add(new Item(id, model == null ? null : model.revision(), model == null ? null : model.checksum(),
                implementationState, current, reason == null, reason, state, model == null ? null : selected, referenceReason));
        }
        return new View(owner.id(), owner.revision(), owner.checksum(), Instant.now(), List.copyOf(items));
    }

    public List<Issue> validate(String tenantId, ModelSpecView owner, List<UpstreamModelInput> inputs) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        inputs.forEach(input -> ids.add(input.modelSpecId()));
        ids.add(owner.id());
        Context context = loader.load(tenantId, ids, inputs.stream().map(pin -> new ModelRevisionRef(pin.modelSpecId(), pin.revision())).toList());
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            UpstreamModelInput input = inputs.get(i);
            String reason = ModelImplementationInputPolicy.inspectUpstream(owner, context, input.modelSpecId(), input);
            if (reason != null) {
                boolean readable = context.heads().containsKey(input.modelSpecId());
                issues.add(new Issue(input.modelSpecId(), reason, readable ? input : null,
                    readable ? ModelImplementationInputPolicy.currentPin(context.heads().get(input.modelSpecId()), context.implementations().get(input.modelSpecId())) : null,
                    "inputs[" + i + "]"));
            }
        }
        return List.copyOf(issues);
    }
}
