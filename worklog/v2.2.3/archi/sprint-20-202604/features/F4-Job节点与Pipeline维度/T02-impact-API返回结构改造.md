# T02: impact API 返回结构改造

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

让 `/api/catalog/lineage/impact` 在 `nodes[]` 中同时返回 dataset 与 job 节点；用 `kind` 字段区分；保持向后兼容。

## 技术设计

### 响应结构演进

```jsonc
{
  "nodes": [
    { "kind": "dataset", "id": "uuid", "name": "ods_orders", "layer": "ODS", ... },
    { "kind": "job",     "id": "uuid", "jobType": "DBT_MODEL", "name": "dwd_orders", "lastRunStatus": "SUCCESS", ... }
  ],
  "edges": [
    { "fromId": "...", "toId": "...", "kind": "DATASET_TO_JOB",   "relationType": "DBT" },
    { "fromId": "...", "toId": "...", "kind": "JOB_TO_DATASET",   "relationType": "DBT" }
  ],
  "columnEdges": [...],
  "impactStats": {...}
}
```

### 兼容策略

- 当请求 `?withJobs=false`（默认）时：仍返回老格式（`nodes[].kind` 全为 `dataset`，`edges[].kind` 全为 `DATASET_TO_DATASET`），后端把 dataset→job→dataset 折叠为 dataset→dataset
- `?withJobs=true`：返回 dataset + job 混合图
- 前端 F5-T02 切到 `withJobs=true`

### 服务层改造

`CatalogLineageService.computeImpact()`：

```java
public LineageGraphResponse computeImpact(ImpactRequest req) {
    Set<UUID> visitedDatasets = ...;
    Set<UUID> visitedJobs = ...;
    if (req.withJobs()) {
        bfsWithJobs(req, visitedDatasets, visitedJobs);
    } else {
        bfsDatasetOnly(req, visitedDatasets);
    }
    return assemble(visitedDatasets, visitedJobs, req);
}
```

### 性能

- 同样限制 depth ≤ 10
- 新增节点上限 hard cap 1000，超出截断 + warning header
- 加 BFS visited set 防环

## 影响范围

- 修改 `dts-platform/.../web/rest/CatalogLineageResource.java`
- 修改 `dts-platform/.../service/catalog/CatalogLineageService.java`
- 修改 `dts-platform/.../service/catalog/lineage/dto/LineageGraphResponse.java`
- 新增 `dts-platform/.../service/catalog/lineage/dto/JobNodeDto.java`

## 验证

- [ ] 单测：`withJobs=false` 与现有响应完全一致（snapshot 比对）
- [ ] 单测：`withJobs=true` 节点数 = dataset + job
- [ ] 环路（dataset A → job → dataset B → job → dataset A）BFS 不死循环
- [ ] 1000 节点截断生效，header 中含 `X-Truncated: true`
- [ ] 性能：500 节点图 P95 < 1s

## 完成标准

- [ ] API 改造完成并通过测试
- [ ] 旧调用链完全兼容
- [ ] 性能达标
- [ ] 文档更新
