# T04: 真实运行、Chrome 95 与 Go/No-Go

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T03

## 目标

在真实认证、数据库、dbt/OpenLineage、接入和浏览器环境证明 Sprint 可交付。

## 技术设计

- 升级现有数据库并检查迁移/对账/回滚边界。
- 使用真实 JDBC、Excel/CSV、API、dbt target、OpenLineage/Airflow。
- Chrome 95 验收接入密级、资产解释、生命周期、大屏发布与访问。
- Go/No-Go 汇总实现、测试、构建、迁移、容器和浏览器六类证据。

## 影响范围

部署环境、运行容器、测试数据夹具、IT evidence。

## 验证

- [ ] `it/README.md` IT-10 全部证据。
- [ ] 升密后跨服务访问即时收敛。

## 完成标准

- [ ] 所有高风险适配器通过真实 smoke。
- [ ] 缺任一证据时 Sprint 保持 IN_PROGRESS，不得标记 DONE。

## 编码进展

已建立八类证据域和严格 NO-GO 默认清单。真实数据库、容器、dbt/OpenLineage、接入和 Chrome 95
验证现在才允许开始；证据未生成前本 Task 保持 `IN_PROGRESS`。
