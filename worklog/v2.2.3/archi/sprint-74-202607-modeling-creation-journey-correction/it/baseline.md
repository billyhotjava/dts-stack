# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-26；2026-07-27 完成复测
**环境**：当前 v2.2.3 本机 compose 实例  
**结论**：PASS

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | 当前后端镜像健康；当前 webapp 镜像 `sha256:cb191641…f131d` 已部署 | - |
| P2 | 登录/auth 路径 | PASS | `bi.yuzhicloud.com` 经显式 resolver 访问；真实 `portal_session` API 200，凭据未落证据 | - |
| P3 | Schema 状态 | PASS | PostgreSQL `databasechangelog` 已登记 20260727-01～07；clean Testcontainers migration 通过 | - |
| P4 | 真实/代表数据 | PASS | 隔离验收计划包含四类模型、3 个普通实现、1 个 dbt 实现、1 个 COMPILE/PASSED lifecycle 和 1 条改型 command | - |
| P5 | API 验收 harness | PASS | 真实认证 API、P95、纠错、兼容 dry-run/rollback 均有证据 | - |
| P6 | UI 验收 harness | PASS | 精确 Chromium 95.0.4638.0，真实环境 3/3 通过 | - |
| P7 | 构建/测试命令 | PASS | 后端 202+18 tests、前端 143 tests、TypeScript、production build 全绿；范围外用户修改保持原样 | - |
| P8 | 外部依赖 | PASS | PostgreSQL 17、dbt 1.11.3/postgres adapter 1.10.0；真实 dbt compile 与 lifecycle artifact 通过 | - |

## 阻断项与处置

| 阻断 | 影响 | 处置 | Task |
|---|---|---|---|
| 自动化 DNS/login/API 不通 | 已关闭 | Playwright/curl 使用显式 resolver；本地用户态与真实 HttpOnly Cookie 分离 | F0/T01 |
| 当前镜像/数据库落后 | 已关闭 | 后端/webapp 重建部署，changeset 05/06/07 实库可查 | F0/T01 |
| GitNexus 落后 HEAD | 已关闭 | `s10-stack` 对齐 `c7e085de3`，所有 owning symbol 编码前完成 impact | F0/T01 |
| 缺代表数据 | 已关闭 | 隔离计划和四类 canonical 对象已建立并保留作回归 | F0/T02 |
| 范围外 dirty 文件 | 已控制 | 质量治理、AGENTS/CLAUDE 等用户修改未被回退；最终 detect_changes 单独说明影响范围 | F0/T01 |

## 本 Sprint 验收路径约定

- 后端：真实 Spring Security + PostgreSQL，通过 API 创建/更新/门禁/实现/纠错；
- UI：真实 Cookie 登录会话，Chrome95 完成 `it/README.md` 定义的旅程；
- dbt：至少一条 `DBT_MANAGED` 实现产生真实 artifact；
- 普通模式：至少一条 `DESIGNER_GENERATED` 实现；
- 证据全部落 `it/evidence/`，mock 未用于最终 Chrome95/API/PG/dbt 关闭证据。
