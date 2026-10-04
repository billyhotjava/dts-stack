# IT-07：合并质量门

**日期**：2026-07-31
**状态**：PASS（按 Sprint-78 变更范围）

## 质量门建议与执行对照

`.skills/dts-quality-gate/scripts/changed_module_checks.sh` 输出及执行：

| 建议检查 | 执行 | 结果 |
|---|---|---|
| `docker compose -f docker-compose-app.yml config` | 已执行（`-q`） | PASS |
| `docker compose -f docker-compose.dev.yml config` | 已执行（`-q`） | PASS |
| `docker compose -f docker-compose.legacy.yml config` | 已执行（`-q`） | PASS（`version` 字段废弃警告为既有，与本变更无关） |
| `cd source/dts-platform-webapp && pnpm build` | 已执行 | PASS（2m27s；chunk>1500kB 警告为既有） |
| `cd source/dts-admin-webapp && pnpm build` | **未执行：越界** | 工作区中 dts-admin-webapp 变更属 Sprint-76/77 在途工作，非本 Sprint 触碰 |
| `cd source/dts-admin && npm run backend:unit:test` | **未执行：越界** | 同上（dts-admin 后端变更非本 Sprint 触碰；F1 已 ABANDONED） |
| `cd source/dts-platform && npm run backend:unit:test` | **未执行：越界** | 同上（dts-platform 后端变更非本 Sprint 触碰） |

范围说明：质量门按"本 Sprint 实际触碰的模块"执行——`bin/dts-backup`（新）、`docker-compose-app.yml`、`docker-compose.dev.yml`、`init.sh`、`services/dts-proxy/dynamic/traefik-dynamic.yml`、`.gitignore`、`source/dts-platform-webapp`（BiLinksPage.tsx、biLinkUrl.test.ts）。工作区其余未提交变更（portal-menus、liquibase master.xml、sprint67 e2e 删除等）为 Sprint-76/77 在途工作，其验证归属对应 Sprint，本 Sprint 不代为签收。

## 本 Sprint 变更的验证汇总

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| 备份脚本语法 | `bash -n bin/dts-backup` | PASS |
| init.sh 语法 | `bash -n init.sh` | PASS |
| 备份全量/故障/保留 | IT-04 | PASS |
| 恢复演练 | IT-05 | PASS |
| compose 静态校验 | 三个 compose `config -q` | PASS |
| traefik 运行时 | file provider 热加载 + 受控重建后路由表 hetu=0、新错误=0、路径矩阵 | PASS（IT-06） |
| 前端契约测试 | `pnpm vitest run src/utils/biLinkUrl.test.ts`（7/7）、`TopReportsBlock.test.tsx`（8/8） | PASS |
| 前端构建 | `pnpm build`（platform-webapp） | PASS |
| 空白/冲突检查 | `git diff --check`（本 Sprint 全部触碰文件） | PASS |
| hetu 残留 | `grep -rni "hetu" src`：仅兼容层（biLinkUrl 重定向、menuTree 存量分类、BiLinksPage 存量映射、注释） | PASS（符合契约） |

## 浏览器证据段

GAP——共享登录基线未恢复（Sprint-77 F0/T01），本 Sprint 用户可见变更（BI 链接页引擎选项、旧路径 SPA 回落）以 curl 矩阵 + 契约测试为准，浏览器证据待基线恢复后补，不作为 DONE 的占位。
