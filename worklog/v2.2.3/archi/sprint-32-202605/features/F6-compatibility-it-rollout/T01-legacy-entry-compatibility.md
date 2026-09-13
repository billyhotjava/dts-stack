# T01: 旧入口兼容与弃用提示

**优先级**: P0
**状态**: READY
**依赖**: F2

## 目标

确保旧 platform 语义页面/API 不再误导用户，同时保留必要兼容窗口。

## 技术设计

- platform-webapp 旧入口跳转到 `/metrics/**`。
- 旧 `/api/semantic/**` 明确代理、兼容或返回弃用提示。
- 文档列出旧路径到新路径映射。

## 影响范围

- `source/dts-platform-webapp`
- `source/dts-platform`
- `source/dts-metrics-webapp`

## 验证

- [ ] 旧菜单不会打开空白/占位页面。
- [ ] 旧 API 调用有明确响应。

## 完成标准

- [ ] 回滚时可以恢复到旧入口或明确禁用新入口。
