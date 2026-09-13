# T01：旧 Card 下钻与未配置大屏回归

**优先级**：P0
**状态**：DONE
**依赖**：F1-F3

## 目标

验证历史 Card 下钻配置无需迁移即可运行，且未配置交互的大屏行为不变。

## 技术设计

- 使用旧格式 fixture 验证读取、预览、下钻、上卷和再次保存。
- 使用无 actions/drillDown/interaction fixture 验证展示和点击行为。
- 对比改造前后规范化配置，允许新增默认值但禁止删除历史字段。
- 不修改线上 ScreenConfig 数据作为测试前置条件。

## 影响范围

- `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.drillDown.test.ts`
- `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts`
- `source/dts-platform-webapp/src/analytics/pages/screens/ScreenPreviewPage.hooks.test.ts`

## 验证

- [x] 旧 Card 两级下钻参数与原行为一致。
- [x] 保存后旧配置仍能再次加载。
- [x] 未配置交互时组件不新增点击样式和事件。

## 完成标准

- [x] 回归测试自动化并进入 IT 证据。
- [x] 不需要数据库迁移脚本。
