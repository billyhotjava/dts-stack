# F1：通用交互契约

**优先级**：P0
**状态**：DONE

## 目标

把 Card 专用下钻配置扩展为数据源无关的字段映射契约，同时保持历史 ScreenConfig 可读、可运行。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| [T01](T01-通用DrillLevel与映射契约.md) | 通用 DrillLevel 与映射契约 | P0 | DONE | 无 |
| [T02](T02-ScreenConfig校验与旧配置归一化.md) | ScreenConfig 校验与旧配置归一化 | P0 | DONE | T01 |

## 完成标准

- [x] 新配置只依赖 DataSourceConfig 和 ComponentInteractionMapping。
- [x] 旧 `cardId + paramName` 配置无需批量迁移即可运行。
- [x] 合法和非法配置均有自动化契约测试。
- [x] 类型和校验中没有业务领域专属字段。
