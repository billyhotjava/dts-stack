# Sprint-73 发布计划

## 变更顺序

1. 先部署 `dts-platform`，Liquibase 按 `20260727_01 → 02 → 03` expand-only 顺序执行。
2. 确认新增表、列、约束和索引均存在，再部署 `dts-platform-webapp`。
3. 旧 DOMAIN 维度和未绑定 DataMart 的 ModelSpec 保持可读；不回填猜测出的 DataMart。
4. 新建/更新链路通过后，再开放数据集市确认、计划纳入和维度表创建。

## 发布门槛

- focused contract、source-contract、后端单测和前端 build 通过；
- clean PostgreSQL Liquibase 和存量库 updateSQL/dry-run 通过；
- 认证 API 完成 DataMart CAS、维度 revision 和重复维度表 409 验证；
- Chrome 95 完成数据集市、计划范围、维度目录、模型详情四态验证；
- PUBLISHED 资产注册沿用 Sprint-69 链路，DRAFT 不得出现在资产台账。

## 回退与恢复

本 Sprint 数据变更是 forward-only。若应用回退：

- 保留新增表列，不执行 drop；
- 回退到兼容读取旧字段的应用版本；
- 用新的前向 changeset 修复约束或数据，不修改已执行 changeset；
- 重复发布冲突、资产注册 PARTIAL 均通过既有幂等修复入口恢复。
