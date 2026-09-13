# 真实容器状态

2026-07-26 最终检查：

| 容器 | 状态 |
|------|------|
| `v223-dts-platform-1` | running / healthy |
| `v223-dts-ingestion-1` | running / healthy |
| `v223-dts-analytics-1` | running / healthy |
| `v223-dts-metrics-1` | running / healthy |
| `v223-dts-platform-webapp-1` | running |

Platform 最终启动日志为 `Started DtsPlatformApp`，检查窗口内未再出现 `Instant` JDBC 绑定错误、
`UnexpectedRollbackException` 或其他 ERROR/Exception。

真实 PostgreSQL API 与迁移 dry-run 已通过；dbt/OpenLineage/Airflow、Excel/CSV 和 JDBC 接入闭环仍待补证。
