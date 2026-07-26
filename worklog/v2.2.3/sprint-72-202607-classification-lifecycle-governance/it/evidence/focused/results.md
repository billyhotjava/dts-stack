# Focused test 结果

验证日期：2026-07-26

| 模块 | 结果 |
|------|------|
| dts-common | `SecurityLevelCatalogMonotonicTest` 3/3 通过，install 通过 |
| dts-platform | Sprint-72 组合套件 159/159 通过；其中修正集合断言后 `IngestionTaskProxyResourceTest` 9/9 定向复跑通过 |
| dts-platform Testcontainers | JDBC 元数据接入、存量字段继承、临时销毁/恢复及双人永久销毁 4 项隔离集成场景通过 |
| dts-ingestion | seal guard、task 执行/全量刷新、task service、Airflow DAG 46/46 通过 |
| dts-metrics | `MetricLifecycleSecurityParityTest` 6/6 通过，production package 通过 |
| dts-analytics | `ScreenPermissionServiceTest` 32/32、消费密级 5/5、旧公开链接 4/4，共 41/41 通过 |
| dts-platform-webapp | Sprint-72 与资产详情 source-contract 12/12 通过，`tsc --noEmit` 与 Chrome 95 production build 通过 |

构建日志只有仓库既有的 Maven 依赖收敛、deprecated API、Browserslist 数据过期和大分块提示，
没有本 Sprint 引入的编译或测试错误。

本轮没有重建或发布容器；production package 与前端 build 为编码完成后的统一验证证据，不代表
现网容器已包含本轮最终源码。
