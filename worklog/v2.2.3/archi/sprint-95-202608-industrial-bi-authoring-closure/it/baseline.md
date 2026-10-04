# 交付基线探针结果（Gate G0）

**探针日期**：2026-08-20  
**环境**：本地 v2.2.3 Docker 运行栈，`https://bi.yuzhicloud.com`  
**结论**：PASS_WITH_GAPS——可运行、数据、API/前端测试 harness 可用；当前真实登录会话与 Chrome 95 缺失，只阻断最终兼容/角色验收，不阻断基于固定契约的实现。

| # | 探针 | 结果 | 实际证据 | 处置 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | dts-platform、dts-platform-webapp、dts-analytics running；Platform/Analytics healthy；UI `/`=200 | - |
| P2 | 登录/认证 | GAP | 未登录 `/api/management/health`、`/bi/api/management/health` 均 401；当前 shell 无 E2E_USERNAME/PASSWORD；Playwright MCP 停在登录页 | F3/T02 真实复验 |
| P3 | Schema | PASS | dts_platform 248 张业务表、dts_analytics 66 张；本 Sprint 复用现有表且无迁移 | - |
| P4 | 代表数据 | PASS_WITH_GAPS | QueryDataset=25、PUBLISHED version=25、analysis=2、dashboard=2；只代表本地验收量级 | F3/T02 不外推容量 |
| P5 | API harness | PASS | 现有 Analysis/Dashboard Mock API E2E；受保护入口 fail-closed | - |
| P6 | UI harness | PASS_WITH_GAPS | Playwright spec/config 与 Chrome 150 可用；browser-use CLI 不存在；Chrome 95 不存在 | F3/T02 |
| P7 | 构建/测试 | PASS | `node --test ...AnalysisEditor... ...DashboardPublication...` 9/9；`mvn -DskipITs -Dtest=AnalysisApplicationServiceTest,AnalysisQueryGatewayTest test` 5/5 | - |
| P8 | 外部依赖 | PASS | 本 Sprint 只依赖现有 Platform contract、PostgreSQL、查询网关和浏览器 | - |

## 验收路径

- 前端：Node source-contract/unit → `pnpm build` → Sprint-95 Playwright。
- 后端：Analytics 聚焦 JUnit → module test/package → 容器 API/下载。
- 页面：分析创建/编辑、看板编辑/预览；1366×768 与窄视口；console/network 分开记录。
- 证据：只写真实执行结果到 `it/README.md`；Chrome 150 不替代 Chrome 95。
