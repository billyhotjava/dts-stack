# F2: 主数据管理 MDM (dts-mdm)

**优先级**: P0
**状态**: READY
**依赖**: F1

## 目标

建立独立的主数据服务 `dts-mdm`，作为所有业务主数据（project / dept / person / subsystem / supplier / pbs）和业务字典的**唯一权威源**，对外提供 REST API 供 platform / 填报 / 财务 / PLM / ERP / dbt 等所有下游系统对接。主数据的**业务变更走 F1 审批引擎**。

## 设计红线（brainstorm 已对齐）

- **不进 admin**：admin 只管人（账号/三员/部门归属），MDM 是业务主数据，**必须独立**（参考 `feedback_mdm_is_independent_service.md`）
- **代码稳定**：主键使用 ASCII 稳定代理码（`PRJ-xxx` / `DEPT-xxx`），中文仅作 label；别名走 `source_code → standard_code` 映射（参考 `dim-code-design` 技能）
- **权威外部化**：其他应用系统只从 MDM 读主数据，**不允许**在本地再维护一份
- **版本与历史**：主数据生效有时间段（`valid_from` / `valid_to`），允许做时点查询；指标口径可回溯
- **Excel 一次性导入**：首次上线需从历史 Excel 反向清洗灌入一份合并后的主数据基线

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | [服务初始化与认证对接](T01-服务初始化与认证对接.md) | P0 | READY | F1 |
| T02 | [主数据实体模型](T02-主数据实体模型.md) | P0 | READY | T01 |
| T03 | [业务字典管理](T03-业务字典管理.md) | P0 | READY | T01 |
| T04 | [主数据 CRUD 与审批集成](T04-主数据CRUD与审批集成.md) | P0 | READY | T02,T03,F1 |
| T05 | [MDM 管理前端](T05-MDM管理前端.md) | P0 | READY | T04 |
| T06 | [对外开放 API](T06-对外开放API.md) | P0 | READY | T04 |
| T07 | [历史基线数据导入](T07-历史基线数据导入.md) | P1 | READY | T04 |

## 完成标准

- [ ] 6 类主数据实体和约 12 类业务字典上线并可维护
- [ ] 任一主数据变更都经过 F1 审批引擎，审批历史可溯
- [ ] 对外 REST API 稳定版发布并有 openapi 文档
- [ ] 至少 1 个下游系统（预计 dts-platform 或 intake）完成接入
- [ ] 历史 Excel 主数据清洗导入完成（至少覆盖 project / dept / subsystem 三类）
