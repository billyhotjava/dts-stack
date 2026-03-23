# F1: 第4屏指标全览

**优先级**: P0
**状态**: READY

## 目标
新增大屏第 4 屏，将 project3.xlsx 全部 34 个指标以 number-card 面板形式按 4 个维度分组展示，领导一屏看全貌

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 后端补充缺失指标 API | P0 | READY | - |
| T02 | 第4屏模板布局与组件配置 | P0 | READY | T01 |
| T03 | 指标数值验证 | P1 | READY | T02 |

## 完成标准
- [ ] `/screen/metrics-overview` API 返回 34 个指标
- [ ] 第 4 屏 number-card 按 4 个维度分组排列
- [ ] 所有指标计算逻辑与 project3.xlsx 一致
