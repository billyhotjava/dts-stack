# T03：完成真实 dbt 与 Chrome 95 主旅程

**优先级**：P0
**状态**：READY
**依赖**：F5、T02

## 目标

用真实 PostgreSQL target、dbt 运行、认证会话和 Chrome 95 验证客户可操作的第六步主旅程。

## 技术设计

- 最小 dbt 项目包含成功与预期失败模型/测试，不借用预置 run result。
- Playwright 通过用户名密码认证，访问真实后端 API。
- 覆盖桌面、390px、键盘、刷新、后退、409、失败修复和返回链。
- FACT/SUMMARY/APPLICATION 先行；DIMENSION/SCD2 在 Sprint-67 接口稳定后纳入最终证据。

## 影响范围

- 新增 `e2e/sprint69-delivery-workbench.spec.ts`
- 新增 `playwright.sprint69.chrome95.config.ts`
- 新增最小 dbt fixture
- `it/evidence/dbt/` 与 `it/evidence/chrome95/`

## 实施步骤

1. 执行真实 dbt compile/build/test 并保存 invocation/manifest/run_results 摘要。
2. 运行 `pnpm exec playwright test -c playwright.sprint69.chrome95.config.ts`。
3. 对失败场景确认 Stage 6 不完成，修复重跑后才完成。

## 完成标准

- [ ] 真实浏览器可完成候选到发布及失败恢复，不依赖 route mock。
- [ ] Chrome 95 与 390px 无阻断性兼容、可访问性和布局问题。
- [ ] **UI 真实验收**：Playwright trace、关键节点截图和真实 runId 共同证明成功、dbt 失败、质量失败、409 恢复及精确重试旅程，截图不得来自 mock route。
