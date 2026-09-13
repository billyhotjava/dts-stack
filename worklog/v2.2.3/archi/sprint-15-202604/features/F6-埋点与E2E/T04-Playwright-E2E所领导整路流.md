# T04: Playwright E2E · 所领导整路流

**优先级**: P0
**状态**: READY
**依赖**: F5/T05

## 目标

用 Playwright 写一条端到端脚本：模拟所领导登录 → 进入工作台 → 切业务域 → 切部门 → 打开 TOP 报表 → 回到工作台。全程无 JS 异常、无 404，关键接口响应正常。

## 技术设计

### 测试位置

项目既有 E2E 目录：查 `source/dts-platform-webapp/tests/e2e/`（或 `playwright/`）。若无则新建：

```
source/dts-platform-webapp/tests/e2e/workbench-leader-overview.spec.ts
playwright.config.ts（若无则新建）
```

### 前置

- 登录态：使用项目既有 `tests/e2e/fixtures/login.ts` 或 `storageState.json`。若无，用 API 走登录后把 `storageState` 写到 `.auth/leader.json`。
- 账号：一个已分配 `ROLE_INST_LEADER` 角色的测试账号（如 `lead_e2e@demo`）。账号口令放在 `.env.e2e`（`.gitignore`）里，不入库。

### 脚本骨架

```ts
import { test, expect } from "@playwright/test";

test.describe("workbench · leader overview · inst leader", () => {
  test.use({ storageState: ".auth/leader.json" });

  test("切域 / 切部门 / 打开 TOP 报表", async ({ page }) => {
    // 1. 进入工作台
    await page.goto("/workbench");
    await expect(page.getByText("所内报表")).toBeVisible({ timeout: 10_000 });

    // 2. KPI 卡片 4 张
    await expect(page.getByText("所内报表")).toBeVisible();
    await expect(page.getByText("数据资产")).toBeVisible();
    await expect(page.getByText("核心资产（S1）")).toBeVisible();

    // 3. 时间切换到本季
    await page.getByRole("combobox").filter({ hasText: "本月" }).click();
    await page.getByRole("option", { name: "本季" }).click();
    await expect(page.getByText("本季访问")).toBeVisible();

    // 4. 业务域色块矩阵出现（视业务域 API 是否可用）
    const matrixCell = page.locator('[role="button"][aria-pressed]').first();
    if (await matrixCell.count()) {
      await matrixCell.click();
      // 点击后 bizDomain 下拉应同步
      // （具体断言依 UI 文案；此处用 URL 或 Network 侧断言更稳）
      await page.waitForResponse((r) => r.url().includes("/api/workbench/leader-overview") && r.status() === 200);
    }

    // 5. 所领导切具体部门
    await page.getByText("全所（默认）").click();
    await page.getByRole("treeitem").first().click();
    await page.waitForResponse((r) => r.url().includes("/api/workbench/leader-overview"));

    // 6. 打开一条 TOP 报表
    const [newTab] = await Promise.all([
      page.context().waitForEvent("page"),
      page.getByText("月度财务月报").first().click(), // 文案与 seed 数据对齐
    ]);
    await newTab.waitForLoadState();
    expect(newTab.url()).toContain("/reports/");
    await newTab.close();

    // 7. 回到工作台，过滤状态仍保留
    await expect(page.getByText("本季访问")).toBeVisible();
  });

  test("CatalogDomain API 失败时业务域筛选器消失", async ({ page, context }) => {
    await context.route("**/api/catalog/domains", (route) => route.fulfill({ status: 500, body: "boom" }));
    await page.goto("/workbench");
    await expect(page.getByText("所内报表")).toBeVisible({ timeout: 10_000 });
    // 业务域下拉不渲染
    await expect(page.getByText("全部业务域")).not.toBeVisible();
    // 色块矩阵也不渲染
    await expect(page.locator('[role="button"][aria-pressed]')).toHaveCount(0);
  });

  test("scope 降级：dept-leader 无法看到 ALL", async ({ page, browser }) => {
    const ctx = await browser.newContext({ storageState: ".auth/dept-leader.json" });
    const p2 = await ctx.newPage();
    await p2.goto("/workbench");
    await expect(p2.getByText("本部门报表")).toBeVisible({ timeout: 10_000 });
    // 部门下拉应显示为锁定
    await expect(p2.getByText(/本部门：/)).toBeVisible();
    await ctx.close();
  });
});
```

### 产物落地

运行命令：

```bash
pnpm --filter dts-platform-webapp exec playwright test workbench-leader-overview --reporter=html,line
```

- 自动生成 trace + screenshot + video 放到 `source/dts-platform-webapp/test-results/`
- 把关键 artifact（trace.zip、一张 KPI + 色块的截图）拷到 `worklog/v2.2.3/sprint-15-202604/assets/e2e/`

### CI 集成

暂不强求 CI 运行（项目现有 CI 是否跑 E2E 取决于 pipeline 配置）。本 task 只要求本地能跑通 + artifacts 入库。

## 影响范围

- 新增：`source/dts-platform-webapp/tests/e2e/workbench-leader-overview.spec.ts`
- 可能新增：`playwright.config.ts`（若项目尚未配置）
- 新增 `.gitignore` 排除：`.auth/`、`test-results/`（若未排除）

## 验证

- [ ] `pnpm --filter dts-platform-webapp exec playwright test workbench-leader-overview` 本地通过。
- [ ] artifacts 拷到 `assets/e2e/`：至少一张 KPI 截图 + 一个 trace.zip。
- [ ] 手检 trace：无 404、无 JS 异常、所有 fetch 都是 200。

## 完成标准

- [ ] 3 条测试用例（所领导整路流 / 业务域 API 失败 / 部门领导锁定）全部通过。
- [ ] `assets/e2e/` 目录非空，`it/README.md` 引用截图。
- [ ] 测试不依赖硬编码时间戳（用 `expect(...).toBeVisible()` 而非 `waitForTimeout`）。
