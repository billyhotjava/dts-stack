import fs from "node:fs";
import path from "node:path";
import { expect, type Page, test } from "@playwright/test";

const evidenceDir = path.resolve(
	process.cwd(),
	"../../worklog/v2.2.3/sprint-79-202607-modeling-workspace-convergence/it/evidence/baseline",
);

type BrowserFailures = {
	pageErrors: string[];
	requestFailures: string[];
	httpFailures: string[];
};

const observeFailures = (page: Page): BrowserFailures => {
	const failures: BrowserFailures = { pageErrors: [], requestFailures: [], httpFailures: [] };
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) =>
		failures.requestFailures.push(`${request.method()} ${request.url()} ${request.failure()?.errorText ?? "unknown"}`),
	);
	page.on("response", (response) => {
		if (response.status() < 400) return;
		const pathname = new URL(response.url()).pathname;
		if (pathname.startsWith("/api/")) {
			failures.httpFailures.push(`${response.status()} ${response.request().method()} ${pathname}`);
		}
	});
	return failures;
};

test.beforeAll(() => {
	fs.mkdirSync(evidenceDir, { recursive: true });
});

test("authenticated modeling baseline reaches canonical workbench and model center", async ({ page }) => {
	const failures = observeFailures(page);

	await page.goto("/#/modeling/workbench");
	await expect(page.getByTestId("warehouse-plan-workbench")).toBeVisible();
	await expect(page).not.toHaveURL(/\/login/);
	await page.screenshot({
		path: path.join(evidenceDir, "formal-workbench-authenticated.png"),
		fullPage: true,
	});

	await page.goto("/#/modeling/models");
	await expect(page.getByTestId("model-center-page")).toBeVisible();
	await page.screenshot({
		path: path.join(evidenceDir, "formal-model-center-authenticated.png"),
		fullPage: true,
	});

	expect(failures.pageErrors).toEqual([]);
	expect(failures.requestFailures).toEqual([]);
	expect(failures.httpFailures).toEqual([]);
});
