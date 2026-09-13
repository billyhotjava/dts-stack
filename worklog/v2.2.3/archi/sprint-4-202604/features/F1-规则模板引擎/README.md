# F1: 规则模板引擎（后端）

**优先级**: P0
**状态**: READY

## 目标
建立模板驱动的质量规则创建机制，实施人员在线管理模板，客户选模板填参数即可创建规则。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | gov_quality_template 表 + Liquibase + 实体 | P0 | READY | - |
| T02 | 10 种内置模板数据初始化 | P0 | READY | T01 |
| T03 | SQL 模板渲染引擎 | P0 | READY | T01 |
| T04 | gov_rule 扩展字段 | P0 | READY | T01 |
| T05 | QualityRuleService 改造 | P0 | READY | T03, T04 |

## 完成标准
- [ ] gov_quality_template 表创建，10 种内置模板初始化
- [ ] SQL 模板渲染引擎支持参数校验、白名单、防注入
- [ ] 从模板创建规则时快照 SQL 到 rendered_sql
- [ ] 模板 CRUD API 可用
