# T03: Chrome95 与页面 smoke 证据

**优先级**: P0  
**状态**: READY  
**依赖**: T02

## 目标

确保现有页面重构在客户 Chrome95 和常见桌面分辨率下可用。

## 技术设计

- 使用 `pnpm build` 做 legacy build。
- 对 P0 页面做 1366x768 smoke。
- 表格、抽屉、标签、按钮不重叠、不溢出。

## 影响范围

- `it/README.md`
- `it/screenshots/`

## 验证

- [ ] build 通过。
- [ ] P0 页面截图无明显布局错误。
- [ ] console error 无新增阻断项。

## 完成标准

- [ ] Sprint-51 实施结果可现场验收。
