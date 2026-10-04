# F2: 数据集市规划闭环

**优先级**: P0  
**状态**: DRAFT（等待 F0）

## 目标

用户可在既有业务分类页面管理数据集市，并在建设计划中确认本计划使用的数据集市。

## 契约定义

| 类型 | 契约 | 关键字段 |
|------|------|----------|
| REST | `/api/modeling/data-marts` | `code/name/purpose/ownerId/domainIds/status/revision/checksum` |
| REST | `/baseline/data-marts` | `version/etag/bindings[dataMartId,confirmationStatus]` |
| 数据 | `modeling_data_mart*` | tenant、revision、CAS、domain 多对多 |

## UI/UX 规格

- **入口**: `/governance/subjects?tab=data-marts`，现有页面新增工作区；无新菜单。
- **布局**: 左侧保留分类树；DataMart Tab 使用搜索/状态筛选/新建按钮和 10 条分页表。
- **四态**: 空态说明用途并提供“新建数据集市”；加载 skeleton；错误可重试；成功显示分类数、计划引用数、模型数。
- **计划交互**: 计划基线“业务范围”内先确认分类，再确认可用集市。
- **happy path**: 业务分类 → 数据集市 Tab → 新建 → 关联分类 → 确认 → 返回计划 → 纳入计划。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 实现 DataMart 持久化与 API | P0 | DRAFT | F0、F1/T01 |
| T02 | 接入业务分类页与计划业务范围 | P0 | DRAFT | T01、F1/T02 |
| T03 | 闭合权限审计并发与性能 | P0 | DRAFT | T01/T02 |

## Definition of Ready

- [x] 契约和 UI 落点已钉死
- [ ] F0 基线 PASS
- [ ] GitNexus 影响分析完成

## 完成标准

- [ ] DataMart CRUD/确认/退役真实可用
- [ ] 计划能保存 DataMart bindings
- [ ] 多租户、权限、审计、并发与分页证据齐全
