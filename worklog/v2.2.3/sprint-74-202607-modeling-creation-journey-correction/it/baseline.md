# 交付基线探针结果（Gate G0）

**探针日期**：2026-07-26  
**环境**：当前 v2.2.3 本机 compose 实例  
**结论**：BLOCKED（可进行文档与架构评审，不允许拉取运行时 Feature）

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `v223-dts-platform-1`、`v223-dts-pg-1` health 均为 healthy | - |
| P2 | 登录/auth 路径 | BLOCKED | 自动化环境 `curl https://dts.local/` 返回 `Could not resolve host`；用户截图证明人工页面可达，但不能替代自动化证据 | F0/T01 |
| P3 | Schema 状态 | PASS_WITH_GAPS | ModelSpec、revision、implementation、release 和 policy 表存在；Sprint-74 迁移尚未创建 | F5/T02 |
| P4 | 真实/代表数据 | GAP | 仅 3 个 v2 草稿、0 实现、0 lifecycle；缺四类+两种实现+发布结果样本 | F0/T02 |
| P5 | API 验收 harness | BLOCKED | 无可复用的真实认证请求证据 | F0/T01 |
| P6 | UI 验收 harness | BLOCKED | DNS/login 尚不能由 Playwright/Chrome95 自动完成 | F0/T01 |
| P7 | 构建/测试命令 | GAP | 当前工作树含 Sprint-73 并行未提交代码，本轮按用户要求不编译 | F5/T01 |
| P8 | 外部依赖 | PASS_WITH_GAPS | PostgreSQL/dbt 容器运行；真实 dbt 实现/发布联动未验证 | F0/T02、F5/T01 |

## 阻断项与处置

| 阻断 | 影响 | 处置 | Task |
|---|---|---|---|
| 自动化 DNS/login/API 不通 | 全部真实 UI/API 验收 | 固化 hosts/DNS、用户名密码登录和认证 storage state | F0/T01 |
| 缺代表数据 | 无法证明四类模型和普通/dbt/发布结果 | 建隔离验收计划与 fixture，不修改用户财务计划 | F0/T02 |
| 并行 dirty worktree | 无法形成可靠构建基线 | 架构批准后先记录 owning files/base commit，再拉取任务 | F0/T01 |

## 本 Sprint 验收路径约定

- 后端：真实 Spring Security + PostgreSQL，通过 API 创建/更新/门禁/实现/纠错；
- UI：用户名密码登录，Chrome95 完成 `it/README.md` 定义的旅程；
- dbt：至少一条 `DBT_MANAGED` 实现产生真实 artifact；
- 普通模式：至少一条 `DESIGNER_GENERATED` 实现；
- 证据全部落 `it/`，mock 不能关闭 Sprint。

