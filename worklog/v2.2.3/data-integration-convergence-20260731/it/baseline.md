# 数据集成收敛交付基线（Gate G0）

**探针日期**：2026-07-31  
**环境**：本机 `v223` Docker 部署，正式入口 `https://bi.yuzhicloud.com`  
**结论**：BLOCKED（仅最终 UI E2E 被登录凭据阻断；实现可继续，Goal 不得关闭）

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `dts-platform`、`dts-ingestion`、PG healthy；`dts-admin` health 恢复正常；UI 返回 HTTP 200 | - |
| P2 | 登录路径 | FAIL | 仓库默认 `opadmin/opadmin123` 调用 `/api/keycloak/auth/login` 返回 HTTP 401；无已有 storageState | F0：提供有效 E2E 账号或恢复测试账号 |
| P3 | Schema 状态 | PASS | `dts_admin.databasechangelog=272`，`dts_platform.databasechangelog=423` | - |
| P4 | 代表性数据 | PASS | 18 个连接器、4 个数据源、23 个接入任务、3023 条执行记录 | - |
| P5 | API 验收通道 | PASS_WITH_GAPS | 已有 API/JDBC/file live 脚本；按本 Goal 约定暂不提前执行 | 最终阶段统一执行 |
| P6 | UI 验收通道 | BLOCKED | Playwright 与 auth setup 存在；登录凭据失效 | 同 P2 |
| P7 | 构建与测试命令 | PASS | Webapp `pnpm build`（含 legacy browser build）；platform `backend:unit:test` 已定义 | 编码后执行 |
| P8 | 外部依赖 | DEFERRED | 远程 JDBC/API/file 测试源留到最终一次 E2E | 最终阶段统一探测 |

## 本 Goal 验收路径

- UI：Chrome/Playwright 登录后验证接入概览、数据库、API、离线文件、默认策略和详情页。
- 后台：复用现有 platform/ingestion/quality seam，分别完成 JDBC、API、file 真实运行。
- 回归：确认存量任务、checkpoint、密级封条、质量失败样例和 Airflow DAG 不因 UI 收敛失效。
- E2E 纪律：UI 与后台全部合并、模块测试通过后只运行一次三路径 E2E。

## 删除边界

“删除旧模块”指删除已被统一工作台承接的旧页面、旧菜单和重复编排入口；不删除仍被正式流程引用的
`IngestionTaskAPI`、Addax、Airflow、API Runtime、质量规则执行链或存量任务数据。
