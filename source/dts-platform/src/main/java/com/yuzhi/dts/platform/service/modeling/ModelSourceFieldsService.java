package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort.SourceField;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.*;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Resolves fields through existing owners; checks structured visual references without parsing SQL. */
@Service
public class ModelSourceFieldsService {
    private static final Pattern QUALIFIED = Pattern.compile("src_([0-9]+)\\.([A-Za-z_][A-Za-z0-9_]*)");
    private final ModelInputContextLoader loader;
    private final ModelSpecSourceValidationPort physical;

    public ModelSourceFieldsService(ModelInputContextLoader loader, ModelSpecSourceValidationPort physical) {
        this.loader = loader;
        this.physical = physical;
    }
    public record Source(int index, ImplementationInput input, String alias, String schemaState, List<SourceField> fields) {}
    public record FieldIssue(UUID modelSpecId, String reason, UpstreamModelInput expected, UpstreamModelInput actual,
                             String fieldPath, String targetField, String sourceField, String inputAlias) implements ModelInputInspectionContract.Diagnostic {}
    public record Directory(List<Source> sources, List<FieldIssue> issues) {}

    public Directory directory(String tenantId, ModelSpecView owner, InputMode mode, List<ImplementationInput> inputs) {
        if (mode == null || inputs == null || inputs.size() > 200 || inputs.stream().anyMatch(input -> input == null || input.mode() != mode)) {
            throw new ModelSpecException("MODEL_IMPLEMENTATION_INPUT_REQUEST_INVALID", "来源类型或数量不合法", ModelSpecException.Kind.BAD_REQUEST);
        }
        if (mode == InputMode.GENERATED) return new Directory(List.of(), List.of());
        List<Source> sources = new ArrayList<>();
        List<FieldIssue> issues = new ArrayList<>();
        ModelInputInspectionContract.Context context = null;
        if (mode == InputMode.UPSTREAM_MODEL) {
            List<UpstreamModelInput> pins = inputs.stream().map(UpstreamModelInput.class::cast).toList();
            var ids = new java.util.LinkedHashSet<UUID>();
            pins.forEach(pin -> ids.add(pin.modelSpecId()));
            ids.add(owner.id());
            context = loader.load(tenantId, ids, pins.stream().map(pin -> new ModelRevisionRef(pin.modelSpecId(), pin.revision())).toList());
        }
        for (int i = 0; i < inputs.size(); i++) {
            ImplementationInput input = inputs.get(i);
            List<SourceField> fields = List.of();
            String reason = null;
            boolean readable = true;
            if (input instanceof UpstreamModelInput pin) {
                reason = ModelImplementationInputPolicy.inspectUpstream(owner, context, pin.modelSpecId(), pin);
                readable = context.heads().containsKey(pin.modelSpecId());
                if (reason == null) {
                    ModelSpecView model = context.revisions().get(new ModelRevisionRef(pin.modelSpecId(), pin.revision()));
                    if (model != null) fields = model.fields().stream().map(field -> new SourceField(field.name(), field.dataType(), field.nullable())).toList();
                }
            } else if (input instanceof PhysicalAssetInput pin) {
                fields = physical.readFields(tenantId, owner.planId(), pin.sourceBindingId(), pin.resolvedVersion());
            }
            if (reason == null && fields.isEmpty()) reason = "SOURCE_SCHEMA_UNAVAILABLE";
            if (reason != null) issues.add(issue(input, reason, "inputs[" + i + "]", null, null));
            sources.add(new Source(i, readable ? input : null, "src_" + i, reason == null ? "RESOLVED" : "UNAVAILABLE", fields));
        }
        return new Directory(List.copyOf(sources), List.copyOf(issues));
    }

