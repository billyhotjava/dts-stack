package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.CompilerModel;
import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelingCompilerContract.StandardBinding;
import java.util.List;

/** PJM compiler inputs for tests only; never part of the runtime modeling contract. */
public final class PjmModelingFixture {

    private PjmModelingFixture() {}

    public record Fixture(CompilerModel compilerModel) {}

    public record GoldenPathFixture(List<CompilerModel> compilerModels) {}

    public static Fixture projectNode() {
        CompilerModel model = new CompilerModel(
            "pjm-project-node-dwd",
            Layer.DWD,
            ModelType.FACT,
            ImplementationMode.DESIGNER_GENERATED,
            "project_node_detail",
            new Grain(
                "一行代表一个项目在一个计划日期上的节点任务",
                List.of("project_no", "subsystem", "node_task", "plan_date")
            ),
            List.of(
                new StandardBinding("project_no", "std.project.code"),
                new StandardBinding("plan_date", "std.date"),
                new StandardBinding("node_type", "std.node.type"),
                new StandardBinding("completion_status", "std.completion.status"),
                new StandardBinding("risk_level", "std.risk.level"),
                new StandardBinding("delay_days", "std.delay.days")
            ),
            List.of(new SourceRef("TABLE", "ods_project_subject_domain_v2", Layer.ODS)),
            List.of(),
            List.of(),
            1
        );
        return new Fixture(model);
    }

    public static GoldenPathFixture goldenPath() {
        CompilerModel dwd = projectNode().compilerModel();
        CompilerModel dws = new CompilerModel(
            "pjm-project-node-dws",
            Layer.DWS,
            ModelType.SUMMARY,
            ImplementationMode.DESIGNER_GENERATED,
            "project_progress_monthly",
            new Grain("一行代表一个项目在一个月份的进度汇总", List.of("project_no", "plan_month")),
            List.of(),
            List.of(new SourceRef("DBT_MODEL", "project_node_detail", Layer.DWD)),
            List.of("project_no", "plan_month"),
            List.of("due_node_count", "completed_node_count", "delay_node_count"),
            1
        );
        CompilerModel ads = new CompilerModel(
            "pjm-project-node-ads",
            Layer.ADS,
            ModelType.APPLICATION,
            ImplementationMode.DESIGNER_GENERATED,
            "project_progress_kpi",
            new Grain("一行代表一个项目在一个月份的经营进度 KPI", List.of("project_no", "plan_month")),
            List.of(),
            List.of(new SourceRef("DBT_MODEL", "project_progress_monthly", Layer.DWS)),
            List.of("project_no", "plan_month"),
            List.of("on_time_rate", "delay_rate", "progress_health_score"),
            1
        );
        return new GoldenPathFixture(List.of(dwd, dws, ads));
    }
}
