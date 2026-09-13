# T02: analytics DB catalog seed 与未分类治理

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

为 dts-analytics 首批人工操作注册 DB 审计目录，并让未知 analytics actionCode 进入待治理队列。

## 技术设计

- 在 `20260523-01_audit_action_catalog.xml` 增加 analytics 模块和 action seed。
- 覆盖大屏 CRUD/发布/回滚/导出/公开链接/授权/密级/协作评论，语义层查询/VDS/契约发布，仪表板和关键 fallback 动作。
- `AuditV2Service` 将 analytics 与 platform 一样视为业务源；未知动作写 `audit_classification_miss`，模块落 `analytics.unclassified`。

## 影响范围

- `source/dts-admin/src/main/resources/config/liquibase/changelog/20260523-01_audit_action_catalog.xml`
- `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/audit/AuditV2Service.java`

## 验证

- [x] `AuditV2ServiceTest`
- [x] `xmllint --noout ...20260523-01_audit_action_catalog.xml ...master.xml`

## 完成标准

- [x] 已知 analytics actionCode 命中 DB catalog。
- [x] 未知 analytics actionCode 记录 miss，不信任模块 override。
