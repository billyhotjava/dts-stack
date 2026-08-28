import { expect, type Page, type Response, test } from "@playwright/test";

test.use({ viewport: { width: 1366, height: 768 } });
test.setTimeout(90_000);

const targetOrigin = new URL(process.env.E2E_BASE_URL ?? "http://127.0.0.1:3001").origin;

type TaskRow = {
	id?: number;
	name?: string;
	revisionNumber?: number;
};

function unwrap<T>(value: unknown): T {
	const record = value as { data?: T };
	return (record?.data ?? value) as T;
}

function isTaskListResponse(response: Response): boolean {
	const url = new URL(response.url());
	return response.request().method() === "GET" && url.pathname.endsWith("/api/ingestion/tasks/list");
}

async function openTaskList(page: Page): Promise<{ task: TaskRow; listUrl: URL }> {
	const listResponsePromise = page.waitForResponse(isTaskListResponse);
	await page.goto("/#/explore/etl/orchestration");
	const listResponse = await listResponsePromise;
	expect(listResponse.ok()).toBeTruthy();

	const body = unwrap<{ content?: TaskRow[] }>(await listResponse.json());
	const task = (body.content ?? []).find((candidate) => Number(candidate.id) > 0 && Boolean(candidate.name));
	expect(task, "真实环境至少需要一个当前账号可访问的 DTS 接入任务").toBeTruthy();

	await expect(page.getByRole("heading", { name: "数据集成流程", exact: true })).toBeVisible();
	await expect(page.getByText("这里只展示纳入 DTS 编排治理", { exact: false })).toBeVisible();
	await expect(page).not.toHaveURL(/taskId=/);

	const table = page.getByRole("table").first();
	await expect(table).toBeVisible();
	for (const heading of [
		"任务名称",
		"生命周期",
		"来源",
		"目标资产",
		"任务版本",
		"调度",
		"质量验证",
		"最近运行",
		"操作",
	]) {
		await expect(table.getByRole("columnheader", { name: heading, exact: true })).toBeVisible();
	}

	const listUrl = new URL(listResponse.url());
	expect(listUrl.searchParams.get("page")).toBe("0");
	expect(listUrl.searchParams.get("size")).toBe("10");
	return { task: task as TaskRow, listUrl };
}

test("任务编排默认进入 DTS 任务列表，运行实例按任务和版本收敛", async ({ page }) => {
	const pageErrors: string[] = [];
	const consoleErrors: string[] = [];
	const failedRequests: string[] = [];
	const badResponses: string[] = [];
	const requestedUrls: string[] = [];

	page.on("pageerror", (error) => pageErrors.push(error.message));
	page.on("console", (message) => {
		if (message.type() === "error") consoleErrors.push(message.text());
	});
	page.on("request", (request) => requestedUrls.push(request.url()));
	page.on("requestfailed", (request) => failedRequests.push(`${request.method()} ${request.url()}`));
	page.on("response", (response) => {
		if (response.status() >= 400 && response.url().startsWith(targetOrigin)) {
			badResponses.push(`${response.status()} ${response.request().method()} ${response.url()}`);
		}
	});

	const { task } = await openTaskList(page);
	await page.screenshot({ path: "/tmp/sprint103-e2e/task-list-1366x768.png", fullPage: true });

	const taskRow = page
		.getByRole("row")
		.filter({ hasText: task.name as string })
		.first();
	await expect(taskRow).toBeVisible();
	await taskRow.getByRole("button", { name: /设\s*计/ }).click();
	await expect(page).toHaveURL(new RegExp(`taskId=${task.id}(?:&|$)`));
	await expect(page.getByRole("heading", { name: new RegExp(`任务编排.*${task.name}`) })).toBeVisible();
	await expect(page.getByRole("button", { name: "返回任务列表", exact: true })).toBeVisible();
	await page.getByRole("button", { name: "返回任务列表", exact: true }).click();
	await expect(page).not.toHaveURL(/taskId=/);

	const refreshed = await openTaskList(page);
	const runRow = page
		.getByRole("row")
		.filter({ hasText: refreshed.task.name as string })
		.first();
	await runRow.getByRole("button", { name: "运行实例", exact: true }).click();
	await expect(page).toHaveURL(
		new RegExp(`taskId=${refreshed.task.id}.*tab=runs|tab=runs.*taskId=${refreshed.task.id}`),
	);
	await expect(page.getByText(`仅显示当前 DTS 接入任务 #${refreshed.task.id}`, { exact: false })).toBeVisible();

	const executionPath = `/api/ingestion/tasks/${refreshed.task.id}/executions`;
	if (refreshed.task.revisionNumber) {
		await expect(page.getByText(`当前版本 R${refreshed.task.revisionNumber}`, { exact: true })).toBeVisible();
		await expect
			.poll(() =>
				requestedUrls.some((raw) => {
					const url = new URL(raw);
					return (
						url.pathname.endsWith(executionPath) &&
						url.searchParams.get("revisionNumber") === String(refreshed.task.revisionNumber)
					);
				}),
			)
			.toBeTruthy();

		const requestCount = requestedUrls.length;
		const revisionSelect = page
			.locator(".ant-select")
			.filter({ hasText: `当前版本 R${refreshed.task.revisionNumber}` })
			.first();
		await revisionSelect.click();
		await page.locator(".ant-select-dropdown:visible").getByText("全部历史版本", { exact: true }).click();
		await expect
			.poll(() =>
				requestedUrls.slice(requestCount).some((raw) => {
					const url = new URL(raw);
					return url.pathname.endsWith(executionPath) && !url.searchParams.has("revisionNumber");
				}),
			)
			.toBeTruthy();
	}

	await page.screenshot({ path: "/tmp/sprint103-e2e/task-runs-1366x768.png", fullPage: true });
	expect(requestedUrls.some((url) => /\/api\/(?:etl\/)?airflow\/(?:jobs|dags)/i.test(url))).toBeFalsy();
	expect(
		requestedUrls
			.filter((raw) => new URL(raw).pathname.includes("/executions"))
			.every((raw) => new URL(raw).pathname.includes(`/tasks/${refreshed.task.id}/`)),
	).toBeTruthy();

	await page.setViewportSize({ width: 390, height: 844 });
	await openTaskList(page);
	await expect
		.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1))
		.toBeTruthy();
	await page.screenshot({ path: "/tmp/sprint103-e2e/task-list-390x844.png", fullPage: true });

	expect(pageErrors, `页面运行时异常：\n${pageErrors.join("\n")}`).toEqual([]);
	expect(consoleErrors, `console error：\n${consoleErrors.join("\n")}`).toEqual([]);
	expect(failedRequests, `请求失败：\n${failedRequests.join("\n")}`).toEqual([]);
	expect(badResponses, `HTTP 4xx/5xx：\n${badResponses.join("\n")}`).toEqual([]);
});
