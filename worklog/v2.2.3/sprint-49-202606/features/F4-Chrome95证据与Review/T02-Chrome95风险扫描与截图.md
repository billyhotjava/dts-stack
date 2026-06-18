# T02: Chrome95 风险扫描与截图

**状态**: DONE
**优先级**: P0

## 验证内容

- 扫描生产相关改动，未发现 `:has()`、`dvh/svh/lvh`、container query、拖拽库、`structuredClone`、`ResizeObserver`、`bodyStyle`。
- 连接器目录 smoke：通过，操作列和能力标签单行稳定。
- 资产消费 smoke：通过，资产台账 7 个操作按钮单行稳定，数据产品进入工作台消费发布 section。
- 工作台入口 smoke：通过，默认本地偏好降级不请求 `/api/workbench/preferences`，自定义抽屉 11 个复选框可见。

## 截图证据

- `it/evidence/connector-registry-1366x768.png`
- `it/evidence/connector-registry-drawer-1366x768.png`
- `it/evidence/data-source-create-from-connector-1366x768.png`
- `it/evidence/asset-ledger-table-1366x768.png`
- `it/evidence/data-products-consumption-1366x768.png`
- `it/evidence/workbench-consumption-product-1366x768.png`
- `it/evidence/workbench-home-customize-1366x768.png`
- `it/evidence/workbench-data-management-entry-1366x768.png`
