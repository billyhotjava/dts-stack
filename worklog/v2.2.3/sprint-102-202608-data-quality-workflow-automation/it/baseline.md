# 交付基线探针结果（Gate G0）

**探针日期**：2026-08-22  
**环境**：本机 Docker 开发运行栈  
**结论**：PASS

| # | 探针 | 结果 | 证据 | 阻断 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | `v223-dts-platform-1` healthy；容器内 `/management/health` 返回 `UP` | - |
| P2 | 登录 | PASS | `xiezm` 真实登录 HTTP 200，`authenticated=true`，含 `ROLE_INST_DATA_OWNER` | - |
| P3 | Schema | PASS | `gov_quality_task`、`gov_quality_run`、`gov_issue_ticket` 可查询 | - |
| P4 | 真实数据 | PASS | 1 策略、244 运行、240 未关闭质量问题 | - |
| P5 | API harness | PASS | 真实登录 API 可调用；最终 API 验收延后 F6 | - |
| P6 | UI harness | PASS | 本机 Google Chrome 150 可用；按约定不在 Feature 开发前跑完整 E2E | - |
| P7 | 测试/构建 | PASS_WITH_NOTE | 采用 dts-platform 聚焦 Maven 测试、webapp Node 测试/构建 | 目标 Chrome 95 最终集中验证 |
| P8 | 外部依赖 | PASS | PG、Keycloak、平台容器健康；核心切片不依赖新增外部系统 | - |

## 验收路径

- 后端：迁移契约测试 → Service/Resource 聚焦测试 → dts-platform 模块构建。
- 前端：类型/语义测试 → webapp 构建。
- E2E：全部 Feature 完成后，使用 `xiezm` 从真实菜单执行策略、模型和入湖链路一次性验收。
