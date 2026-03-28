# T01: Liquibase 建表与数据源扩展

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

在 platform 数据库中创建资产权限相关表，扩展数据源表。

## 技术设计

### 新增表

1. **asset_ownership** — 资产归属
   - `(asset_type, asset_id)` UNIQUE
   - `owner_dept_id` 使用部门 code（与 `X-DTS-Dept-Code` 头一致）
   - `source_id` 外键关联 `infra_data_source`

2. **asset_grant** — 资产授权
   - `grantee_type`: USER / ROLE / DEPT
   - `permission`: READ / EDIT / MANAGE
   - `valid_from` / `valid_to` 支持时效性
   - `modified_date` 字段用于追踪更新

3. **asset_permission_audit** — 审计日志
   - `action`: GRANT / REVOKE / CHANGE_OWNERSHIP
   - `oa_reference` 记录 OA 审批单号

### 扩展列

4. `infra_data_source` 新增 `owner_dept_id VARCHAR(64)`

### 部门标识符约定

使用 `dept_code`（字符串，如 "HR"、"FIN"），与 Keycloak `X-DTS-Dept-Code` 头保持一致。`asset_ownership.owner_dept_id` 和 `asset_grant.grantee_id`（当 grantee_type=DEPT 时）均使用此格式。

## 影响范围

- `source/dts-platform/src/main/resources/config/liquibase/changelog/` — 新增 changelog XML
- `source/dts-platform/src/main/resources/config/liquibase/master.xml` — 引入新 changelog

## 验证

- [ ] Liquibase migration 成功执行
- [ ] 表结构符合设计（字段类型、约束、索引）
- [ ] infra_data_source 新列可 NULL（兼容存量数据）

## 完成标准

- [ ] 四个 DDL 变更全部通过 Liquibase 执行
- [ ] 索引覆盖高频查询路径
