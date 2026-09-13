# F1: 概念、关系与契约收敛

**优先级**: P0  
**状态**: READY

## 目标

冻结业务分类、数据集市、业务维度、维度表和资产台账的边界以及可执行契约，使后续实现不再重新讨论对象归属。

## 契约定义

以 Sprint README ADR 和 `assets/contract-design.md` 为唯一输入。禁止另建 DataMart 资产目录、Dimension 表台账或发布控制面。

## UI/UX 规格

- 复用 `/governance/subjects`、计划详情、维度目录、模型详情。
- 不新增一级菜单。
- 用户语言采用 `assets/dataworks-reference-review.md` 的术语表。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 冻结 DataMart 与维度关系契约 | P0 | READY | - |
| T02 | 固化用户语言与修复导航规范 | P0 | READY | T01 |

## Definition of Ready

- [x] API、DTO、表、错误码已钉死
- [x] 页面落点和 happy path 已命名
- [x] DataWorks 参考与 DTS 不照搬项已记录

## 完成标准

- [ ] 后续 Task 只引用本 Feature 契约，不另造术语或对象
- [ ] source-contract 能防止资产台账/数据集市再次混用
