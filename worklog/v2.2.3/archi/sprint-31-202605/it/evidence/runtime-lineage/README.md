# 运行血缘证据目录

本目录用于最终统一测试阶段归档 Sprint-31 F3 的证据。当前按用户要求暂不执行中途编译、测试和容器重建。

## 待最终执行

```bash
curl -sS -X POST http://127.0.0.1:18082/api/internal/lineage/openlineage \
  -H 'Content-Type: application/json' \
  -d @openlineage-sample.json
```

```bash
curl -sS 'http://127.0.0.1:18082/api/catalog/lineage/impact?datasetId={id}&withColumns=true&withJobs=true'
```

## 验收点

- `assetEvidence` 中自动发现资产为 `PENDING_GOVERNANCE` 或更严格的待治理状态。
- Addax/Airflow 血缘同步失败写入 `INGESTION_LINEAGE_SYNC` 审计失败事件。
- `withColumns=true` 返回 `columnLineages`。
- `withJobs=true` 返回运行 job 虚拟节点和边。
