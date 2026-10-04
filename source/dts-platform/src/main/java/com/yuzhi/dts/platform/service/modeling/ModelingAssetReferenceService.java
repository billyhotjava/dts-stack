package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.ModelingTemplate;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingTemplateRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort.IndicatorView;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort.ReferenceCodeView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ModelingAssetReferenceService {

    private static final Pattern STD_CODE_PATTERN = Pattern.compile("(?i)(?:\\bSTD\\b|标准)\\s*[:：]\\s*([A-Za-z0-9_\\-\\.]+)");

    private final MetadataStandardRepository metadataStandardRepository;
    private final ModelingTemplateRepository modelingTemplateRepository;
    private final ModelingGlossaryTermRepository modelingGlossaryTermRepository;
    private final GovernanceReferenceAssetReadPort governanceAssets;
    private final DataStandardRepository dataStandardRepository;

    public ModelingAssetReferenceService(
        MetadataStandardRepository metadataStandardRepository,
        ModelingTemplateRepository modelingTemplateRepository,
        ModelingGlossaryTermRepository modelingGlossaryTermRepository,
        GovernanceReferenceAssetReadPort governanceAssets,
        DataStandardRepository dataStandardRepository
    ) {
        this.metadataStandardRepository = metadataStandardRepository;
        this.modelingTemplateRepository = modelingTemplateRepository;
        this.modelingGlossaryTermRepository = modelingGlossaryTermRepository;
        this.governanceAssets = governanceAssets;
        this.dataStandardRepository = dataStandardRepository;
    }

    public Map<String, Object> glossaryReferences(ModelingGlossaryTerm term) {
        String needle = StringUtils.trimToNull(term.getCode());
        if (needle == null) {
            needle = StringUtils.trimToNull(term.getName());
        }
        if (needle == null && term.getId() != null) {
            needle = term.getId().toString();
        }
        String keyword = StringUtils.defaultString(needle).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> items = new ArrayList<>();

        for (MetadataStandard standard : metadataStandardRepository.findAll()) {
            if (
                matchKeyword(standard.getFieldNameCn(), keyword) ||
                matchKeyword(standard.getFieldNameEn(), keyword) ||
                matchKeyword(standard.getDescription(), keyword) ||
                matchKeyword(standard.getDomain(), keyword) ||
                matchKeyword(standard.getCodeSet(), keyword)
            ) {
                items.add(
                    referenceItem(
                        "METADATA_STANDARD",
                        "数据元",
                        toStringId(standard.getId()),
                        standard.getFieldNameEn(),
                        standard.getFieldNameCn(),
                        "/governance/standards/elements",
                        "数据元定义命中术语关键字"
                    )
                );
            }
        }

        for (ModelingTemplate template : modelingTemplateRepository.findAll()) {
            if (
                matchKeyword(template.getName(), keyword) ||
                matchKeyword(template.getNamingRule(), keyword) ||
                matchKeyword(template.getFieldsTemplate(), keyword) ||
                matchKeyword(template.getReviewChecklist(), keyword)
            ) {
                items.add(
                    referenceItem(
                        "TEMPLATE",
                        "标准模板",
                        toStringId(template.getId()),
                        null,
                        template.getName(),
                        "/governance/templates",
                        "模板配置命中术语关键字"
                    )
                );
            }
        }

        for (ReferenceCodeView directory : governanceAssets.referenceCodes()) {
            if (
                matchKeyword(directory.name(), keyword) ||
                matchKeyword(directory.code(), keyword) ||
                matchKeyword(directory.businessCatalog(), keyword)
            ) {
                items.add(
                    referenceItem(
                        "REFERENCE_CODE",
                        "公共码表",
                        directory.id(),
                        directory.code(),
                        directory.name(),
                        "/governance/standards/reference",
                        "码表定义命中术语关键字"
                    )
                );
            }
        }

        for (DataStandard standard : dataStandardRepository.findAll()) {
            if (
                matchKeyword(standard.getName(), keyword) ||
                matchKeyword(standard.getCode(), keyword) ||
                matchKeyword(standard.getDescription(), keyword) ||
                matchKeyword(standard.getTags(), keyword)
            ) {
                items.add(
                    referenceItem(
                        "DATA_STANDARD",
                        "数据标准",
                        toStringId(standard.getId()),
                        standard.getCode(),
                        standard.getName(),
                        "/modeling/standards",
                        "数据标准命中术语关键字"
                    )
                );
            }
        }

        for (IndicatorView indicator : governanceAssets.indicators()) {
            if (
                matchKeyword(indicator.name(), keyword) ||
                matchKeyword(indicator.code(), keyword) ||
                matchKeyword(indicator.definition(), keyword) ||
                matchKeyword(indicator.expressionSql(), keyword) ||
                matchKeyword(indicator.tags(), keyword)
            ) {
                items.add(
                    referenceItem(
                        "INDICATOR",
                        "指标",
                        toStringId(indicator.id()),
                        indicator.code(),
                        indicator.name(),
                        "/governance/indicators/dictionary",
                        "指标定义命中术语关键字"
                    )
                );
            }
        }

        return referencePayload("GLOSSARY_TERM", toStringId(term.getId()), term.getName(), items);
    }

    public Map<String, Object> metadataStandardReferences(MetadataStandard standard) {
        Set<String> keys = new LinkedHashSet<>();
        addNormalizedKey(keys, toStringId(standard.getId()));
        addNormalizedKey(keys, standard.getFieldNameEn());
        addNormalizedKey(keys, standard.getFieldNameCn());

        List<Map<String, Object>> items = new ArrayList<>();
        for (ModelingTemplate template : modelingTemplateRepository.findAll()) {
            Set<String> refTokens = tokenize(template.getMetadataStandardIds());
            if (containsAny(refTokens, keys)) {
                items.add(
                    referenceItem(
                        "TEMPLATE",
                        "标准模板",
                        toStringId(template.getId()),
                        null,
                        template.getName(),
                        "/governance/templates",
                        "模板 metadataStandardIds 引用了该数据元"
                    )
                );
                continue;
            }
            String text = String.join(
                " ",
                safe(template.getFieldsTemplate()),
                safe(template.getReviewChecklist()),
                safe(template.getNamingRule())
            );
            if (containsAnyKeyword(text, keys)) {
                items.add(
                    referenceItem(
                        "TEMPLATE",
                        "标准模板",
                        toStringId(template.getId()),
                        null,
                        template.getName(),
                        "/governance/templates",
                        "模板文本配置命中了该数据元"
                    )
                );
            }
        }

        String codeSet = StringUtils.trimToNull(standard.getCodeSet());
        if (codeSet != null) {
            governanceAssets
                .referenceCodeByCode(codeSet)
                .ifPresent(directory ->
                    items.add(
                        referenceItem(
                            "REFERENCE_CODE",
                            "公共码表",
                            directory.id(),
                            directory.code(),
                            directory.name(),
                            "/governance/standards/reference",
                            "数据元 codeSet 指向该码表"
                        )
                    )
                );
            governanceAssets
                .referenceCodeById(codeSet)
                .ifPresent(directory ->
                    items.add(
                        referenceItem(
                            "REFERENCE_CODE",
                            "公共码表",
                            directory.id(),
                            directory.code(),
                            directory.name(),
                            "/governance/standards/reference",
                            "数据元 codeSet 指向该码表"
                        )
                    )
                );
        }

        return referencePayload("METADATA_STANDARD", toStringId(standard.getId()), standard.getFieldNameCn(), deduplicate(items));
    }

    public Map<String, Object> templateReferences(ModelingTemplate template) {
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> metadataRefs = tokenize(template.getMetadataStandardIds());

        if (!metadataRefs.isEmpty()) {
            for (MetadataStandard standard : metadataStandardRepository.findAll()) {
                if (
                    containsAny(
                        metadataRefs,
                        List.of(
                            normalize(toStringId(standard.getId())),
                            normalize(standard.getFieldNameEn()),
                            normalize(standard.getFieldNameCn())
                        )
                    )
                ) {
                    items.add(
                        referenceItem(
                            "METADATA_STANDARD",
                            "数据元",
                            toStringId(standard.getId()),
                            standard.getFieldNameEn(),
                            standard.getFieldNameCn(),
                            "/governance/standards/elements",
                            "模板 metadataStandardIds 引用了该数据元"
                        )
                    );
                }
            }
        }

        Set<String> stdCodeRefs = extractStdCodes(template.getFieldsTemplate());
        if (!stdCodeRefs.isEmpty()) {
            for (ReferenceCodeView directory : governanceAssets.referenceCodes()) {
                String code = normalize(directory.code());
                String id = normalize(directory.id());
                if (stdCodeRefs.contains(code) || stdCodeRefs.contains(id)) {
                    items.add(
                        referenceItem(
                            "REFERENCE_CODE",
                            "公共码表",
                            directory.id(),
                            directory.code(),
                            directory.name(),
                            "/governance/standards/reference",
                            "模板字段定义引用了该码表"
                        )
                    );
                }
            }
        }

        String text = String.join(" ", safe(template.getFieldsTemplate()), safe(template.getNamingRule()), safe(template.getReviewChecklist()));
        for (ModelingGlossaryTerm term : modelingGlossaryTermRepository.findAll()) {
            String code = StringUtils.trimToNull(term.getCode());
            String name = StringUtils.trimToNull(term.getName());
            if ((code != null && matchKeyword(text, normalize(code))) || (name != null && matchKeyword(text, normalize(name)))) {
                items.add(
                    referenceItem(
                        "GLOSSARY_TERM",
                        "业务术语",
                        toStringId(term.getId()),
                        term.getCode(),
                        term.getName(),
                        "/governance/standards/glossary",
                        "模板文本命中了术语"
                    )
                );
            }
        }

        return referencePayload("TEMPLATE", toStringId(template.getId()), template.getName(), deduplicate(items));
    }

    public Map<String, Object> referenceCodeReferences(String directoryId, String directoryCode, String directoryName) {
        String codeTypeCode = normalize(directoryCode);
        String codeTypeId = normalize(directoryId);
        List<Map<String, Object>> items = new ArrayList<>();

        for (MetadataStandard standard : metadataStandardRepository.findAll()) {
            String codeSet = normalize(standard.getCodeSet());
            if (codeSet != null && (codeSet.equals(codeTypeCode) || codeSet.equals(codeTypeId))) {
                items.add(
                    referenceItem(
                        "METADATA_STANDARD",
                        "数据元",
                        toStringId(standard.getId()),
                        standard.getFieldNameEn(),
                        standard.getFieldNameCn(),
                        "/governance/standards/elements",
                        "数据元 codeSet 引用了该码表"
                    )
                );
            }
        }

        for (DataStandard standard : dataStandardRepository.findAll()) {
            String codeSet = normalize(standard.getCodeSet());
            if (codeSet != null && (codeSet.equals(codeTypeCode) || codeSet.equals(codeTypeId))) {
                items.add(
                    referenceItem(
                        "DATA_STANDARD",
                        "数据标准",
                        toStringId(standard.getId()),
                        standard.getCode(),
                        standard.getName(),
                        "/modeling/standards",
                        "数据标准 codeSet 引用了该码表"
                    )
                );
            }
        }

        for (ModelingTemplate template : modelingTemplateRepository.findAll()) {
            Set<String> stdCodes = extractStdCodes(template.getFieldsTemplate());
            if (stdCodes.contains(codeTypeCode) || stdCodes.contains(codeTypeId)) {
                items.add(
                    referenceItem(
                        "TEMPLATE",
                        "标准模板",
                        toStringId(template.getId()),
                        null,
                        template.getName(),
                        "/governance/templates",
                        "模板字段定义引用了该码表"
                    )
                );
            }
        }

        return referencePayload("REFERENCE_CODE", directoryId, directoryName, deduplicate(items));
    }

    public boolean hasReferences(Map<String, Object> payload) {
        return countReferences(payload) > 0;
    }

    public int countReferences(Map<String, Object> payload) {
        if (payload == null) {
            return 0;
        }
        Object total = payload.get("totalReferences");
        if (total instanceof Number number) {
            return number.intValue();
        }
        Object items = payload.get("items");
        if (items instanceof Collection<?> collection) {
            return collection.size();
        }
        return 0;
    }

    public String summarizeReferences(Map<String, Object> payload, int maxItems) {
        if (payload == null) {
            return "";
        }
        Object items = payload.get("items");
        if (!(items instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String label = StringUtils.trimToNull(String.valueOf(map.get("label")));
            String name = StringUtils.trimToNull(String.valueOf(map.get("name")));
            if (name == null) {
                continue;
            }
            names.add((label != null ? label + ":" : "") + name);
            if (names.size() >= maxItems) {
                break;
            }
        }
        return String.join("、", names);
    }

    private Map<String, Object> referencePayload(String targetType, String targetId, String targetName, List<Map<String, Object>> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", targetType);
        payload.put("targetId", targetId);
        payload.put("targetName", targetName);
        payload.put("totalReferences", items.size());
        payload.put("items", items);
        return payload;
    }

    private Map<String, Object> referenceItem(
        String type,
        String label,
        String id,
        String code,
        String name,
        String path,
        String reason
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("label", label);
        row.put("id", id);
        row.put("code", code);
        row.put("name", name);
        row.put("path", path);
        row.put("reason", reason);
        return row;
    }

    private List<Map<String, Object>> deduplicate(List<Map<String, Object>> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : input) {
            if (row == null) {
                continue;
            }
            String key = String.join(
                "::",
                safe((String) row.get("type")),
                safe((String) row.get("id")),
                safe((String) row.get("code")),
                safe((String) row.get("name"))
            );
            unique.putIfAbsent(key, row);
        }
        return new ArrayList<>(unique.values());
    }

    private Set<String> tokenize(String raw) {
        String text = StringUtils.trimToEmpty(raw);
        if (text.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : text.split("[,，;；\\s]+")) {
            String normalized = normalize(token);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        return out;
    }

    private Set<String> extractStdCodes(String text) {
        String source = StringUtils.trimToEmpty(text);
        if (source.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = STD_CODE_PATTERN.matcher(source);
        while (matcher.find()) {
            String code = normalize(matcher.group(1));
            if (code != null) {
                out.add(code);
            }
        }
        return out;
    }

    private boolean containsAny(Set<String> source, Collection<String> candidates) {
        if (source == null || source.isEmpty() || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (normalized != null && source.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyKeyword(String text, Collection<String> candidates) {
        String normalizedText = normalize(text);
        if (normalizedText == null || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (normalized != null && normalizedText.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchKeyword(String value, String keyword) {
        String left = normalize(value);
        String right = normalize(keyword);
        return left != null && right != null && left.contains(right);
    }

    private String normalize(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private void addNormalizedKey(Set<String> out, String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            out.add(normalized);
        }
    }

    private String toStringId(UUID id) {
        return id != null ? id.toString() : null;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
