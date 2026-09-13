# F1: 数仓规划上下文与主题域入口

**优先级**: P0  
**状态**: DONE

## 目标

让主题域成为数仓规划的业务入口，创建可恢复的 DWD 维度建模规划上下文。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 规划上下文纯函数与 session 草稿 | P0 | DONE | - |
| T02 | 主题域页规划卡片与 DWD 维度入口 | P0 | DONE | T01 |
| T03 | 规划上下文恢复、失效与降级 | P0 | DONE | T01 |

## 完成标准

- [x] 规划上下文包含 `planningId/domainId/warehouseLayer/modelingMode`。
- [x] 主题域页能创建和恢复 DWD 维度规划。
- [x] session storage 异常、版本不兼容和空主题域均有 blocker。
