# T03: 最小动作 seed 与兼容导入

**优先级**: P0
**状态**: DONE
**依赖**: T01, T02

## 目标

用 DB seed 覆盖本 Sprint 必须修复的现场问题动作，并保留 JSON 到 DB 的迁移兼容边界。

## 技术设计

- Liquibase seed 最小动作：审计查询/导出、主题域 CRUD、语义主题域 CRUD、报表查看/新增/修改/删除、platform generic/unclassified。
- JSON 不再作为运行时权威，但可作为后续一次性导入来源。

## 影响范围

- `source/dts-admin/src/main/resources/config/liquibase/changelog/`
- `source/dts-admin/src/main/resources/config/audit-button-registry.json`（只读兼容，不扩展运行时依赖）

## 验证

- [x] DB seed 中 actionCode 唯一。
- [x] 关键 platform actionCode 全部可查。

## 完成标准

- [x] 本 Sprint 关键动作不再依赖 JSON 分类。
