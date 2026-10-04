package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.modeling.ModelInputInspectionContract.Context;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** Standalone reads use a short consistent snapshot; existing writes reuse their connection and CAS boundary. */
@Service
public class ModelInputContextLoader {
    private final ModelSpecRepository models;
    private final ModelLifecycleRepository implementations;
    private final ModelSpecReader reader;
    private final ModelSpecDomainReadAccessPort domains;

    public ModelInputContextLoader(ModelSpecRepository models, ModelLifecycleRepository implementations,
                                   ModelSpecReader reader, ModelSpecDomainReadAccessPort domains) {
        this.models = models;
        this.implementations = implementations;
        this.reader = reader;
        this.domains = domains;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 3)
    public Context load(String tenantId, Collection<UUID> ids, Collection<ModelRevisionRef> selected) {
        if (tenantId == null || tenantId.isBlank() || ids == null || ids.size() > 201 || selected.size() > 200) {
            throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_WINDOW_INVALID", "一次最多检查 200 个上游，请缩小查询范围", ModelSpecException.Kind.BAD_REQUEST);
        }
        Set<UUID> visible = domains.visibleDomainIds();
        Map<UUID, ModelSpecView> heads = new LinkedHashMap<>();
        models.findInputHeads(tenantId, ids).stream()
            .filter(row -> visible.contains(row.domainId()))
            .map(reader::read).forEach(model -> heads.put(model.id(), model));
        Map<UUID, ImplementationView> impl = new LinkedHashMap<>();
        implementations.findInputImplementations(tenantId, heads.keySet()).forEach(row -> impl.put(row.modelSpecId(), row));
        Set<ModelRevisionRef> roots = new LinkedHashSet<>();
        heads.values().forEach(model -> roots.add(new ModelRevisionRef(model.id(), model.revision())));
        selected.stream().filter(ref -> heads.containsKey(ref.modelSpecId())).forEach(roots::add);
        var stored = models.findInputClosure(tenantId, java.util.List.copyOf(roots));
        if (stored.size() > 2000) throw new ModelSpecException("MODEL_IMPLEMENTATION_CONTEXT_LIMIT_EXCEEDED",
            "依赖范围超过检查上限，请缩小候选范围后重试", ModelSpecException.Kind.CONFLICT);
        Map<ModelRevisionRef, ModelSpecView> revisions = new LinkedHashMap<>();
        stored.stream().filter(row -> visible.contains(row.domainId())).map(reader::read)
            .forEach(model -> revisions.put(new ModelRevisionRef(model.id(), model.revision()), model));
        return new Context(Map.copyOf(heads), Map.copyOf(impl), Map.copyOf(revisions));
    }
}
