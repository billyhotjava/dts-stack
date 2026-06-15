package com.yuzhi.dts.platform.service.goldenchain.modeling;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GoldenChainOdsDbtSourceContractService {

    private static final String UNASSIGNED_OWNER = "待分配";

    public GoldenChainOdsDbtSourceCandidate buildCandidate(GoldenChainOdsDbtSourceRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        List<String> blockers = collectBlockers(request);
        String owner = hasText(request.owner()) ? request.owner() : UNASSIGNED_OWNER;
        if (!blockers.isEmpty()) {
            return new GoldenChainOdsDbtSourceCandidate(
                false,
                request.sourceName(),
                request.schemaName(),
                request.tableName(),
                owner,
                request.refreshCadence(),
                request.columns(),
                "",
                blockers,
                GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.MODEL_READY,
                    owner,
                    GoldenChainBlockerCode.BLOCKED_MODEL,
                    String.join("；", blockers)
                )
            );
        }

        return new GoldenChainOdsDbtSourceCandidate(
            true,
            request.sourceName(),
            request.schemaName(),
            request.tableName(),
            owner,
            request.refreshCadence(),
            request.columns(),
            buildSourceYaml(request, owner),
            List.of(),
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.MODEL_READY,
                owner,
                "dbt-source://%s/%s".formatted(request.sourceName(), request.tableName())
            )
        );
    }

    private List<String> collectBlockers(GoldenChainOdsDbtSourceRequest request) {
        List<String> blockers = new ArrayList<>();
        if (!hasText(request.owner())) {
            blockers.add("缺少 owner，不能进入 dbt source 发布");
        }
        if (request.columns().isEmpty()) {
            blockers.add("缺少字段快照，不能生成 dbt source 候选配置");
        }
        if (isStg(request.schemaName()) || isStg(request.tableName())) {
            blockers.add("STG 只能作为 dbt 内部层或诊断层，不能作为普通业务 source 发布");
        }
        return blockers;
    }

    private String buildSourceYaml(GoldenChainOdsDbtSourceRequest request, String owner) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("version: 2\n");
        yaml.append("sources:\n");
        yaml.append("  - name: ").append(request.sourceName()).append('\n');
        yaml.append("    schema: ").append(request.schemaName()).append('\n');
        yaml.append("    meta:\n");
        yaml.append("      owner: ").append(owner).append('\n');
        if (hasText(request.refreshCadence())) {
            yaml.append("      refresh_cadence: ").append(request.refreshCadence()).append('\n');
        }
        yaml.append("    tables:\n");
        yaml.append("      - name: ").append(request.tableName()).append('\n');
        yaml.append("        meta:\n");
        yaml.append("          ods_managed: true\n");
        yaml.append("        columns:\n");
        for (GoldenChainOdsColumnSnapshot column : request.columns()) {
            yaml.append("          - name: ").append(column.name()).append('\n');
            yaml.append("            data_type: ").append(column.dataType()).append('\n');
            yaml.append("            nullable: ").append(column.nullable()).append('\n');
            if (hasText(column.description())) {
                yaml.append("            description: ").append(column.description()).append('\n');
            }
        }
        return yaml.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isStg(String value) {
        return value != null && value.trim().toLowerCase().startsWith("stg");
    }
}
