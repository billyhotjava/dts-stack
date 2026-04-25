/**
 * Sprint-15 F6/T04 — Workbench leader-overview E2E.
 *
 * Three scenarios:
 *   1. INST_LEADER full path (KPI + filter refetch + healthy network).
 *   2. catalogDomains 500 → bizDomain selector and matrix disappear.
 *   3. DEPT_LEADER scope is locked (uses a separate storageState).
 *
 * Auth comes from the global setup (`auth.setup.ts`) which writes
 * `e2e/.auth/user.json`. The DEPT_LEADER scenario looks for an optional
 * `E2E_DEPT_LEADER_AUTH` env var pointing at a dept-leader storageState
 * JSON; when unset, the test self-skips so CI without a dept-leader
 * account stays green.
 */

import { expect, test } from "@playwright/test";
import type { ConsoleMessage, Request } from "@playwright/test";

const WORKBENCH_API = /\/api\/workbench\//;

test.describe("workbench · leader overview", () => {
	test("INST_LEADER full path · KPI + time refetch + clean network", async ({ page }) => {
		const pageErrors: Error[] = [];
		const consoleErrors: ConsoleMessage[] = [];
		const failedWorkbenchRequests: Request[] = [];

		page.on("pageerror", (err) => pageErrors.push(err));
		page.on("console", (msg) => {
			if (msg.type() === "error") consoleErrors.push(msg);
		});
		page.on("requestfailed", (req) => {
			if (WORKBENCH_API.test(req.url())) failedWorkbenchRequests.push(req);
		});

		const initialResponse = page.waitForResponse(
			(r) => /\/api\/workbench\/leader-overview/.test(r.url()) && r.status() === 200,
			{ timeout: 15_000 },
		);

		await page.goto("/#/workbench");
		await initialResponse;

		// 4 KPI titles for INST_LEADER
		await expect(page.getByText("所内报表").first()).toBeVisible({ timeout: 10_000 });
		await expect(page.getByText("数据资产").first()).toBeVisible();
		await expect(page.getByText("核心资产（S1）").first()).toBeVisible();
		// "本月访问" is the visit-related KPI title under MONTH range
		await expect(page.getByText(/本月访问|本期访问/).first()).toBeVisible();

		// Switch time range to 本季 and assert refetch
		const refetched = page.waitForResponse(
			(r) =>
				/\/api\/workbench\/leader-overview/.test(r.url()) &&
				/timeRange=QUARTER/.test(r.url()) &&
				r.status() === 200,
			{ timeout: 10_000 },
		);
		await page
			.getByRole("combobox")
			.filter({ hasText: "本月" })
			.first()
			.click();
		await page.getByRole("option", { name: "本季" }).click();
		await refetched;
		await expect(page.getByText("本季访问").first()).toBeVisible();

		expect(pageErrors, `pageerror events: ${pageErrors.map((e) => e.message).join("; ")}`).toHaveLength(0);
		expect(failedWorkbenchRequests, "no /api/workbench/* request should fail").toHaveLength(0);
		// Console errors are noisy in some envs; only fail if any explicitly mentions leader-overview
		const offending = consoleErrors.filter((m) => /leader-overview|workbench/i.test(m.text()));
		expect(offending, offending.map((m) => m.text()).join("\n")).toHaveLength(0);
	});

	test("CatalogDomain API 失败时业务域筛选器消失", async ({ page, context }) => {
		await context.route("**/api/catalog/domains*", (route) =>
			route.fulfill({ status: 500, body: "boom", contentType: "text/plain" }),
		);
		// Tree endpoint sometimes serves the same data — fail it too for parity.
		await context.route("**/api/catalog/domains/tree*", (route) =>
			route.fulfill({ status: 500, body: "boom", contentType: "text/plain" }),
		);

		await page.goto("/#/workbench");
		await expect(page.getByText("所内报表").first()).toBeVisible({ timeout: 15_000 });

		// BizDomainSelect renders nothing → its "全部业务域" placeholder must be absent
		await expect(page.getByText("全部业务域")).toHaveCount(0);
		// DomainMatrix gates on bizDomainAvailable, so its interactive cells must not render
		await expect(page.locator('[role="button"][aria-pressed]')).toHaveCount(0);
		// TopReports block still renders (verifies KPI/reports survive the soft-dep failure)
		await expect(page.getByText(/报表/).first()).toBeVisible();
	});

	test("DEPT_LEADER scope 锁定（dept selector locked）", async ({ browser }) => {
		const deptAuth = process.env.E2E_DEPT_LEADER_AUTH;
		test.skip(!deptAuth, "no dept-leader storageState (set E2E_DEPT_LEADER_AUTH to enable)");

		const ctx = await browser.newContext({ storageState: deptAuth });
		try {
			const p = await ctx.newPage();
			await p.goto("/#/workbench");
			// Locked dept indicator from DeptSelect's non-INST_LEADER branch
			await expect(p.getByTestId("dept-select-locked")).toBeVisible({ timeout: 15_000 });
			await expect(p.getByText(/本部门：/)).toBeVisible();
			// DEPT_LEADER sees 3 KPI cards — "本部门报表" replaces "所内报表"
			await expect(p.getByText("本部门报表").first()).toBeVisible();
		} finally {
			await ctx.close();
		}
	});
});
