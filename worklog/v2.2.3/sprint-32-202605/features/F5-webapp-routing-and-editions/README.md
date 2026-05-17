# F5: platform-webapp 入口、能力发现与兼容代理

**优先级**: P0
**状态**: DONE
**目标**: 让 `platform-webapp` 保持统一入口，根据 capability 和 platform 权限决定是否显示指标语义功能；license 接入前不做版本禁用。

**Sprint-31A 依赖**: 前端入口、菜单、服务异常态和兼容代理都必须基于 platform capability、asset contract 和 permission check 判断，不在前端重复权限事实源。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | capability registry 消费 | 前端读取 platform capabilities，控制菜单、路由和提示 |
| T02 | metrics API 客户端 | 指标页面调用 `/api/metrics/**` 或 platform 代理，不再直接依赖 platform 内部语义路径 |
| T03 | 服务异常友好态 | metrics 服务不可用时显示明确服务异常，不跳模糊 403/404 |
| T04 | `/api/semantic/**` 兼容代理 | 兼容旧入口一个 Sprint，转发到 metrics 或返回明确禁用状态 |
| T05 | 导航和权限一致性 | 菜单可见性、页面访问、按钮权限和 asset_grant 结果一致 |

## 当前进展

- [x] `/metrics/**` 前端入口已改由 `dts-metrics` 服务承载。
- [x] `docker-compose-app.yml` 已把 `/metrics` 路由到 `dts-metrics`，并从 `dts-platform-webapp` 路由中排除。
- [x] `platform-webapp` 不再注册旧指标/语义中心页面路由，内部旧入口只做整页跳转。
- [x] `platform-webapp` 动态页面 glob 已排除 `pages/metrics/**` 和旧 `pages/modeling/Semantic*.tsx`，避免旧指标/语义页面继续进入 platform 产物。
- [x] portal 菜单已重组为 `商业智能应用 -> 指标与语义 / BI 分析`，指标菜单收敛为 4 个稳定入口。
- [x] 固定菜单种子已移除 `行业业务开发`、`项目看板` 和指标建模的步骤型子菜单；行业内容后续通过 app/app-pack 配置承载。
- [x] capability 菜单显隐以 platform 菜单权限为准；license 接入前不做版本禁用。
- [x] `/api/semantic/**` 当前保留旧 platform 接口作为一个 Sprint 的兼容窗口，后续切为明确代理或弃用提示。

## 完成标准

- [x] 指标菜单由 platform 菜单权限控制；capability 当前用于服务状态和友好提示。
- [x] 指标页面走 `dts-metrics` 服务。
- [x] 兼容路径已在证据中说明，旧入口整页跳转到 `bi-apps/metrics/**`。

## 证据

- `worklog/v2.2.3/sprint-32-202605/it/evidence/metrics-frontend/README.md`
- `worklog/v2.2.3/sprint-32-202605/it/evidence/menu-regroup/README.md`
