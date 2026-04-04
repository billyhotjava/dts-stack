# F3: PersonProfile 迁入 Keycloak attributes (Phase 3)

**优先级**: P1
**状态**: READY

## 目标
将 PersonProfile 表中的人员主数据迁入 Keycloak user attributes，PersonProfile 表废弃。

## 数据迁移映射

| PersonProfile 字段 | Keycloak attribute | 说明 |
|-------------------|-------------------|------|
| fullName | fullName | 已有 |
| email | email (内置字段) | 已有 |
| phone | phone | 已有 |
| personCode | person_code | 新增 |
| nationalId | national_id | 新增（敏感数据，需评估） |
| deptCode | dept_code | 已有 |
| deptName | dept_name | 新增 |
| title | title | 新增 |
| grade | grade | 新增 |
| person_security_level | person_security_level | 已有 |

不迁移的字段（保留在导入记录中）:
- lifecycleStatus, activeFrom/To → 业务状态，改为 Keycloak enabled + attributes
- lastSourceType, lastBatchId, lastReference → person_import_record 表

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Keycloak User Profile 配置新 attributes | P1 | READY | - |
| T02 | 数据迁移脚本（PersonProfile → Keycloak） | P1 | READY | T01 |
| T03 | 删除 PersonProfile 相关代码 | P1 | READY | T02, F3 |

## 完成标准
- [ ] PersonProfile 所有有效数据迁入 Keycloak attributes
- [ ] Keycloak User Profile 配置包含所有新 attributes
- [ ] PersonProfile 表标记为废弃（下个版本删除）
- [ ] 依赖 PersonProfile 的查询全部改为走缓存层
