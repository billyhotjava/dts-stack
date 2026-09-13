# T02: 治理 BI 服务运维入口组件

**优先级**: P0  
**状态**: DONE  
**依赖**: F3

## 目标

把治理阻断、BI 与大屏成果、数据 API 服务、运行健康迁移成工作台可选组件。

## 技术设计

组件：

- `governance-blockers`: 治理阻断摘要，按钮“查看治理问题”。
- `bi-delivery`: BI 与大屏成果入口，按钮“查看成果”。
- `api-services`: 数据 API 服务入口，按钮“查看 API 服务”。
- `ops-health`: 运行健康入口，按钮“查看运行健康”。

状态：

- 无权限: 后端过滤，前端通常不可见。
- 接口不可用: 组件内错误态和“重新加载”。
- 无数据: 展示健康或待配置空态。

## 影响范围

- `source/dts-platform-webapp/src/pages/workbench/**`
- `source/dts-platform-webapp/src/pages/services/**`
- `source/dts-platform-webapp/src/pages/ops/**`

## 验证

- [x] RED: 测试断言四个组件 key、标题、目标路由、空态，确认失败。
- [x] GREEN: 组件实现后测试通过。
- [x] 无数据不展示 demo 指标。

## 完成标准

- [x] 四个组件都能被个人配置选择。
- [x] 所有按钮有真实目标。
