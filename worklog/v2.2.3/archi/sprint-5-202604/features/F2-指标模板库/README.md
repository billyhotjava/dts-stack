# F2: 指标模板库

**优先级**: P0
**状态**: READY

## 目标
建立指标模板库，实施人员管理模板（含蓝图、所需源字段、seed 对照表），客户从模板选取指标。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | gov_indicator_template 表 + 实体 | P0 | READY | F1/T01 |
| T02 | 内置模板初始化（财务+项目管理） | P0 | READY | T01 |
| T03 | 模板 CRUD API + Service | P0 | READY | T01 |
| T04 | 模板展开：蓝图→指标定义批量创建 | P0 | READY | T03, F1/T03 |

## 完成标准
- [ ] gov_indicator_template 表创建，内置模板初始化
- [ ] 模板 CRUD API 可用（内置不可删）
- [ ] 选模板 + 绑源表 → 批量创建 GovIndicatorDefinition 记录
