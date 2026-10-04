package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Trusted projection reader. User-facing callers must authorize each returned identity and snapshot. */
@Component
@Transactional(readOnly = true)
public class PublishedIndicatorVersionReader {
    private final GovIndicatorDefinitionRepository definitions;
    private final GovIndicatorVersionRepository versions;
    private final ObjectMapper mapper;
    public PublishedIndicatorVersionReader(GovIndicatorDefinitionRepository definitions, GovIndicatorVersionRepository versions, ObjectMapper mapper) {
        this.definitions = definitions; this.versions = versions; this.mapper = mapper;
    }
    public GovIndicatorDefinition read(IndicatorAnalysisContract.VersionRef ref) {
        var current = definitions.findById(ref.id()).orElseThrow(() -> new IndicatorNotFoundException("指标不存在"));
        if ("ARCHIVED".equals(current.getStatus()) || "DEPRECATED".equals(current.getStatus())) throw new IndicatorConflictException("指标已停用");
        var version = versions.findByIndicatorAndVersion(current, ref.version()).orElseThrow(() -> new IndicatorNotFoundException("固定版本不存在"));
        if (!"PUBLISHED".equals(version.getStatus())) throw new IndicatorConflictException("固定版本未发布");
        try {
            IndicatorUpsertRequest request = mapper.readerFor(IndicatorUpsertRequest.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(version.getSnapshotJson());
            GovIndicatorDefinition result = new GovIndicatorDefinition(); IndicatorMapper.apply(result, request);
            result.setId(ref.id()); result.setVersion(ref.version()); result.setStatus("PUBLISHED"); return result;
        } catch (Exception error) { throw new IndicatorConflictException("版本快照无效"); }
    }
    public java.util.List<IndicatorAnalysisContract.VersionRef> dependencies(GovIndicatorDefinition definition) {
        return IndicatorMapper.readSourceRefs(definition.getSourceRefs()).stream()
            .filter(ref -> ref.sourceType() == IndicatorBusinessContextContract.SourceType.INDICATOR_VERSION)
            .map(ref -> new IndicatorAnalysisContract.VersionRef(java.util.UUID.fromString(ref.sourceId()), ref.sourceVersion())).toList();
    }
    public IndicatorImplementationRef implementation(GovIndicatorDefinition definition) {
        var ref = IndicatorMapper.readImplementationRef(definition.getImplementationRef());
        if (ref != null) return ref;
        String target = IndicatorDefinitionSemantics.expectedModelFieldReferenceTarget(definition).orElse(null);
        if (target == null) return null;
        var match = java.util.regex.Pattern.compile("([0-9a-fA-F-]{36})@([1-9][0-9]*)#([A-Za-z_][A-Za-z0-9_]*)").matcher(target);
        if (!match.matches()) return null;
        return new IndicatorImplementationRef(java.util.UUID.fromString(match.group(1)), Integer.parseInt(match.group(2)), match.group(3));
    }
}
