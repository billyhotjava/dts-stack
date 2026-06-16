package com.yuzhi.dts.platform.service.goldenchain.modeling;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoldenChainDbtMigrationInventoryService {

    public GoldenChainDbtMigrationReport inventory(List<GoldenChainDbtAssetSnapshot> snapshots) {
        List<GoldenChainDbtMigrationItem> items = (snapshots == null ? List.<GoldenChainDbtAssetSnapshot>of() : snapshots)
            .stream()
            .map(this::toItem)
            .toList();
        int high = countRisk(items, GoldenChainDbtMigrationRisk.HIGH);
        int medium = countRisk(items, GoldenChainDbtMigrationRisk.MEDIUM);
        int low = countRisk(items, GoldenChainDbtMigrationRisk.LOW);
        return new GoldenChainDbtMigrationReport(items.size(), high, medium, low, items);
    }

    private GoldenChainDbtMigrationItem toItem(GoldenChainDbtAssetSnapshot snapshot) {
        if (snapshot.goldenChainManaged()) {
            return new GoldenChainDbtMigrationItem(
                snapshot.packageName(),
                snapshot.modelName(),
                List.of(GoldenChainDbtMigrationStatus.MANAGED),
                GoldenChainDbtMigrationRisk.LOW,
                "已纳入黄金链路，保持例行发布门禁"
            );
        }

        List<GoldenChainDbtMigrationStatus> statuses = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        if (!snapshot.sourceRegistered()) {
            statuses.add(GoldenChainDbtMigrationStatus.NEED_SOURCE);
            actions.add("补齐 dbt source");
        }
        if (!snapshot.catalogAssetRegistered()) {
            statuses.add(GoldenChainDbtMigrationStatus.NEED_ASSET);
            actions.add("登记资产");
        }
        if (!snapshot.lineageReady()) {
            statuses.add(GoldenChainDbtMigrationStatus.NEED_LINEAGE);
            actions.add("补齐血缘");
        }
        if (!snapshot.runtimeGraphReady()) {
            statuses.add(GoldenChainDbtMigrationStatus.NEED_RUNTIME_GRAPH);
            actions.add("补齐运行图");
        }
        statuses.add(GoldenChainDbtMigrationStatus.NEED_CONFIRMATION);
        actions.add("人工确认迁移优先级");
        return new GoldenChainDbtMigrationItem(
            snapshot.packageName(),
            snapshot.modelName(),
            statuses,
            risk(statuses),
            String.join("；", actions)
        );
    }

    private GoldenChainDbtMigrationRisk risk(List<GoldenChainDbtMigrationStatus> statuses) {
        long missingLinks = statuses.stream().filter(status -> status != GoldenChainDbtMigrationStatus.NEED_CONFIRMATION).count();
        if (missingLinks >= 3) {
            return GoldenChainDbtMigrationRisk.HIGH;
        }
        if (missingLinks >= 1) {
            return GoldenChainDbtMigrationRisk.MEDIUM;
        }
        return GoldenChainDbtMigrationRisk.LOW;
    }

    private int countRisk(List<GoldenChainDbtMigrationItem> items, GoldenChainDbtMigrationRisk risk) {
        return (int) items.stream().filter(item -> item.riskLevel() == risk).count();
    }
}
