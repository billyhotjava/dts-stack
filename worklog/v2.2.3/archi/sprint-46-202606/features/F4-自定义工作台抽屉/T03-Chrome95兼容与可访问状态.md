# T03: Chrome95 兼容与可访问状态

**优先级**: P0  
**状态**: DONE  
**依赖**: T01/T02

## 目标

确保自定义抽屉在客户侧 Chrome 95 可用，并具备基本键盘和可访问状态。

## 技术设计

兼容要求：

- 不使用 `structuredClone`。
- 不使用 CSS container query。
- 不使用原生拖拽或依赖新浏览器 API 的拖拽库。
- 不使用 masonry/free grid。
- 数组重排使用普通不可变数组复制。

可访问要求：

- checkbox 有可读 label。
- 图标按钮提供 tooltip 或 `aria-label`。
- loading 时按钮禁用。
- 错误提示可被用户看到，不只写 console。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/**`
- Vite/TypeScript 目标配置
- Playwright smoke

## 验证

- [x] RED: lint/source-contract 捕获禁用 API 或缺失按钮状态，确认失败。
- [x] GREEN: 兼容实现后测试通过。
- [x] Chrome 95 目标构建无语法风险。

## 完成标准

- [x] Chrome 95 兼容检查通过。
- [x] 所有抽屉按钮有可识别状态。
