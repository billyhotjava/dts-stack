import { test, expect } from "@playwright/test";

test("operations discovers a late plan, confirms deployment and separately enables it", async ({ page }, testInfo) => {
    const errors: string[] = [], writes: Array<{ path: string; data: unknown }> = [];
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("console", (message) => { if (message.type() === "error") errors.push(message.text()); });
    page.on("response", (response) => { if (response.status() >= 400) errors.push(`HTTP ${response.status()} ${new URL(response.url()).pathname}`); });
    await page.addInitScript(() => {
        localStorage.setItem("dts.platform.userStore", JSON.stringify({ state: {
            userInfo: { username: "feature15-reviewer", fullName: "运行维护验收", roles: ["ROLE_OP_ADMIN"], permissions: ["modeling.manage"], enabled: true },
            userToken: { accessToken: "mock-feature15" } }, version: 0 }));
        localStorage.setItem("dts.platform.session.loginTs", String(Date.now()));
        localStorage.setItem("dts.platform.session.lastActivity", String(Date.now()));
    });
    let deployed = false, enabled = false;
    await page.route("**/api/**", async (route) => {
        const path = new URL(route.request().url()).pathname.replace(/^\/api/, "");
        const respond = (data: unknown) => route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ status: 200, data, message: "OK" }) });
        if (path === "/session/status") return respond({ authenticated: true, remainingSeconds: 3600 });
        if (path === "/menu/tree") return respond([]);
        if (path === "/modeling/warehouse-plans") return respond(Array.from({ length: 61 }, (_, i) => ({ id: `plan-${i}`, name: `财务建模规划${i}`, code: `P${i}`, tenantId: "t", ownerId: "a" })));
        if (path.endsWith("/execution-bindings/publications")) return respond(path.includes("plan-60/") ? [{ candidateId: "candidate", candidateVersion: 9, environment: "prod", models: ["预算明细 · r3", "预算汇总 · r2"], releaseIds: ["release-a", "release-b"], bindingVersion: deployed ? 1 : 0, canDeploy: true }] : []);
        if (path.endsWith("/execution-bindings/workspace")) return respond({ planId: "plan-60", state: deployed ? "READY" : "NOT_DEPLOYED", bindings: deployed && path.includes("plan-60/") ? [{ id: "binding", version: enabled ? 2 : 1, environment: "prod", state: enabled ? "ONLINE" : "DISABLED", scheduleMode: "MANUAL_ONLY", deploymentStatus: "ACTIVE", airflowDagId: "dts_plan_budget", latestOperationalRun: {}, latestRelation: {}, allowedActions: enabled ? ["RUN_NOW"] : ["ENABLE"] }] : [] });
        if (route.request().method() === "POST" && (path.endsWith("/deploy") || path.endsWith("/enable"))) {
            writes.push({ path, data: route.request().postDataJSON() });
            if (path.endsWith("/deploy")) deployed = true; else enabled = true;
            return route.fulfill({ status: 202, contentType: "application/json", body: JSON.stringify({ status: 200, data: null, message: "OK" }) });
        }
        return respond([]);
    });
    await page.goto("/ops/instances?tab=schedule&planId=plan-60");
    await page.getByRole("combobox", { name: "选择部署版本" }).click();
    await page.getByText("财务建模规划60 · 生产环境", { exact: true }).click();
    await page.getByRole("button", { name: "部署版本", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "确认部署范围" });
    await expect(dialog).toContainText("预算明细 · r3");
    await page.screenshot({ path: testInfo.outputPath("deployment-confirm-desktop.png"), fullPage: true });
    await dialog.getByRole("button", { name: "确认部署", exact: true }).click();
    await expect(page.getByText("已部署，未启用", { exact: true })).toBeVisible();
    expect(writes).toHaveLength(1);
    expect(writes[0].data).toEqual({ candidateId: "candidate", candidateVersion: 9, releaseIds: ["release-a", "release-b"], bindingVersion: 0 });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({ path: testInfo.outputPath("deployed-narrow.png"), fullPage: true });
    await page.getByRole("button", { name: "启用运行", exact: true }).click();
    await expect(page.getByRole("button", { name: "立即运行并核验" })).toBeVisible();
    expect(writes).toHaveLength(2);
    expect(writes[1].data).toEqual({ version: 1 });
    expect(errors).toEqual([]);
});
