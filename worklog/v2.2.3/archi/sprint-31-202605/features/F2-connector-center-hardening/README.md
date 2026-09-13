# F2: Connector Center 企业级补齐

**优先级**: P0
**状态**: DONE
**目标**: 把数据接入中心从“JDBC 主链路可用 + 文件/API 分散能力”收敛为边界清晰、可预检、可追踪、可治理的 Connector Center。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 消除 `listDataSources` silent empty fallback | DONE: 后端不再吞异常返回空列表，前端显示错误和重试 |
| T02 | 数据源变更影响治理 | DONE: 连接严重变更生成 H 风险 `NEEDS_REVIEW` 影响单，ingestion 审批流支持提交 |
| T03 | ODS 生成 per-table 契约 | DONE: Schema 探测结果保留表级主键、增量列和字段映射覆盖 |
| T04 | 大表预检保护 | DONE: 源表精确 `count(*)` 默认关闭，通过 `props.precheckExactRowCount=true` 手动启用 |
| T05 | 文件/API 接入能力矩阵和最小契约 | DONE: 页面显示 JDBC/API/文件链路能力边界，文档固化能力矩阵 |

## 代码关注点

- `InfraDataSourceResource`
- `InfraManagementService`
- `OdsGenerationService`
- `OdsPrecheckProbeService`
- `IngestionTaskResource`

## 交付记录

- `InfraManagementService.listDataSources` 不再把查询异常伪装成空数据源列表。
- `DataSourcesPage` 在列表加载失败时显示错误态；同时展示 JDBC / API / 文件的黄金链路能力差异。
- 数据源连接参数变更生成 `NEEDS_REVIEW` 影响单，前端变更中心可以筛选和提交审批。
- `OdsPrecheckProbeService` 默认跳过源表精确行数扫描，避免大表预检阻塞；行数基线校验需要显式打开精确计数。
- 交付契约见 `../../assets/connector-center-hardening-contract.md`。
