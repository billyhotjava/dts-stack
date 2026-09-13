# F1: Connector Registry 连接器目录

**优先级**: P0
**状态**: DONE

## 目标

建立 DTS 自己的连接器注册表，统一描述连接器类型、配置 schema、支持能力、执行引擎和部署兼容性，让后续 Addax、dlt、SeaTunnel、Airbyte 都只作为能力实现，而不是产品模型本身。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 连接器注册表数据模型与内置 seed | P0 | DONE | - |
| T02 | 连接器能力声明模型 | P0 | DONE | T01 |
| T03 | 配置 schema 与敏感字段声明 | P0 | DONE | T01 |
| T04 | 连接器目录 API 与前端列表 | P1 | DONE | T01-T03 |

## 完成标准

- [x] 内置 MySQL、PostgreSQL、Oracle、SQL Server、DM8、人大金仓、Hive/Inceptor、Excel、CSV、JSON、HTTP API、SFTP、MinIO 连接器。
- [x] 每个连接器声明支持的同步模式、schema discover、连接测试、增量、CDC、文件采样能力。
- [x] 每个连接器声明默认执行引擎：Addax/JDBC/File/API/Future。
- [x] API 能返回连接器列表、详情、配置 schema 和能力矩阵。
- [x] 前端连接器目录列表与能力矩阵展示。
