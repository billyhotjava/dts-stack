# T01：通用 DrillLevel 与映射契约

**优先级**：P0
**状态**：DONE
**依赖**：无

## 目标

扩展现有 `DrillLevel`，使每一层可以引用任意现有数据源并使用统一字段映射，不建立第二套动作协议。

## 技术设计

- `DrillLevel` 增加可选 `dataSource`、`mappings`、`inheritContext`。
- `cardId`、`paramName` 改为可选历史兼容字段。
- 映射继续使用 `ComponentInteractionMapping`：`sourcePath` 是来源路径，`variableKey` 是目标参数。
- `ScreenActionType` 不新增领域或数据源专用动作。

## 影响范围

- `source/dts-platform-webapp/src/analytics/pages/screens/types.ts`
- `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.drillDown.test.ts`（新增）

## 验证

- [x] 先编写类型/Schema 契约测试并确认旧实现失败。
- [x] TypeScript 接受 SQL、API、Card、Dataset、Metric 作为下一层数据源。
- [x] `rg` 检查新增核心代码不存在项目、BOM、凭证、工单等领域字段。

## 完成标准

- [x] 通用契约编译通过。
- [x] 历史字段明确标记为兼容用途。
- [x] 没有新增数据库或后端 DTO。
