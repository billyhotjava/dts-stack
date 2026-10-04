# T04: common catalog 缺失动作启动导入

**优先级**: P0
**状态**: DONE
**依赖**: T01, T02

## 目标

避免升级后已有 platform `auditAction(...)` 因 DB catalog 只包含本次最小 seed 而大面积进入 `platform.unclassified`。

## 技术设计

- dts-admin 启动后读取 common audit catalog。
- 只为 DB 中不存在的 platform actionCode 建立模块和动作目录。
- DB 中已经存在的目录项不覆盖，确保现场治理配置始终优先。
- common catalog 只承担 seed/export 兼容角色，不作为 `AuditV2Service` 运行时分类权威。

## 验证

- [x] 缺失 actionCode 会被导入 DB。
- [x] 已存在 actionCode 不被覆盖。
- [x] admin 侧显式注册 common `AuditActionCatalog` Bean，避免默认扫描路径缺失。
