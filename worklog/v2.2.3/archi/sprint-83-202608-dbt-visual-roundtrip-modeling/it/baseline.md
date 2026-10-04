# 交付基线（Gate G0）

**状态**：PASS_WITH_GAPS（83a 可编码；真实登录/上传/浏览器旅程留到最终 E2E）
**探针时间**：2026-08-02（Asia/Shanghai）
**约束**：按用户要求，全部 Feature/Task 编码完成前不执行 Sprint 用户旅程 E2E；本次只验证验收通道和工具可达性。

编码前一次性执行并记录：

| # | 探针 | 当前状态 | 通过条件 | 阻断 Task |
|---|---|---|---|---|
| P1 | dts-platform 可运行实例 | PASS | `v223-dts-platform-1` healthy；容器内 `/management/health` 返回 `UP` | - |
| P2 | 真实登录/受保护页面 | PASS_WITH_GAPS | `https://sso.yuzhicloud.com` 可达；S10 realm 存在 enabled 建模用户 `xiezm`；真实密码登录与受保护页面点击按用户约束归最终 E2E | F6/T04（最终 E2E） |
| P3 | schema/changelog | PASS_WITH_GAPS | `dts_platform` 已有 `modeling_model_spec_import_run/run_item/apply_attempt/apply_result` 与 materialization pin/dispatch 表；clean-db migration 在最终集成阶段复验 | F6/T03 |
| P4A | 当前切片工程 fixture | PASS | `assets/dbt-fixture-inventory.md`；FX-01/基础 FX-03/FX-05 离线、无客户数据、SHA-256 固定，3 个契约测试 GREEN | - |
| P4B | 后续兼容 fixture | GAP（非 83a 阻断） | S4 排期时再要求 FX-02～04 完整 | F0/T02、F3/T05～T06 |
| P5 | API harness | PASS_WITH_GAPS | parser/import focused Maven harness 可运行并 GREEN；真实 authenticated inspect→preview→apply 留到最终 E2E | F6/T02 |
| P6 | UI harness | PASS_WITH_GAPS | Google Chrome、Node 24.14.1、pnpm 10.33.0 可用；`https://bi.yuzhicloud.com` 返回 200；登录、上传与截图留到最终 E2E | F6/T04 |
| P7 | 构建/测试命令 | PASS_WITH_GAPS | focused Maven test 实际 `BUILD SUCCESS`；webapp 全量 build 在全部前端编码完成后统一执行一次 | F6/T01 |
| P8A | Airflow/API control-plane 可达性 | PASS | `dts-airflow-webserver` healthy；`/health` 与 `/api/v1/health` 均返回 200 | - |
| P8B | 真实 dbt/PostgreSQL materialization | PASS_EVIDENCE / DEPLOYMENT_E2E_PENDING | H83-01 原始证据经 F0/T05 登记为 CERTIFIED；将认证 derivative 固定到目标环境后验收 DbtExecutionGateway 代表链 | F6/T03～T04 |

P2/P5/P6/P7 的 GAP 是用户明确要求的“编码完成后集中 E2E/构建”调度约束，不代表已经完成真实验收，也不允许在最终 DoD 中降级或省略。它们不阻断 83a 编码，但最终整体验收必须补齐。P4B、P8B 等后续探针有具名 owner/status，不反向阻断 83a。
