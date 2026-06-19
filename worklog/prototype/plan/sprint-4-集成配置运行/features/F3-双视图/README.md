# F3: 双视图

**优先级**: P1
**状态**: READY

## 目标

提供画布⇄列表视图切换：两视图共享**同一份转换作业数据**，列表视图收编现网 `TransformPage` / `OrchestrationPage` 表格。

## 背景

设计 §7：`画布 ⇄ 列表`切换，同一份转换作业数据，列表视图收编现网 Transform/Orchestration 表格。视图切换是同一数据的两种表现，不是两套数据。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 画布⇄列表视图切换 | P1 | READY | S3 |
| T02 | 列表视图（收编 Transform/Orchestration 表格） | P1 | READY | T01 |

## 完成标准

- [ ] 阶段② 顶部有 `画布 | 列表` 切换控件；切换不丢失/不复制数据，两视图读同一转换作业数据源。
- [ ] 列表视图以 `CompactTable`（10 条/页）展示转换作业，列收编现网 Transform/Orchestration。
- [ ] 列表行可跳回画布（定位/打开对应作业）。
- [ ] 视图状态可持久到 URL（如 `?view=canvas|list`），刷新保持。
- [ ] 切换控件与列表布局 Chrome 95 安全。
