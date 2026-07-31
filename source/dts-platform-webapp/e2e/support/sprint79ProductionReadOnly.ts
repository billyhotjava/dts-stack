import type { Page, Request } from "@playwright/test";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const DTS_API_PREFIXES = ["/api/", "/admin/api/", "/analytics/api/", "/bi/api/"];

export type Sprint79ReadOnlyFailures = {
	pageErrors: string[];
	requestFailures: string[];
	httpFailures: string[];
	modelingWrites: string[];
};

export const isProtectedDtsApiWrite = (method: string, url: string): boolean => {
	const pathname = new URL(url, "https://dts.invalid").pathname;
	return DTS_API_PREFIXES.some((prefix) => pathname.startsWith(prefix)) && !SAFE_METHODS.has(method.toUpperCase());
};

export const shouldIgnoreReadOnlyAbort = (errorText: string, blockedByBarrier: boolean): boolean =>
	errorText === "net::ERR_BLOCKED_BY_CLIENT" && blockedByBarrier;

export const installSprint79ProductionReadOnlyBarrier = async (page: Page): Promise<Sprint79ReadOnlyFailures> => {
	const failures: Sprint79ReadOnlyFailures = {
		pageErrors: [],
		requestFailures: [],
		httpFailures: [],
		modelingWrites: [],
	};
	const blockedRequests = new WeakSet<Request>();
	page.on("pageerror", (error) => failures.pageErrors.push(error.message));
	page.on("requestfailed", (request) => {
		const errorText = request.failure()?.errorText ?? "unknown";
		if (shouldIgnoreReadOnlyAbort(errorText, blockedRequests.has(request))) return;
		failures.requestFailures.push(`${request.method()} ${new URL(request.url()).pathname} ${errorText}`);
	});
	page.on("response", (response) => {
		if (response.status() < 400) return;
		const pathname = new URL(response.url()).pathname;
		if (DTS_API_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
			failures.httpFailures.push(`${response.status()} ${response.request().method()} ${pathname}`);
		}
	});
	await page.route("**/*", async (route) => {
		const request = route.request();
		if (!isProtectedDtsApiWrite(request.method(), request.url())) {
			await route.continue();
			return;
		}
		blockedRequests.add(request);
		failures.modelingWrites.push(`${request.method()} ${new URL(request.url()).pathname}`);
		await route.abort("blockedbyclient");
	});
	return failures;
};
