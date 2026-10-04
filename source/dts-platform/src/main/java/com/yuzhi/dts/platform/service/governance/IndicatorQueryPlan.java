package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.*;
import java.util.*;
import java.util.function.Function;

/** A single statement aggregates each input before aligning public keys, preserving one DB snapshot. */
final class IndicatorQueryPlan {
    record Source(UUID datasourceId, String schema, String table, Set<String> fields) {}
    record Plan(String sql, UUID datasourceId, List<Object> parameters, List<VersionRef> versions) {}
    private record Node(String cte, Config config) {}
    private final Query query;
    private final Function<VersionRef, GovIndicatorDefinition> definitions;
    private final Function<GovIndicatorDefinition, Source> sources;
    private final ControlledIndicatorDerivationCompiler compiler;
    private final Map<VersionRef, GovIndicatorDefinition> loaded = new LinkedHashMap<>();
    private final Set<VersionRef> visiting = new HashSet<>();
    private final List<String> ctes = new ArrayList<>();
    private final List<Object> parameters = new ArrayList<>();
    private UUID datasource;
    private int sequence;
    private int expandedNodes;

    IndicatorQueryPlan(Query query, Function<VersionRef, GovIndicatorDefinition> definitions,
                       Function<GovIndicatorDefinition, Source> sources, ControlledIndicatorDerivationCompiler compiler) {
        this.query = query; this.definitions = definitions; this.sources = sources; this.compiler = compiler;
    }

    Plan build() {
        List<Node> roots = query.indicatorRefs().stream().map(ref -> node(ref, List.of(), 0)).toList();
        for (Node root : roots) compatibleTime(roots.get(0).config().timeBinding(), root.config().timeBinding());
        String keys = keys("");
        String union = String.join(" UNION ", roots.stream().map(n -> "SELECT " + keys + " FROM " + n.cte()).toList());
        ctes.add("result_keys AS (" + union + ")");
        List<String> select = new ArrayList<>();
        for (int i = 0; i < query.dimensions().size(); i++) select.add("k.k" + i + " AS " + identifier(query.dimensions().get(i)));
        List<String> joins = new ArrayList<>();
        for (int i = 0; i < roots.size(); i++) {
            String alias = "r" + i;
            joins.add(" LEFT JOIN " + roots.get(i).cte() + " " + alias + " ON " + joinKeys("k", alias));
            select.add(alias + ".value AS metric_" + i);
            select.add("CASE WHEN " + alias + ".present IS NULL THEN 'MISSING_DEPENDENCY_GROUP' ELSE " + alias + ".reason END AS metric_" + i + "_null_reason");
            select.add(alias + ".invalid AS metric_" + i + "_invalid");
        }
        String sql = "WITH " + String.join(", ", ctes) + " SELECT " + String.join(", ", select) +
            " FROM result_keys k" + String.join("", joins) + " ORDER BY " + keys("k.") + " LIMIT " + (query.limit() + 1);
        if (sql.length() > 200000) throw invalid("指标查询计划超过大小预算");
        return new Plan(sql, datasource, List.copyOf(parameters), List.copyOf(loaded.keySet()));
    }

    private GovIndicatorDefinition load(VersionRef ref) {
        if (loaded.containsKey(ref)) return loaded.get(ref);
        if (loaded.size() >= 100) throw invalid("指标依赖超过100个版本");
        var definition = definitions.apply(ref);
        loaded.put(ref, definition);
        return definition;
    }

