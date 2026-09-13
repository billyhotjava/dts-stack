# F8: 遗留 P0 修复

**优先级**: P0
**状态**: READY

## 目标
修复已知的 P0 级 bug，不依赖其他 Feature，可并行处理。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | sourceDataSourceId 非空校验 | P0 | READY | - |
| T02 | modelingPlan 同名校验 | P0 | READY | - |

## 完成标准
- [ ] sourceDataSourceId 创建时非空校验，返回明确错误信息
- [ ] modeling_plan 同名时返回冲突错误，不静默覆盖
