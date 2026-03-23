package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtOutputRelationService {

    private final ModelingSqlModelRepository modelRepository;
    private final DbtTargetConnectionFactory connectionFactory;

    public DbtOutputRelationService(
        ModelingSqlModelRepository modelRepository,
        DbtTargetConnectionFactory connectionFactory
    ) {
        this.modelRepository = modelRepository;
        this.connectionFactory = connectionFactory;
    }

    public DbtOutputRelationSummary analyze(UUID modelId) {
        ModelingSqlModel model = resolveModel(modelId);
        DbtTargetConnectionFactory.TargetWarehouse target = connectionFactory.resolveTarget();
        String schema = resolveSchema(model, target);
        String identifier = resolveIdentifier(model);
        String selector = resolveSelector(model);
        int downstreamRefs = countDownstreamRefs(model);
        try (Connection connection = connectionFactory.open(target)) {
            RelationDescriptor descriptor = inspectRelation(connection, schema, identifier);
            return new DbtOutputRelationSummary(
                model.getId(),
                model.getName(),
                selector,
                target.database(),
                schema,
                identifier,
                buildQualifiedName(schema, identifier),
                normalizeMaterialized(model.getMaterialized()),
                descriptor.type(),
                descriptor.exists(),
                descriptor.exists() && !"VIEW".equalsIgnoreCase(descriptor.type()),
                downstreamRefs,
                descriptor.exists() ? "已发现当前模型产出 relation" : "当前模型尚未生成产出 relation"
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("检查产出表失败: " + ex.getMessage(), ex);
        }
    }

    public DbtOutputRelationActionResult truncate(UUID modelId) {
        DbtOutputRelationSummary summary = analyze(modelId);
        if (!summary.exists()) {
            return new DbtOutputRelationActionResult(
                summary.modelId(),
                summary.modelName(),
                summary.selector(),
                summary.qualifiedName(),
                "truncate",
                false,
                false,
                "当前模型暂无产出表，无需清空"
            );
        }
        if (!summary.truncateAllowed()) {
            throw new IllegalStateException("当前产出 relation 为视图，不支持清空，请使用重建产出表");
        }
        DbtTargetConnectionFactory.TargetWarehouse target = connectionFactory.resolveTarget();
        try (Connection connection = connectionFactory.open(target); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE " + summary.qualifiedName());
            return new DbtOutputRelationActionResult(
                summary.modelId(),
                summary.modelName(),
                summary.selector(),
                summary.qualifiedName(),
                "truncate",
                true,
                true,
                "已清空当前模型产出表"
            );
        } catch (SQLException ex) {
            throw new IllegalStateException("清空产出表失败: " + ex.getMessage(), ex);
        }
    }

    public DbtOutputRelationActionResult prepareRebuild(UUID modelId) {
        DbtOutputRelationSummary summary = analyze(modelId);
        if (!summary.exists()) {
            return new DbtOutputRelationActionResult(
                summary.modelId(),
                summary.modelName(),
                summary.selector(),
                summary.qualifiedName(),
                "rebuild",
                false,
                false,
                "当前模型暂无产出 relation，将直接执行 dbt build"
            );
        }
        // S4-002: Do NOT drop the table before build. Instead, rely on dbt --full-refresh
        // which handles DROP+CREATE atomically within the dbt materialization.
        return new DbtOutputRelationActionResult(
            summary.modelId(),
            summary.modelName(),
            summary.selector(),
            summary.qualifiedName(),
            "rebuild",
            true,
            false,
            "将通过 dbt --full-refresh 安全重建产出 relation"
        );
    }

    private ModelingSqlModel resolveModel(UUID modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("请选择一个模型");
        }
        return modelRepository.findById(modelId).orElseThrow(() -> new IllegalArgumentException("模型不存在"));
    }

    private RelationDescriptor inspectRelation(Connection connection, String schema, String identifier) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(null, schema, identifier, new String[] { "TABLE", "VIEW" })) {
            while (tables.next()) {
                String type = stringVal(tables.getString("TABLE_TYPE"));
                if (StringUtils.hasText(type)) {
                    return new RelationDescriptor(true, type.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return new RelationDescriptor(false, null);
    }

    private int countDownstreamRefs(ModelingSqlModel model) {
        if (model == null || !StringUtils.hasText(model.getName())) {
            return 0;
        }
        String singleQuoteRef = "ref('" + model.getName() + "')";
        String doubleQuoteRef = "ref(\"" + model.getName() + "\")";
        List<ModelingSqlModel> models = modelRepository.findAll();
        return (int) models
            .stream()
            .filter(candidate -> candidate != null && candidate.getId() != null && !candidate.getId().equals(model.getId()))
            .filter(candidate -> {
                String sql = candidate.getSqlText();
                return StringUtils.hasText(sql) && (sql.contains(singleQuoteRef) || sql.contains(doubleQuoteRef));
            })
            .count();
    }

    private String resolveSchema(ModelingSqlModel model, DbtTargetConnectionFactory.TargetWarehouse target) {
        String schema = stringVal(model.getSchemaName());
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        return target.schema();
    }

    private String resolveIdentifier(ModelingSqlModel model) {
        String alias = stringVal(model.getAlias());
        if (StringUtils.hasText(alias)) {
            return alias;
        }
        String name = stringVal(model.getName());
        if (StringUtils.hasText(name)) {
            return name;
        }
        throw new IllegalArgumentException("模型名称不能为空");
    }

    private String resolveSelector(ModelingSqlModel model) {
        String selector = stringVal(model.getDagSelector());
        if (StringUtils.hasText(selector)) {
            return selector;
        }
        return "model:" + resolveIdentifier(model);
    }

    private String normalizeMaterialized(String materialized) {
        String normalized = stringVal(materialized);
        return StringUtils.hasText(normalized) ? normalized.toLowerCase(Locale.ROOT) : "table";
    }

    private String buildQualifiedName(String schema, String identifier) {
        if (StringUtils.hasText(schema)) {
            return quoteIdentifier(schema) + "." + quoteIdentifier(identifier);
        }
        return quoteIdentifier(identifier);
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null) {
            return "\"\"";
        }
        return "\"" + identifier.replace("\"", "") + "\"";
    }

    private String stringVal(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private record RelationDescriptor(boolean exists, String type) {}

    public record DbtOutputRelationSummary(
        UUID modelId,
        String modelName,
        String selector,
        String database,
        String schema,
        String identifier,
        String qualifiedName,
        String materialized,
        String relationType,
        boolean exists,
        boolean truncateAllowed,
        int downstreamRefCount,
        String message
    ) {}

    public record DbtOutputRelationActionResult(
        UUID modelId,
        String modelName,
        String selector,
        String qualifiedName,
        String action,
        boolean relationExists,
        boolean executed,
        String message
    ) {}
}
