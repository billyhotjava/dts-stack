// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * Sprint-15 F6/T01 unit tests for the workbench client-audit service.
 *
 * We stub the underlying axios-backed `apiClient` so the test boundary is
 * the service contract (URL + body shape + error propagation) without
 * dragging the full axios interceptor chain into the test.
 */

const apiPostMock = vi.fn<
	(config: { url: string; data?: unknown; method?: string }) => Promise<unknown>
>();

vi.mock("../apiClient", () => ({
	default: {
		get: () => Promise.resolve(undefined),
		post: (config: { url: string; data?: unknown }) => apiPostMock(config),
		put: () => Promise.resolve(undefined),
		delete: () => Promise.resolve(undefined),
		request: () => Promise.resolve(undefined),
	},
}));

let recordClientAudit: typeof import("./workbenchAuditService").recordClientAudit;

beforeEach(async () => {
	vi.resetModules();
	apiPostMock.mockReset();
	({ recordClientAudit } = await import("./workbenchAuditService"));
});

afterEach(() => {
	vi.restoreAllMocks();
});

describe("recordClientAudit", () => {
	it("posts_to_workbench_audit_with_event_and_payload", async () => {
		apiPostMock.mockResolvedValueOnce({ ok: true });
		await recordClientAudit({
			event: "WORKBENCH_OVERVIEW_VIEW",
			payload: { role: "INST_LEADER" },
		});
		expect(apiPostMock).toHaveBeenCalledTimes(1);
		const config = apiPostMock.mock.calls[0][0];
		expect(config.url).toBe("/workbench/audit");
		expect(config.data).toEqual({
			event: "WORKBENCH_OVERVIEW_VIEW",
			payload: { role: "INST_LEADER" },
		});
	});

	it("resolves_void_even_when_backend_returns_payload", async () => {
		apiPostMock.mockResolvedValueOnce({ ok: true, extra: 1 });
		const result = await recordClientAudit({
			event: "WORKBENCH_FILTER_CHANGE",
			payload: { dim: "timeRange", value: "QUARTER" },
		});
		expect(result).toBeUndefined();
	});

	it("propagates_rejection_so_callers_can_swallow_at_boundary", async () => {
		apiPostMock.mockRejectedValueOnce(new Error("network down"));
		await expect(
			recordClientAudit({ event: "WORKBENCH_DOMAIN_DRILL", payload: { domain: "FIN" } }),
		).rejects.toThrow("network down");
	});

	it("accepts_events_without_payload", async () => {
		apiPostMock.mockResolvedValueOnce({ ok: true });
		await recordClientAudit({ event: "WORKBENCH_DOMAIN_API_EMPTY" });
		expect(apiPostMock.mock.calls[0][0].data).toEqual({
			event: "WORKBENCH_DOMAIN_API_EMPTY",
		});
	});
});
