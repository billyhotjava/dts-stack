# F3: 前端角色与筛选器

**优先级**: P0
**状态**: READY

## 目标

实现领导视角页面最关键的基础设施：

1. `useWorkbenchRole` hook：从 `userStore` 提取角色、部门、身份判定。
2. `WorkbenchFilterBar`：部门 / 业务域 / 时间三档下拉过滤器，含角色锁定与软依赖降级。

这两件事做好，F4（KPI）和 F5（报表/资产）就能基于同一 filter 状态联动。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | useWorkbenchRole hook | P0 | READY | - |
| T02 | 部门下拉（含所领导层级） | P0 | READY | T01 |
| T03 | 业务域下拉（软依赖降级） | P0 | READY | T01 |
| T04 | 时间预设下拉 | P1 | READY | - |
| T05 | WorkbenchFilterBar 装配 | P0 | READY | T01, T02, T03, T04 |

## 完成标准

- [ ] `useWorkbenchRole` 能正确识别 `EMP` / `DEPT_LEADER` / `INST_LEADER`；空 roles 降级为 `EMP`。
- [ ] 所领导身份下，部门下拉可切换；其他角色锁定本部门、下拉禁用。
- [ ] 业务域 API 失败时业务域下拉**消失**（不是禁用），整页仍可用。
- [ ] 时间下拉默认"本月"；切换不刷新静态 KPI（资产总数），但刷新随期指标。
- [ ] `WorkbenchFilterBar` 输出的 `WorkbenchFilterState` 类型贯穿 F4/F5 数据拉取。