    public List<FieldIssue> check(String tenantId, ModelSpecView owner, SaveImplementationCommand command) {
        if (command == null || command.inputMode() == InputMode.GENERATED) return List.of();
        Directory directory = directory(tenantId, owner, command.inputMode(), command.inputs());
        List<FieldIssue> issues = new ArrayList<>(directory.issues());
        // Unavailable metadata is distinct from a positively missing field.
        if (!issues.isEmpty()) return List.copyOf(issues);
        Map<String, String> mappings = new LinkedHashMap<>();
        command.fieldMappings().forEach(mapping -> mappings.put(mapping.targetField(), mapping.sourceField()));
        Set<String> outputs = owner.fields().stream().map(ModelSpecContract.ModelField::name).collect(java.util.stream.Collectors.toSet());
        Map<String, Object> settings = command.settings();
        List<Map<?, ?>> aggregations = entries(settings.get("aggregations"));
        Set<String> aggregateTargets = aggregations.stream().map(row -> string(row.get("targetField"))).collect(java.util.stream.Collectors.toSet());
        for (var field : owner.fields()) {
            String mapped = mappings.get(field.name());
            if (mapped != null || !aggregateTargets.contains(field.name())) {
                checkField(directory, mapped == null ? field.name() : mapped, field.name(), "fieldMappings." + field.name(),
                    directory.sources().size() > 1, issues);
            }
        }
        for (int i = 0; i < aggregations.size(); i++) {
            Map<?, ?> aggregation = aggregations.get(i);
            String value = string(aggregation.get("sourceField"));
            checkField(directory, mappings.getOrDefault(value, value), string(aggregation.get("targetField")),
                "settings.aggregations[" + i + "].sourceField", false, issues);
        }
        List<Map<?, ?>> filters = entries(settings.get("filters"));
        for (int i = 0; i < filters.size(); i++) {
            String value = string(filters.get(i).get("field"));
            checkField(directory, mappings.getOrDefault(value, value), null, "settings.filters[" + i + "].field", false, issues);
        }
        List<Map<?, ?>> joins = entries(settings.get("joins"));
        for (int i = 0; i < joins.size(); i++) {
            for (String side : List.of("leftField", "rightField")) checkField(directory, string(joins.get(i).get(side)), null,
                "settings.joins[" + i + "]." + side, true, issues);
        }
        for (String key : List.of("groupBy", "deduplicateBy")) {
            if (settings.get(key) instanceof List<?> values) {
                for (int i = 0; i < values.size(); i++) {
                    String value = string(values.get(i));
                    if (!outputs.contains(value)) issues.add(new FieldIssue(null, "OUTPUT_FIELD_NOT_FOUND", null, null,
                        "settings." + key + "[" + i + "]", value, null, null));
                }
            }
        }
        return List.copyOf(issues);
    }

    public void requireValid(String tenantId, ModelSpecView owner, SaveImplementationCommand command) {
        List<FieldIssue> issues = check(tenantId, owner, command);
        if (!issues.isEmpty()) {
            if (issues.stream().anyMatch(issue -> !issue.reason().startsWith("SOURCE_") && !issue.reason().startsWith("OUTPUT_"))) {
                throw new ModelSpecException("MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE", "上游引用已变化或不可用，请检查并明确更新引用",
                    ModelSpecException.Kind.CONFLICT, ModelInputInspectionContract.details(issues));
            }
            String summary = issues.stream().limit(5).map(issue ->
                (issue.targetField() == null ? issue.fieldPath() : issue.targetField()) + "：" +
                (issue.sourceField() == null ? "来源字段目录或引用版本不可用" : "请检查来源字段 " + issue.sourceField())).collect(java.util.stream.Collectors.joining("；"));
            throw new ModelSpecException("MODEL_IMPLEMENTATION_SOURCE_FIELDS_INVALID", summary,
                ModelSpecException.Kind.UNPROCESSABLE, ModelInputInspectionContract.details(issues));
        }
    }

    private static void checkField(Directory directory, String value, String target, String path, boolean qualifiedRequired, List<FieldIssue> issues) {
        var matcher = QUALIFIED.matcher(value);
        String name = value;
        List<Source> candidates = directory.sources();
        String reason = null;
        if (matcher.matches()) {
            int index;
            try { index = Integer.parseInt(matcher.group(1)); } catch (NumberFormatException invalid) { index = -1; }
            candidates = index >= 0 && index < candidates.size() ? List.of(candidates.get(index)) : List.of();
            name = matcher.group(2);
        } else if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            reason = "SOURCE_EXPRESSION_UNSUPPORTED";
        } else if (qualifiedRequired) {
            reason = "SOURCE_FIELD_QUALIFIER_REQUIRED";
        }
        String fieldName = name;
        List<Source> matching = candidates.stream().filter(source -> source.fields().stream().anyMatch(field -> Objects.equals(field.name(), fieldName))).toList();
        if (reason == null) reason = matching.isEmpty() ? "SOURCE_FIELD_NOT_FOUND" : matching.size() > 1 ? "SOURCE_FIELD_AMBIGUOUS" : null;
        if (reason != null) {
            ImplementationInput input = candidates.size() == 1 ? candidates.getFirst().input() : null;
            issues.add(issue(input, reason, path, target, value));
        }
    }
    private static FieldIssue issue(ImplementationInput input, String reason, String path, String target, String source) {
        return new FieldIssue(input instanceof UpstreamModelInput pin ? pin.modelSpecId() : null, reason, null, null, path, target, source,
            source != null && source.contains(".") ? source.substring(0, source.indexOf('.')) : null);
    }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static List<Map<?, ?>> entries(Object value) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (value instanceof List<?> rows) rows.forEach(row -> { if (row instanceof Map<?, ?> map) result.add(map); });
        return result;
    }
}