    private Node node(VersionRef ref, List<Predicate> inherited, int depth) {
        if (++expandedNodes > 256) throw invalid("指标依赖展开超过256个节点");
        if (depth > 32 || !visiting.add(ref)) throw invalid("指标依赖循环或超过32层");
        try {
            var definition = load(ref);
            if (org.springframework.util.StringUtils.hasText(definition.getStaticFilter()) || org.springframework.util.StringUtils.hasText(definition.getDynamicFilterConfig()) || org.springframework.util.StringUtils.hasText(definition.getWindowFunction())) throw invalid("请将旧筛选或窗口配置转换为受控分析规则后再查询");
            Config config = IndicatorMapper.readAnalysisConfig(definition.getAnalysisConfig());
            if (config == null) throw invalid("该指标版本尚未配置分析维度与结果粒度，请创建修订");
            if (!config.dimensionBindings().keySet().containsAll(query.dimensions())) throw invalid("指标没有公共维度映射");
            List<Predicate> filters = new ArrayList<>(inherited);
            filters.addAll(config.predicates());
            for (VersionRef modifier : config.modifierRefs()) {
                var item = load(modifier);
                Config rule = IndicatorMapper.readAnalysisConfig(item.getAnalysisConfig());
                if (!"MODIFIER".equalsIgnoreCase(item.getCategory()) || rule == null || rule.predicates().isEmpty()) throw invalid("修饰词版本缺少受控限定规则");
                filters.addAll(rule.predicates());
            }
            if (filters.size() > 128) throw invalid("合并后的限定规则超过128项");
            if (config.periodRef() != null) {
                var period = load(config.periodRef());
                Config periodConfig = IndicatorMapper.readAnalysisConfig(period.getAnalysisConfig());
                if (!"TIME_PERIOD".equalsIgnoreCase(period.getCategory()) || periodConfig == null || !"RANGE".equals(periodConfig.periodMode())) {
                    throw invalid("首批时间周期仅支持明确区间，累计与滚动计算尚不支持");
                }
                if (query.timeRange() == null) throw invalid("请选择时间区间");
            }
            boolean formula = IndicatorDefinitionSemantics.isDerivedLike(definition) && "FORMULA".equals(definition.getExecutionMode());
            if (!formula) return leaf(definition, config, filters);
            List<Node> inputs = new ArrayList<>();
            Map<String, String> aliases = new LinkedHashMap<>();
            for (var source : IndicatorMapper.readSourceRefs(definition.getSourceRefs())) {
                if (source.sourceType() != IndicatorBusinessContextContract.SourceType.INDICATOR_VERSION) throw invalid("公式只能引用固定指标版本");
                VersionRef dependency = new VersionRef(UUID.fromString(source.sourceId()), source.sourceVersion());
                var inputDefinition = load(dependency);
                String code = inputDefinition.getCode();
                if (code == null || aliases.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(code))) throw invalid("依赖编码存在歧义");
                if (aliases.putIfAbsent(code, "d" + inputs.size() + ".value") != null) throw invalid("依赖编码重复");
                Node input = node(dependency, filters, depth + 1);
                compatibleTime(config.timeBinding(), input.config().timeBinding());
                inputs.add(input);
            }
            if (inputs.isEmpty()) throw invalid("公式缺少固定依赖");
            var formulaCodes = compiler.referencedMetricCodes(definition.getExpressionSql()).stream().map(code -> code.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            var declaredCodes = aliases.keySet().stream().map(code -> code.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            if (!formulaCodes.equals(declaredCodes)) throw invalid("公式与固定依赖版本必须完全对应");
            String name = "n" + sequence++;
            String union = String.join(" UNION ", inputs.stream().map(n -> "SELECT " + keys("") + " FROM " + n.cte()).toList());
            String join = "";
            List<String> missing = new ArrayList<>();
            List<String> invalids = new ArrayList<>();
            List<String> values = new ArrayList<>();
            int index = 0;
            for (var entry : aliases.entrySet()) {
                String alias = "d" + index;
                join += " LEFT JOIN " + inputs.get(index).cte() + " " + alias + " ON " + joinKeys("k", alias);
                missing.add("(" + alias + ".present IS NULL OR " + alias + ".reason = 'MISSING_DEPENDENCY_GROUP')");
                invalids.add("COALESCE(" + alias + ".invalid, false)");
                values.add((config.missingGroupsAsZero() ? "CASE WHEN " + alias + ".present IS NULL THEN 0 ELSE " + alias + ".value END" : alias + ".value") + " AS " + identifier(entry.getKey()));
                index++;
            }
            Map<String, String> formulaAliases = new LinkedHashMap<>();
            aliases.keySet().forEach(code -> formulaAliases.put(code, identifier(code)));
            var compiled = compiler.compileWithDiagnostics(definition.getExpressionSql(), formulaAliases);
            String expression = compiled.expression();
            String absent = "COALESCE((" + String.join(" OR ", missing) + "), false)";
            String value = !config.missingGroupsAsZero() ? "CASE WHEN missing THEN NULL ELSE (" + expression + ") END" : expression;
            ctes.add(name + " AS (SELECT " + keys("") + ", CAST(" + value + " AS numeric) AS value, 1 AS present, invalid, " +
                "CASE WHEN missing AND " + !config.missingGroupsAsZero() + " THEN 'MISSING_DEPENDENCY_GROUP' WHEN (" + expression + ") IS NULL AND (" + compiled.zeroDenominator() + ") THEN 'ZERO_DENOMINATOR' WHEN (" + expression + ") IS NULL THEN 'SQL_NULL' END AS reason " +
                "FROM (SELECT " + keys("k.") + ", " + String.join(", ", values) + ", " + absent + " AS missing, (" + String.join(" OR ", invalids) + ") AS invalid " +
                "FROM (" + union + ") k" + join + ") aligned)");
            return new Node(name, config);
        } finally { visiting.remove(ref); }
    }

    private Node leaf(GovIndicatorDefinition definition, Config config, List<Predicate> inherited) {
        if (IndicatorDefinitionSemantics.isDerivedLike(definition) && !"PRECOMPUTED".equals(definition.getExecutionMode())) throw invalid("指标版本未声明计算方式");
        Source source = sources.apply(definition);
        if (datasource != null && !datasource.equals(source.datasourceId())) throw invalid("首批不支持跨数据源指标计算");
        datasource = source.datasourceId();
        List<String> grouping = new ArrayList<>();
        for (String dimension : query.dimensions()) grouping.add(field(config, source, dimension));
        if (IndicatorDefinitionSemantics.isDerivedLike(definition) && !new HashSet<>(query.dimensions()).equals(new HashSet<>(config.resultGrain()))) {
            throw invalid("预计算指标必须按声明结果粒度查询，不能跨粒度汇总比率");
        }
        List<String> where = new ArrayList<>();
        List<Predicate> all = new ArrayList<>(query.filters()); all.addAll(inherited);
        for (Predicate predicate : all) where.add(predicate(config, source, predicate));
        if ("RANGE".equals(query.scope()) && config.timeBinding() != null && query.timeRange() == null) throw invalid("请明确选择业务时间范围");
        if (query.timeRange() != null) {
            TimeRange range = query.timeRange(); TimeBinding binding = config.timeBinding();
            if (binding == null || !Objects.equals(range.fieldRef(), binding.fieldRef()) || !Objects.equals(range.timezone(), binding.timezone())) throw invalid("业务时间或时区不兼容");
            java.time.ZoneId.of(range.timezone());
            var start = java.time.OffsetDateTime.parse(range.start()); var end = java.time.OffsetDateTime.parse(range.endExclusive());
            if (!start.isBefore(end)) throw invalid("时间起点必须早于终点");
            String field = physical(source, binding.fieldName());
            where.add(field + " >= CAST(? AS timestamptz) AND " + field + " < CAST(? AS timestamptz)"); parameters.add(start); parameters.add(end);
        }
        if ("LATEST_PERIOD".equals(query.scope()) && config.timeBinding() != null) {
            String timeField = physical(source, config.timeBinding().fieldName());
            String relation = identifier(source.schema()) + "." + identifier(source.table());
            where.add(timeField + " = (SELECT MAX(" + timeField + ") FROM " + relation + ")");
        }
        String measure = physical(source, definition.getMeasureField());
        String aggregation = String.valueOf(definition.getAggregationType()).toUpperCase(Locale.ROOT);
        boolean precomputed = IndicatorDefinitionSemantics.isDerivedLike(definition);
        if (!precomputed && !Set.of("SUM", "AVG", "MIN", "MAX", "COUNT", "COUNT_DISTINCT").contains(aggregation)) throw invalid("不支持的指标聚合方式");
        if (!precomputed && !config.allowedAggregations().contains(aggregation)) throw invalid("聚合方式未在指标版本中声明");
        String value = precomputed ? "MAX(" + measure + ")" : "COUNT_DISTINCT".equals(aggregation) ? "COUNT(DISTINCT " + measure + ")" : aggregation + "(" + measure + ")";
        String name = "n" + sequence++;
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < grouping.size(); i++) columns.add(grouping.get(i) + " AS k" + i);
        if (columns.isEmpty()) columns.add("1 AS k0");
        ctes.add(name + " AS (SELECT " + String.join(", ", columns) + ", CAST(" + value + " AS numeric) AS value, 1 AS present, " +
            (precomputed ? "COUNT(*) > 1" : "false") + " AS invalid, CASE WHEN COUNT(*) = 0 AND " + value + " IS NULL THEN 'EMPTY_DATA' WHEN " + value + " IS NULL THEN 'SQL_NULL' END AS reason FROM " +
            identifier(source.schema()) + "." + identifier(source.table()) + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where)) +
            (grouping.isEmpty() ? "" : " GROUP BY " + String.join(", ", grouping)) + ")");
        return new Node(name, config);
    }

    private String predicate(Config config, Source source, Predicate predicate) {
        if (predicate == null) throw invalid("限定规则不能为空");
        String field = field(config, source, predicate.fieldRef());
        Object raw = predicate.value();
        List<?> values = raw instanceof List<?> list ? list : Collections.singletonList(raw);
        if (values.isEmpty() || values.size() > 100) throw invalid("筛选值数量无效");
        for (Object value : values) {
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean) || String.valueOf(value).length() > 1024) throw invalid("筛选值必须为类型化标量");
        }
        String op = String.valueOf(predicate.op());
        String sql = switch (op) {
            case "EQ" -> { if (values.size() != 1) throw invalid("等于筛选只能有一个值"); yield field + " = ?"; }
            case "IN" -> field + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")";
            case "BETWEEN" -> { if (values.size() != 2) throw invalid("区间筛选必须有两个值"); yield field + " BETWEEN ? AND ?"; }
            default -> throw invalid("筛选操作符只支持 EQ、IN、BETWEEN");
        };
        if (parameters.size() + values.size() > 2048) throw invalid("指标查询绑定参数超过2048项");
        parameters.addAll(values); return "(" + sql + ")";
    }
    private String field(Config config, Source source, String publicKey) {
        String field = config.dimensionBindings().get(publicKey);
        if (field == null) throw invalid("该版本没有公共维度映射: " + publicKey);
        return physical(source, field);
    }
    private String physical(Source source, String name) {
        if (!source.fields().contains(name)) throw invalid("固定模型中不存在字段: " + name);
        return identifier(name);
    }
    private void compatibleTime(TimeBinding left, TimeBinding right) {
        if (query.timeRange() == null) return;
        if (left == null || right == null || !Objects.equals(left.fieldRef(), right.fieldRef()) || !Objects.equals(left.timezone(), right.timezone()) || !Objects.equals(left.grain(), right.grain())) throw invalid("上游业务时间角色、时区或粒度不兼容");
    }
    private String keys(String prefix) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < Math.max(1, query.dimensions().size()); i++) keys.add(prefix + "k" + i);
        return String.join(", ", keys);
    }
    private String joinKeys(String left, String right) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < Math.max(1, query.dimensions().size()); i++) keys.add(left + ".k" + i + " IS NOT DISTINCT FROM " + right + ".k" + i);
        return String.join(" AND ", keys);
    }
    private static String identifier(String value) {
        if (value == null || value.length() > 63 || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) throw invalid("字段标识无效");
        return '"' + value + '"';
    }
    private static IndicatorConflictException invalid(String message) { return new IndicatorConflictException(message); }
}
