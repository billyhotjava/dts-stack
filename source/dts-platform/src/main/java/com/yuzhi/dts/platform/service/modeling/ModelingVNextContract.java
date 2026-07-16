package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;

/** Cross-layer contract for the new ModelSpec-based modeling version. */
public final class ModelingVNextContract {

    public static final int CONTRACT_VERSION = 1;

    private ModelingVNextContract() {}

    public enum Layer {
        ODS,
        STG,
        DWD,
        DWS,
        ADS,
    }

    public enum ObjectKind {
        ENTITY,
        FACT,
        EVENT,
        SNAPSHOT,
        DIMENSION,
    }

    public enum ModelType {
        FACT,
        DIMENSION,
        SUMMARY,
        APPLICATION,
    }

    public enum ImplementationMode {
        DESIGNER_GENERATED,
        DBT_MANAGED,
    }

    public record Grain(String statement, List<String> keys) {}

    public record SourceRef(String kind, String ref, Layer layer) {}

    public record StandardBinding(String fieldName, String standardElementId, String referenceCode, String securityLevel) {}

    public record BusinessObject(
        String id,
        String code,
        String name,
        String description,
        ObjectKind objectKind,
        String processId,
        List<String> businessKey,
        Grain grain,
        List<SourceRef> sourceRefs,
        String status,
        ImplementationMode implementationMode
    ) {}

    public record ModelSpec(
        String id,
        String objectId,
        String processId,
        Layer layer,
        ModelType modelType,
        ImplementationMode implementationMode,
        String name,
        Grain grain,
        List<StandardBinding> standardBindings,
        List<SourceRef> sourceRefs,
        List<String> dimensions,
        List<String> metrics,
        String materialization,
        int revision,
        List<String> dependsOn,
        String legacyRef
    ) {
        public ModelSpec(
            String id,
            String objectId,
            String processId,
            Layer layer,
            ModelType modelType,
            ImplementationMode implementationMode,
            String name,
            Grain grain,
            List<StandardBinding> standardBindings,
            List<SourceRef> sourceRefs,
            List<String> dimensions,
            List<String> metrics,
            String materialization,
            int revision
        ) {
            this(id, objectId, processId, layer, modelType, implementationMode, name, grain, standardBindings, sourceRefs, dimensions, metrics, materialization, revision, List.of(), null);
        }

        public ModelSpec(
            String id,
            String objectId,
            String processId,
            Layer layer,
            ModelType modelType,
            ImplementationMode implementationMode,
            String name,
            Grain grain,
            List<StandardBinding> standardBindings,
            List<SourceRef> sourceRefs,
            int revision
        ) {
            this(id, objectId, processId, layer, modelType, implementationMode, name, grain, standardBindings, sourceRefs, List.of(), List.of(), "table", revision, List.of(), null);
        }
    }

    public record PjmFixture(int contractVersion, BusinessObject businessObject, ModelSpec modelSpec) {}

    public record LegacyModelRef(String modelId, String dbtUniqueId, String path, String status) {}

    public record PjmGoldenPathFixture(int contractVersion, BusinessObject businessObject, List<ModelSpec> modelSpecs, List<LegacyModelRef> legacyRefs) {}

    public static List<String> validateModelSpec(ModelSpec model) {
        List<String> issues = new ArrayList<>();
        if (model.layer() == Layer.DWD && (model.grain() == null || model.grain().keys() == null || model.grain().keys().isEmpty())) {
            issues.add("DWD 模型必须声明粒度键");
        }
        if (
            model.layer() == Layer.DWD &&
            model.implementationMode() == ImplementationMode.DESIGNER_GENERATED &&
            (model.standardBindings() == null || model.standardBindings().isEmpty())
        ) {
            issues.add("DWD 设计器模型至少绑定一个数据标准");
        }
        return List.copyOf(issues);
    }

