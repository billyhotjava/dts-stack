# 交付基线探针结果（Gate G0）

**探针日期**：2026-08-21；**环境**：当前 `/opt/prod/s10/v2.2.3` 运行栈
**结论**：PASS_WITH_GAPS

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | platform/analytics/webapp/pg 容器运行；platform `/management/health`=UP | analytics health 受保护返回 401，容器 health=healthy |
| P2 | 登录路径 | GAP | 旧 storageState 被重定向到 `#/auth/login` | 缺当前真实 E2E 凭据；不阻塞代码/mock E2E |
| P3 | Schema | PASS | `databasechangelog` 73 条，最新为 `0052_analysis_publication.xml` | - |
| P4 | 真实数据 | PASS | 2 看板/2 组件/4 Card；1 published analysis + 1 invalid component | - |
| P5 | API harness | PASS | 运行日志中的 dashboard validate=200；服务聚焦测试可运行 | 真实写发布留到 F3 |
| P6 | UI harness | PASS_WITH_GAP | Playwright + Google Chrome 150 可用；Sprint-95 mock journey 可复用 | Chrome 95 executable 缺失，真实登录态过期 |
| P7 | 构建/测试 | PASS | Node source contract 7/7；`mvn -Dtest=DashboardPublicationServiceTest test` 通过 | 全构建留到编码完成后一次执行 |
| P8 | 外部依赖 | PASS | 组织/角色目录由当前 dts-platform/dts-admin 提供 | 无新增第三方依赖 |

## 本 Sprint 验收路径

- 后端：Dashboard publication/resource 聚焦测试。
- 前端：Node source/model tests → 单个 Sprint-98 mock Playwright journey。
- 集中交付：webapp build、analytics test/package、容器重建、健康/日志、页面截图。
- 真实登录仍不可用时，明确保留 G4 GAP，不用 mock 证据替代真实发布验收。
