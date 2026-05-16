# F5: platform-webapp 入口、版本开关与兼容代理

**优先级**: P0
**状态**: READY
**目标**: 让 `platform-webapp` 保持统一入口，但根据 edition/capability 决定是否显示指标语义功能。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | capability registry 消费 | 前端读取 platform capabilities，控制菜单、路由和提示 |
| T02 | metrics API 客户端 | 指标页面调用 `/api/metrics/**` 或 platform 代理，不再直接依赖 platform 内部语义路径 |
| T03 | 未启用友好态 | 基础版访问指标入口显示版本提示和启用说明，不跳 403/404 |
| T04 | `/api/semantic/**` 兼容代理 | 兼容旧入口一个 Sprint，转发到 metrics 或返回明确禁用状态 |
| T05 | 导航和权限一致性 | 菜单可见性、页面访问、按钮权限和 asset_grant 结果一致 |

## 完成标准

- [ ] foundation 版本无指标菜单，直接访问有友好提示。
- [ ] professional 版本指标页面走 `dts-metrics` 服务。
- [ ] 兼容代理有弃用说明和日志。
