import { expect, test } from "@playwright/test";

test("真实运行态仅保留数据集成入口并兼容旧编排地址", async ({ page }) => {
	await page.setViewportSize({ width: 1366, height: 768 });
	const pageErrors: string[] = [];
	page.on("pageerror", (error) => pageErrors.push(error.message));

	const menuResponse = await page.request.get("/api/menu/tree");
	expect(menuResponse.ok(), `菜单接口返回 ${menuResponse.status()}`).toBeTruthy();
	const menuPayload = JSON.stringify(await menuResponse.json());
	expect(menuPayload).not.toContain("studioOrchestration");

	await page.goto("/#/foundation/data-sources", { waitUntil: "domcontentloaded" });
	await expect(page.getByRole("heading", { name: "接入概览", exact: true })).toBeVisible();
	await expect(page.getByRole("table").first()).toBeVisible();
	await expect(page.getByText("任务编排", { exact: true })).toHaveCount(0);

	await page.getByRole("button", { name: "新建接入", exact: true }).click();
	for (const item of ["数据库接入", "API 接入", "离线文件接入"]) {
		await expect(page.getByRole("menuitem", { name: item, exact: true })).toBeVisible();
	}
	await page.keyboard.press("Escape");

	await page.goto("/#/explore/etl/orchestration", { waitUntil: "domcontentloaded" });
	await expect(page).toHaveURL(/\/#\/foundation\/data-sources(?:[?#]|$)/);
	await expect(page.getByRole("heading", { name: "接入概览", exact: true })).toBeVisible();
	await page.screenshot({ path: "/tmp/sprint103-data-integration-live-1366x768.png", fullPage: true });

	expect(pageErrors, `页面运行时异常：\n${pageErrors.join("\n")}`).toEqual([]);
});
