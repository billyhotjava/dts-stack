# Sprint-78 交付基线（G0）

**日期**：2026-07-29
**结论**：命令行/脚本/构建类验收路径可用；浏览器登录基线未恢复（共享缺口，Sprint-77 F0 跟踪），本 Sprint 仅 F4/T03 的浏览器 smoke 段受其约束。

## 验收手段矩阵

| 验收类型 | 可用性 | 说明 |
|---|---|---|
| 后端单测（dts-admin `npm run backend:unit:test`） | 可用 | F1 契约与拒绝路径 |
| 前端 source-contract 测试 + `pnpm build` | 可用 | F1/T02、F4/T02 |
| shell 脚本执行/故障注入 | 可用 | F3 备份、保留清理、恢复演练 |
| `docker compose config` 静态校验 | 可用 | F4/T01 |
| 运行栈 curl 路径矩阵 | 可用（栈在运行时） | F4/T03；前置 `.skills/dts-devops-runbook/scripts/dts_healthcheck.sh` |
| 浏览器登录 / Playwright / Chrome95 | **不可用** | 默认 E2E 账号 401、共享会话占用（继承 Sprint-77 `it/baseline.md` 结论） |

## 基线缺口与处置

| 缺口 | 影响 | 处置 |
|---|---|---|
| 登录 401 / 浏览器验收链未恢复 | F4/T03 浏览器 smoke；F1/T02 的真实 UI 走查 | 源代码层（contract 测试 + build + curl 矩阵）先行；浏览器证据段标 GAP，待 Sprint-77 F0/T01 恢复后补齐，不阻塞其余验收 |

## 环境假设

- 验收环境即本仓库运行栈（`docker compose -f docker-compose.app.yml up -d` 或 dev 模式）；PG 容器名以 compose 为准。
- 所有验证离线可执行；不引入需要外网的工具。
- 备份/恢复演练产物目录 `backups/postgres/` 与 `logs/backup/` 已加入 `.gitignore`（F3/T01 确认项）。
