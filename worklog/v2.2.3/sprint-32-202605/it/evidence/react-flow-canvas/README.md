# React Flow 画布交互证据

**状态**: READY

## 验证目标

验证指标与语义中心不再是占位页或静态列表，而是可编辑、可保存、可验证的 React Flow 工作台。

## 待执行检查

- [ ] `/metrics/semantic/metrics` 加载 React Flow 画布。
- [ ] 从 platform 资产节点池拖入 source asset。
- [ ] 创建 business object、Join、dimension、metric、model、publish 节点。
- [ ] 保存 graph draft 后刷新页面可恢复。
- [ ] graph preflight 错误能定位到节点和边。
- [ ] platform/dbt validation 报告能定位到模型节点或字段节点。

## 证据命令

```bash
cd source/dts-metrics-webapp
pnpm run typecheck
pnpm run build
```

后续实现完成后补 Playwright 截图、trace 和失败诊断样例。
