# T04: 空状态、阻断和 fallback 体验

**优先级**: P1
**状态**: DONE
**依赖**: F2-F4

## 目标

统一资产不可用、治理阻断、OpenMetadata fallback、权限拒绝和无数据状态的前端表达。

## 技术设计

- 为空、阻断、待治理、无权、服务不可用分别定义 UI 状态。
- 页面不再用泛化网络错误掩盖真实原因。
- 操作按钮根据状态禁用或引导。
- 空列表提示区分“未采集”和“无当前账号可见资产”；OpenMetadata-only 资产显示为主目录缓存。

## 影响范围

- platform-webapp asset portal
- release governance UI
- API error model

## 验证

- [x] 每种状态都有明确提示。
- [x] 不把系统错误误报为权限错误。

## 完成标准

- [x] 资产门户可用于现场交付解释。
