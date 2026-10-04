# F5：集中验证与交付证据

**优先级**：P0  
**状态**：IN_PROGRESS（T01 DONE；T02 等待授权 E2E 输入）

| Task | 状态 |
|---|---|
| T01 聚焦测试、模块构建与独立 Review | DONE |
| T02 Sprint-83/84 联合 E2E 与证据收口 | E2E_INPUT_PENDING |

F1～F4 与 Sprint-83 F1～F5 已进入代码收口；只有最终 Review、构建和部署目标镜像后才进入 T02，不以 mock、组件测试或构建结果替代真实 E2E。

当前原型重构后的前端 bundle 已部署；不得用镜像健康状态替代真实登录与用户操作验收。

## T01 当前证据（2026-08-03）

- 最终集中前端回归 Vitest `13 files / 73 tests`、Node 契约 `8/8`；Sprint-83 后端聚焦测试已通过 253 项。
- 安全 Review 的服务端错误正文透传和高级 dbt 权限短路问题已修复并增加回归测试。
- TypeScript、Chrome 95 target 生产构建和镜像构建通过；独立总代码 Review 为 APPROVED，上轮全部 CRITICAL/HIGH/MEDIUM 问题已关闭。
- GitNexus 对整个脏工作区给出 MEDIUM，唯一映射执行流为本轮未修改的 AccessPlan；当前建模改动未出现 HIGH/CRITICAL 执行流风险。
- `dts-platform-webapp:1.0.0` 已部署为 `sha256:2bf603f5ce642319182ae4a89cc26e32063bc0ce910d9a7e6783f5b9f7bdc767`，容器内首页及外部 `/data-modeling` HTTP 200。
