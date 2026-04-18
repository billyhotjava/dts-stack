# F2: 主数据管理 MDM (dts-mdm)

**优先级**: P0
**状态**: READY
**设计阶段**: 骨架
**依赖**: F0（Kafka/信封/starter）+ F1-lite（审批契约/Stub/ApprovalPort SDK）

## 目标

建立独立的主数据服务 `dts-mdm`，作为所有业务主数据的**唯一权威源**，对外提供 REST API 供 platform / 填报 / 财务 / PLM / ERP / dbt 等下游系统对接。主数据的**业务变更走 F1-lite 审批 Stub**。涉密数据的密级贯穿所有实体。

## 设计红线（brainstorm 已对齐）

- **不进 admin**：admin 只管人（账号/三员/部门归属），MDM 是业务主数据（参考 `feedback_mdm_is_independent_service.md`）
- **代码稳定**：主键使用 ASCII 稳定代理码（`PRJ-xxx` / `DEPT-xxx`），中文仅作 label；别名走 `source_code → standard_code` 映射（参考 `dim-code-design` 技能）
- **权威外部化**：其他应用系统只从 MDM 读主数据，**不允许**在本地再维护一份
- **版本与历史**：主数据生效有时间段（`valid_from` / `valid_to`），允许做时点查询；指标口径可回溯
- **密级是必填属性**：每个实体表必须带 `classification` 列，默认 `INTERNAL`；引用 `dts-common.SecurityLevelCatalog.DataSecurityLevel`
- **Person 不维护数据**：dts-mdm **不存** person 数据；但作为"对外统一查询入口"，提供 `/mdm/v1/persons/*` 代理接口（内部走 admin / Keycloak）
- **Excel 一次性导入**：首次上线从历史 Excel 反向清洗灌入基线（不含 person）

## 实体清单

| 实体 | MDM 是否存数据 | 对外查询 API | classification 列 | 审批 |
|---|---|---|---|---|
| project | ✓ 存 | ✓ | ✓ | ✓ |
| dept | ✓ 存 | ✓ | ✓ | ✓ |
| subsystem | ✓ 存 | ✓ | ✓ | ✓ |
| supplier | ✓ 存 | ✓ | ✓ | ✓ |
| pbs | ✓ 存 | ✓ | ✓ | ✓ |
| **person** | **✗ 不存**（代理到 admin/Keycloak） | ✓（统一入口） | 从 IAM 拿 `personnel_security_level` | 审批归 admin 三员 |

**对外查询统一入口**：所有应用系统（platform / PLM / ERP / 财务）都从 `dts-mdm` API 查，包括 person；MDM 内部再决定是"代理"还是"实存"。业务系统**不直接访问 admin/Keycloak 查 person**。

## 字典（特殊处理：密级只读）

- **业务字典**（完成情况 / 节点类型 / 风险等级 等约 12 类）：MDM 维护，支持别名归一
- **密级字典**（数据密级 4 项 + 人员密级 3 项）：MDM 表结构存在但**只读**，Bootstrap 时从 `dts-common.SecurityLevelCatalog` 导入（单一真源在 shared library，MDM 只作"对外查询视图"）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | [服务初始化与认证对接](T01-服务初始化与认证对接.md) | P0 | READY | F0, F1-lite |
| T02 | [主数据实体模型（含 classification）](T02-主数据实体模型.md) | P0 | READY | T01 |
| T03 | [业务字典 + 密级字典只读视图](T03-业务字典管理.md) | P0 | READY | T01 |
| T04 | [主数据 CRUD 与 ApprovalPort 集成](T04-主数据CRUD与审批集成.md) | P0 | READY | T02,T03,F1-lite |
| T05 | [MDM 管理前端](T05-MDM管理前端.md) | P0 | READY | T04 |
| T06 | [对外开放 API（含 person 代理）](T06-对外开放API.md) | P0 | READY | T04 |
| T07 | [历史基线数据导入（不含 person）](T07-历史基线数据导入.md) | P1 | READY | T04 |

## 完成标准

- [ ] 5 类实存主数据 + 约 12 类业务字典上线，支持密级字段
- [ ] 密级字典只读视图从 `SecurityLevelCatalog` 导入，前端/API 统一查 MDM 不再硬编码
- [ ] 任一主数据变更都经 F1-lite ApprovalPort → Stub → 回调落库
- [ ] 对外 REST API 稳定版发布，含 person 代理端点，openapi 文档齐全
- [ ] 至少 1 个下游系统（预计 dts-platform 或 intake）完成接入验证
- [ ] 历史 Excel 基线清洗导入完成（至少 project / dept / subsystem）
