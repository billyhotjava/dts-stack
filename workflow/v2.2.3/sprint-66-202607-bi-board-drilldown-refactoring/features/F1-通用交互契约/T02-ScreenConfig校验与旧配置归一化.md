# T02：ScreenConfig 校验与旧配置归一化

**优先级**：P0
**状态**：DONE
**依赖**：T01

## 目标

让 ScreenConfig 同时接受通用层级和旧 Card 层级，并在运行前归一化为单一内部结构。

## 技术设计

- `screenSpec.ts` 校验每层 label、dataSource、mappings 和 inheritContext。
- 新配置缺少 dataSource 或有效映射时返回明确错误。
- 旧配置仅在 `cardId > 0` 且 `paramName` 非空时通过。
- 在新增纯函数模块中将旧 Card 层级转换为内部 DataSourceConfig 和单参数映射。
- 不回写和批量迁移历史 JSON。

## 影响范围

- `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.ts`
- `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.ts`（新增）
- `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.drillDown.test.ts`（新增）
- `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts`（新增）

## 验证

- [x] 通用层级合法配置通过。
- [x] 空目标、空映射、非法转换类型被拒绝。
- [x] 旧 `cardId + paramName` 配置通过并归一化。
- [x] 校验错误包含准确配置路径。

## 完成标准

- [x] 新旧配置共享同一运行时内部形态。
- [x] 不改变现有 ScreenConfig 存储接口。
