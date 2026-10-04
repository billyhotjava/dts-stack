import { chromium } from "@playwright/test";

const browser = await chromium.launch({ executablePath: "/usr/bin/google-chrome" });
const context = await browser.newContext({
	storageState: "/opt/prod/s10/v2.2.3/source/dts-platform-webapp/e2e/.auth/user.json",
});
const page = await context.newPage();
await page.goto("https://bi.yuzhicloud.com/#/data-modeling/dimensions/workbench", { waitUntil: "domcontentloaded" });
await page.waitForTimeout(6000);

const dump = async (label) => {
	const active = await page.locator(".dmx-layer-tabs button.active").innerText().catch(() => "(none)");
	const tree = await page.locator(".dmx-object-tree").innerText().catch(() => "(no tree)");
	console.log(`[${label}] active=${active} tree=${tree.replace(/\n+/g, " | ").slice(0, 260)}`);
};

await dump("initial");
for (const layer of ["贴源层", "应用层", "公共层"]) {
	await page.locator(".dmx-layer-tabs").getByRole("button", { name: layer, exact: true }).click();
	await page.waitForTimeout(600);
	await dump(layer);
}
await browser.close();
