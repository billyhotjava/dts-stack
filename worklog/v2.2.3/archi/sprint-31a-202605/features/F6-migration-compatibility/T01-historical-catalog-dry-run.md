# T01: 历史 Catalog 迁移 dry-run

**优先级**: P0
**状态**: DONE
**依赖**: F1-F4

## 目标

生成历史资产迁移 dry-run 报告，说明哪些资产可自动映射、哪些冲突、哪些待治理。

## 技术设计

- 不执行破坏性写入。
- 输出资产数量、冲突、缺失治理字段、建议状态。
- 报告进入 IT evidence。
- 暴露只读维护端点 `GET /api/catalog/assets-v2/migration/dry-run`。

## 影响范围

- migration/dry-run service or script
- worklog evidence

## 验证

- [x] dry-run 可重复执行。
- [x] 报告包含冲突和待治理资产。

## 完成标准

- [x] 迁移前风险可见。

## 证据

- `worklog/v2.2.3/sprint-31a-202605/assets/historical-catalog-migration-dry-run.md`
