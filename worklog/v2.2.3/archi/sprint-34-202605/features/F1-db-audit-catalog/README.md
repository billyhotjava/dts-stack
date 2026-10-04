# F1: DB 审计目录模型与迁移

**优先级**: P0
**状态**: DONE

## 目标

建立 dts-admin 运行时审计目录数据库模型，使模块、动作、路由映射和未知分类记录不再依赖中心 JSON。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 目录表结构与 Liquibase 迁移 | P0 | DONE | - |
| T02 | Catalog domain/repository/service | P0 | DONE | T01 |
| T03 | 最小动作 seed 与兼容导入 | P0 | DONE | T01, T02 |
| T04 | common catalog 缺失动作启动导入 | P0 | DONE | T01, T02 |

## 完成标准

- [x] 新增 catalog 表可表达 sourceSystem、module、action、operationKind、resourceType、状态和版本。
- [x] 未分类事件有独立 miss 表，不污染正常审计目录。
- [x] 最小 seed 覆盖 platform 主题域、报表、语义主题域。
- [x] 已有 common audit catalog 可作为启动 seed 补齐缺失 platform 动作，但不覆盖 DB 已治理配置。
