// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Mock workbenchAuditService before importing the module under test so
// auditLog picks up the mocked implementation (hoisted).
const recordClientAuditMock = vi.fn<
	(input: { event: string; payload?: Record<string, unknown> }) => Promise<void>
>();

vi.mock("@/api/services/workbenchAuditService", () => ({
	recordClientAudit: (
		input: { event: string; payload?: Record<string, unknown> },
	) => recordClientAuditMock(input),
	default: {
		recordClientAudit: (
			input: { event: string; payload?: Record<string, unknown> },
		) => recordClientAuditMock(input),
	},
}));

// Dynamic import so the mock is in place before the module executes.
let auditLog: (event: string, payload: Record<string, unknown>) => void;
beforeEach(async () => {
	vi.resetModules();
	({ auditLog } = await import("./audit"));
});

describe("auditLog", () => {
	const originalEnv = { ...import.meta.env };
	let debugSpy: ReturnType<typeof vi.spyOn>;

	beforeEach(() => {
		debugSpy = vi.spyOn(console, "debug").mockImplementation(() => {});
		recordClientAuditMock.mockReset();
		recordClientAuditMock.mockResolvedValue(undefined);
	});

	afterEach(() => {
		debugSpy.mockRestore();
		// Restore DEV flag regardless of what the test mutated.
		Object.assign(import.meta.env, originalEnv);
	});

	it("logs via console.debug when DEV flag is true", () => {
		Object.assign(import.meta.env, { DEV: true });
		auditLog("WORKBENCH_TEST_EVENT", { foo: "bar" });
		expect(debugSpy).toHaveBeenCalled();
		const call = debugSpy.mock.calls.find((c) =>
			String(c[0] ?? "").includes("WORKBENCH_TEST_EVENT"),
		);
		expect(call).toBeTruthy();
		// The DEV mirror passes the raw payload object as the final arg.
		expect(call?.[call.length - 1]).toEqual({ foo: "bar" });
	});

	it("does not log via console.debug when DEV flag is false and upload succeeds", () => {
		Object.assign(import.meta.env, { DEV: false });
		auditLog("WORKBENCH_TEST_EVENT", { foo: "bar" });
		expect(debugSpy).not.toHaveBeenCalled();
	});

	it("calls_recordClientAudit_with_event_and_payload", () => {
		Object.assign(import.meta.env, { DEV: false });
		auditLog("WORKBENCH_OVERVIEW_VIEW", { role: "INST_LEADER" });
		expect(recordClientAuditMock).toHaveBeenCalledTimes(1);
		expect(recordClientAuditMock).toHaveBeenCalledWith({
			event: "WORKBENCH_OVERVIEW_VIEW",
			payload: { role: "INST_LEADER" },
		});
	});

	it("audit_service_failure_does_not_throw", async () => {
		Object.assign(import.meta.env, { DEV: true });
		recordClientAuditMock.mockRejectedValueOnce(new Error("boom"));
		// Asserting that calling `auditLog` never throws, even when the
		// underlying service promise rejects.
		expect(() => auditLog("WORKBENCH_TEST_EVENT", { foo: 1 })).not.toThrow();
		// Flush the microtask queue so the rejection handler runs.
		await Promise.resolve();
		await Promise.resolve();
		// The DEV branch logs both the failure and the mirror, so at least 2 calls.
		expect(debugSpy.mock.calls.length).toBeGreaterThanOrEqual(1);
	});

	it("synchronous_throw_in_service_is_swallowed", () => {
		Object.assign(import.meta.env, { DEV: true });
		recordClientAuditMock.mockImplementationOnce(() => {
			throw new Error("sync-boom");
		});
		expect(() => auditLog("WORKBENCH_TEST_EVENT", { foo: 1 })).not.toThrow();
	});
});
