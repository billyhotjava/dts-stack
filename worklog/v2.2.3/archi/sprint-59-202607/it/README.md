# Sprint-59 IT Evidence

**状态**: DONE
**日期**: 2026-07-08

## 验证矩阵

| 验证项 | 命令 | 状态 | 证据 |
|--------|------|------|------|
| 前端菜单与路由契约 | `node --test src/pages/modeling/LowCodeDevelopmentPage.source-contract.test.ts src/pages/modeling/dataDevelopmentWorkbench.source-contract.test.ts src/pages/modeling/metricWorkbench.source-contract.test.ts src/routes/sections/dashboard/portalGoldenLineMenu.source-contract.test.ts src/routes/sections/dashboard/sprint45Navigation.source-contract.test.ts` | PASS | 26 tests, 26 pass |
| 低代码向导页面契约 | 同上 | PASS | 覆盖六步状态、低代码路由、工作台首张报表、指标、发布、运行入口 |
| 指标工作台上下文契约 | 同上 | PASS | 覆盖 `journey=low-code-development`、低代码上下文提示和返回动作 |
| 前端类型检查 | `pnpm exec tsc --noEmit` | PASS | exit 0 |
| 前端生产构建 | `pnpm build` | PASS | built in 2m 8s；仅 Browserslist 过期和 chunk size warning |
| 后端菜单 seed 契约 | `./mvnw -Dspotless.apply.skip=true -Dspotless.check.skip=true -Dspring-boot.build-info.skip=true -Dtest=PortalMenuSeedDefaultsContractTest#lowCodeDevelopmentSeedStaysInDataDevelopmentAndKeepsAdvancedEntrypoints test` | PASS | Tests run: 1, Failures: 0 |
| Diff 空白检查 | `git diff --check` | PASS | exit 0 |
| GitNexus 影响检查 | `mcp__gitnexus.impact` + `mcp__gitnexus.detect_changes(scope=unstaged)` | PASS | 关键符号预改影响均 LOW；detect_changes: changed_count=46, changed_files=22, affected_processes=[]，risk_level=low；检测范围包含本轮开始前已有 Sprint-58/资产/连接器脏改 |
| 浏览器 smoke | `pnpm preview --host 127.0.0.1 --port 4173` + Playwright | PASS | 桌面 1366: 低代码页面存在、6 步渲染、指标/发布/运行入口可见；移动 390: 6 步渲染、无横向溢出；指标工作台上下文提示可见；截图见 `it/evidence/sprint59-low-code-development-1366.png`、`it/evidence/sprint59-low-code-development-390.png` |

## 必测业务路径

1. 数据开发菜单进入低代码开发向导。
2. 向导显示数据准备、业务对象确认、指标设计、DWS/ADS 生成、发布审核、运行证据。
3. 无数据源时能发起接入或进入数据源页面。
4. 已有表时能进入字段确认或资产详情。
5. 指标设计节点能进入指标工作台并保留上下文。
6. DWD 节点只显示草案或工程审核，不允许普通用户直接发布。
7. DWS/ADS 节点能进入模型管理或发布审核。
8. 发布后能进入报表、大屏、API、数据产品或任务运维入口。

## 验收注意事项

- 普通用户主路径不应要求理解 SQL/dbt。
- 高级开发入口可以存在，但不能成为完成首条链路的必经步骤。
- 如果后端 API 缺失，页面必须展示“待接通/待审核/待工程处理”，不能伪造成已完成。
- Preview smoke 通过本地测试 session 绕过登录门禁；无后端 API 时浏览器控制台存在请求失败日志，不影响本次路由、页面和上下文验收。
