# F2: Connector Center 企业级补齐

**优先级**: P0
**状态**: READY
**目标**: 把数据接入中心从“JDBC 主链路可用 + 文件/API 分散能力”收敛为边界清晰、可预检、可追踪、可治理的 Connector Center。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 消除 `listDataSources` silent empty fallback | 表缺失/查询失败返回可观测错误，不伪装为空列表 |
| T02 | 数据源变更影响治理 | URL/凭据/库名等严重变更触发任务 `NEEDS_REVIEW` 或暂停自动运行 |
| T03 | ODS 生成 per-table 契约 | 多表可以配置不同主键、增量列和字段映射 |
| T04 | 大表预检保护 | `count(*)` 改为采样/估算/超时保护，保留精确计数手动选项 |
| T05 | 文件/API 接入能力矩阵和最小契约 | 非 JDBC 路径不再假装等价，页面展示可用能力和下一步 |

## 代码关注点

- `InfraDataSourceResource`
- `InfraManagementService`
- `OdsGenerationService`
- `OdsPrecheckProbeService`
- `IngestionTaskResource`
