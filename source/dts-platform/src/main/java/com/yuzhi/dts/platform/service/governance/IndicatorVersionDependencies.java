package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Resolves immutable definitions; callers must also check current actor access to each identity. */
final class IndicatorVersionDependencies {
    private IndicatorVersionDependencies() {}

    static List<GovIndicatorDefinition> resolve(
        GovIndicatorDefinition target, GovIndicatorDefinitionRepository definitions,
        GovIndicatorVersionRepository versions, ObjectMapper mapper
    ) {
        var resolved = new LinkedHashMap<String, GovIndicatorDefinition>();
        for (var ref : IndicatorMapper.readSourceRefs(target.getSourceRefs())) {
            if (ref.sourceType() != IndicatorBusinessContextContract.SourceType.INDICATOR_VERSION) continue;
            UUID id;
            try { id = UUID.fromString(ref.sourceId()); }
            catch (Exception error) { throw new IndicatorConflictException("上游指标标识无效"); }
            var current = definitions.findById(id).orElseThrow(() -> new IndicatorConflictException("上游指标不存在"));
            var version = versions.findByIndicatorAndVersion(current, ref.sourceVersion())
                .orElseThrow(() -> new IndicatorConflictException("上游指标固定版本不存在"));
            if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())) throw new IndicatorConflictException("上游固定版本未发布");
            try {
                IndicatorUpsertRequest request = mapper.readerFor(IndicatorUpsertRequest.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(version.getSnapshotJson());
                var definition = new GovIndicatorDefinition();
                IndicatorMapper.apply(definition, request);
                definition.setId(id);
                definition.setVersion(ref.sourceVersion());
                definition.setStatus("PUBLISHED");
                String code = definition.getCode().toUpperCase(Locale.ROOT);
                if (resolved.putIfAbsent(code, definition) != null) throw new IndicatorConflictException("上游指标编码歧义");
            } catch (IndicatorConflictException error) { throw error; }
            catch (Exception error) { throw new IndicatorConflictException("上游版本快照无效"); }
        }
        return List.copyOf(resolved.values());
    }
}