    public static PjmFixture pjmProjectNodeFixture() {
        BusinessObject object = new BusinessObject(
            "pjm-project-node",
            "project_node",
            "项目节点",
            "项目节点计划闭环中的明细事实对象。",
            ObjectKind.FACT,
            "project-node-plan-loop",
            List.of("project_no", "subsystem", "node_task", "plan_date"),
            new Grain("一行代表一个项目在一个计划日期上的节点任务", List.of("project_no", "subsystem", "node_task", "plan_date")),
            List.of(new SourceRef("TABLE", "ods_project_subject_domain_v2", Layer.ODS)),
            "DRAFT",
            ImplementationMode.DESIGNER_GENERATED
        );
        ModelSpec spec = new ModelSpec(
            "pjm-project-node-dwd",
            object.id(),
            object.processId(),
            Layer.DWD,
            ModelType.FACT,
            ImplementationMode.DESIGNER_GENERATED,
            "project_node_detail",
            object.grain(),
            List.of(
                new StandardBinding("project_no", "std.project.code", null, null),
                new StandardBinding("plan_date", "std.date", null, null),
                new StandardBinding("node_type", "std.node.type", "NODE_TYPE", null),
                new StandardBinding("completion_status", "std.completion.status", "COMPLETION_STATUS", null),
                new StandardBinding("risk_level", "std.risk.level", "RISK_LEVEL", null),
                new StandardBinding("delay_days", "std.delay.days", null, null)
            ),
            object.sourceRefs(),
            1
        );
        return new PjmFixture(CONTRACT_VERSION, object, spec);
    }

    public static PjmGoldenPathFixture pjmGoldenPathFixture() {
        PjmFixture base = pjmProjectNodeFixture();
        ModelSpec dwd = new ModelSpec(
            base.modelSpec().id(),
            base.modelSpec().objectId(),
            base.modelSpec().processId(),
            base.modelSpec().layer(),
            base.modelSpec().modelType(),
            base.modelSpec().implementationMode(),
            base.modelSpec().name(),
            base.modelSpec().grain(),
            base.modelSpec().standardBindings(),
            base.modelSpec().sourceRefs(),
            base.modelSpec().dimensions(),
            base.modelSpec().metrics(),
            base.modelSpec().materialization(),
            base.modelSpec().revision(),
            List.of(),
            "model.pm_analytics_v3.biz_dwd_project_node_v2"
        );
        ModelSpec dws = new ModelSpec(
            "pjm-project-node-dws",
            base.businessObject().id(),
            base.businessObject().processId(),
            Layer.DWS,
            ModelType.SUMMARY,
            ImplementationMode.DESIGNER_GENERATED,
            "project_progress_monthly",
            new Grain("一行代表一个项目在一个月份的进度汇总", List.of("project_no", "plan_month")),
            List.of(),
            List.of(new SourceRef("DBT_MODEL", "project_node_detail", Layer.DWD)),
            List.of("project_no", "plan_month"),
            List.of("due_node_count", "completed_node_count", "delay_node_count"),
            "table",
            1,
            List.of(dwd.id()),
            "model.pm_analytics_v3.biz_dws_progress_monthly_v2"
        );
        ModelSpec ads = new ModelSpec(
            "pjm-project-node-ads",
            base.businessObject().id(),
            base.businessObject().processId(),
            Layer.ADS,
            ModelType.APPLICATION,
            ImplementationMode.DESIGNER_GENERATED,
            "project_progress_kpi",
            new Grain("一行代表一个项目在一个月份的经营进度 KPI", List.of("project_no", "plan_month")),
            List.of(),
            List.of(new SourceRef("DBT_MODEL", "project_progress_monthly", Layer.DWS)),
            List.of("project_no", "plan_month"),
            List.of("on_time_rate", "delay_rate", "progress_health_score"),
            "table",
            1,
            List.of(dws.id()),
            "model.pm_analytics_v3.biz_ads_progress_kpi_v2"
        );
        return new PjmGoldenPathFixture(
            CONTRACT_VERSION,
            base.businessObject(),
            List.of(dwd, dws, ads),
            List.of(
                new LegacyModelRef(dwd.id(), dwd.legacyRef(), "models/dwd/biz_dwd_project_node_v2.sql", "LEGACY_READONLY"),
                new LegacyModelRef(dws.id(), dws.legacyRef(), "models/dws/biz_dws_progress_monthly_v2.sql", "LEGACY_READONLY"),
                new LegacyModelRef(ads.id(), ads.legacyRef(), "models/ads/biz_ads_progress_kpi_v2.sql", "LEGACY_READONLY")
            )
        );
    }
}
