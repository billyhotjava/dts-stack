# SD-007

## 标题

扩充 `screens` 模块前端测试，降低当前“82 个文件仅 3 个测试”的风险。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/`
- 重点是 `TemplateGallery`、`ScreenMarketplacePage`、`ScreenHeader`

## 目标

- 让高风险交互模块具备基础单测/组件测试
- 优先覆盖真实状态切换和 helper 逻辑，而不是做大面积 snapshot

## 交付

- 新增 screens 相关测试文件
- 必要时提取纯函数帮助测试

## 验收

- `pnpm -C source/dts-analytics-webapp/modern test` 能覆盖新增测试
- 不再只有 3 个与 screens 相关的测试文件

## 当前进度

- 状态：TODO
- 备注：优先补高价值路径，不追求一次性把所有页面都测满

## 风险

- 若过多依赖 DOM snapshot，会造成后续维护成本失控
