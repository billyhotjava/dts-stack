package com.yuzhi.dts.platform.service.modeling.imports;

import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Column;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionMode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ConversionResult;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.DbtMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Defaults;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.Grain;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SqlArtifact;
import java.util.List;
import java.util.Map;

public final class ModelPackageFixtures {

    private ModelPackageFixtures() {}

    public static SemanticMetadata completeSemantics() {
        return new SemanticMetadata(
            "FACT",
            "DWD",
            new Grain("每行代表一条预算台账快照", List.of("budget_id")),
            "PERIODIC_SNAPSHOT",
            new ModelPackageContract.TimeSemantics("SNAPSHOT_DATE", List.of("source_imported_at")),
            "PROJECT_MANAGEMENT",
            List.of(new SourceRef("DBT_MODEL", "stg_budget", "STG")),
            List.of("预算执行分析"),
            Map.of("budget_id", "BUSINESS_KEY"),
            "CONFORMED",
            "fixtures/semantic-overrides.json",
            false
        );
    }

    public static PackageModel model(String uniqueId, String dependency, String effectiveSql, SemanticMetadata semantics) {
        String sqlChecksum = ModelPackageChecksum.sha256Text(effectiveSql);
        return new PackageModel(
            uniqueId,
            uniqueId.substring(uniqueId.lastIndexOf('.') + 1),
            "预算事实模型",
            "models/dwd/budget.sql",
            new SqlArtifact(
                effectiveSql,
                sqlChecksum,
                null,
                null,
                effectiveSql,
                sqlChecksum,
                "PROJECT_FILE"
            ),
            "table",
            Map.of("materialized", "table"),
            List.of("budget"),
            List.of(new Column("budget_id", "预算主键", "varchar", "BUSINESS_KEY", List.of("not_null"))),
            List.of("not_null_budget_id"),
            dependency == null ? List.of() : List.of(dependency),
            semantics,
            new ConversionResult(ConversionMode.DESIGNER_GENERATED, List.of("SAFE_SQL_SUBSET"))
        );
    }

    public static ModelPackage validPackage() {
        PackageModel model = model(
            "model.pjm.budget",
            "source.pjm.budget",
            "select budget_id from source_budget",
            completeSemantics()
        );
        ModelPackage withoutChecksum = new ModelPackage(
            ModelPackageContract.SCHEMA_VERSION,
            "pjm-budget-v1",
            null,
            new DbtMetadata("pjm", "1.0.0", "v12", "postgres"),
            new Defaults(null, null),
            List.of(
                new ModelPackageContract.SourceNode(
                    "source.pjm.budget",
                    "budget",
                    "models/sources.yml",
                    List.of(new Column("budget_id", null, "varchar", null, List.of()))
                )
            ),
            List.of(),
            List.of(model),
            List.of()
        );
        return ModelPackageChecksum.withChecksum(withoutChecksum);
    }
}
