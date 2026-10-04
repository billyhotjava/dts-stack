# T01: 目录表结构与 Liquibase 迁移

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

新增 DB 审计目录表，支撑模块、动作、路由映射和分类 miss 的运行时治理。

## 技术设计

- 新增 `audit_module_catalog`：模块事实源。
- 新增 `audit_action_catalog`：动作事实源，唯一键为 `source_system + action_code`。
- 新增 `audit_classification_miss`：未知动作、未知路由和低置信度分类的治理队列。

## 影响范围

- `source/dts-admin/src/main/resources/config/liquibase/changelog/`
- `source/dts-admin/src/main/resources/config/liquibase/master.xml`

## 验证

- [x] Liquibase changelog XML 格式可解析。
- [x] 表名、唯一约束和索引符合重复部署要求。

## 完成标准

- [x] 新增表结构满足运行时 catalog 查询。
- [x] 不修改既有 `audit_entry` 历史数据。
