package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.BusinessObject;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.LegacyModelRef;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.ModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.ObjectKind;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelingVNextContract.StandardBinding;
import java.util.List;

/** PJM example data for tests only; never part of the generic runtime contract. */
public final class PjmModelingFixture {

    private PjmModelingFixture() {}

    public record Fixture(int contractVersion, BusinessObject businessObject, ModelSpec modelSpec) {}

    public record GoldenPathFixture(int contractVersion, BusinessObject businessObject, List<ModelSpec> modelSpecs, List<LegacyModelRef> legacyRefs) {}

    public static Fixture projectNode() {
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
        return new Fixture(ModelingVNextContract.CONTRACT_VERSION, object, spec);
    }

    public static GoldenPathFixture goldenPath() {
        Fixture base = projectNode();
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
        return new GoldenPathFixture(
            ModelingVNextContract.CONTRACT_VERSION,
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
