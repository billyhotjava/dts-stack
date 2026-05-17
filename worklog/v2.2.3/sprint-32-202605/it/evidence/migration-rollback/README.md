# Sprint-32 迁移 Dry-run 与回滚证据

## 状态

**READY**：已提供 dry-run 端点和映射口径，生产自动迁移不属于 Sprint-32 必达。

## 覆盖范围

- `GET /api/metrics/migration/semantic-dry-run` 返回旧 `semantic_*` 到新 `metric_*` 的映射。
- dry-run 不写生产数据。
- 旧 `/api/semantic/**` 兼容窗口保留一个 Sprint，作为回滚路径。
- 已发布 dbt artifact 回滚仍通过 platform publish record，不由 metrics 直接删除生产表。

## 待执行命令

```bash
curl -sS http://127.0.0.1:18082/api/metrics/migration/semantic-dry-run
```

## 阻断条件

- dry-run 写入生产指标表。
- 迁移删除或覆盖旧 `semantic_*` 数据。
- 服务不可用时返回模糊 403/404，无法判断是未授权还是 metrics 服务异常。
