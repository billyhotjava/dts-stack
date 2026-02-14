# BI 重构任务卡（分阶段 + 序号）

## 状态总览
| Task | Status |
|---|---|
| `P0-01` | `done-first-pass` |
| `P0-02` | `done-first-pass` |
| `P0-03` | `done-first-pass` |
| `P0-04` | `done-first-pass` |
| `P0-05` | `done-first-pass` |
| `P0-06` | `done-first-pass` |
| `P1-01` | `todo` |
| `P1-02` | `todo` |
| `P1-03` | `todo` |
| `P1-04` | `done-first-pass` |
| `P1-05` | `doing` |
| `P2-01` | `todo` |
| `P2-02` | `todo` |
| `P2-03` | `todo` |
| `P2-04` | `todo` |

## P0（商用准入）
- `P0-01-release-versioning.md`：发布体系（草稿/发布/回滚）
- `P0-02-screen-acl.md`：权限体系（读/编/发/管）
- `P0-03-share-security.md`：安全分享（过期/密码/IP）
- `P0-04-audit-log.md`：操作审计（全链路可追溯）
- `P0-05-observability.md`：稳定性与可观测（500/502可定位）
- `P0-06-browser-compat.md`：浏览器兼容基线（Chrome95/109/最新）

## P1（成熟产品感）
- `P1-01-semantic-binding.md`：语义层接入（指标口径统一）
- `P1-02-global-interaction.md`：全局变量与跨组件联动
- `P1-03-asset-center.md`：模板/组件/主题资产中心
- `P1-04-datasource-unification.md`：API/DB/Card 数据源一致化
- `P1-05-performance.md`：缓存、预热与渲染性能优化

## P2（差异化壁垒）
- `P2-01-ai-screen-generation.md`：AI 生成大屏（NL2SQL -> NL2Viz）
- `P2-02-plugin-sdk.md`：插件化架构（组件 SDK / 数据源 SDK）
- `P2-03-industry-pack.md`：行业包与离线交付包
- `P2-04-enterprise-compliance.md`：企业级合规增强（xpack）

## 执行规则
- 每个任务卡至少包含：范围、依赖、交付物、验收标准、回滚点。
- 状态更新路径：先改任务卡，再同步 `worklog/v2.2.1/platform-analytics-v2.2.1-task-list.md`。
