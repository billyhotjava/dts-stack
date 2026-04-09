import { describe, expect, it } from "vitest";
import { formatPortalSessionStatus, resolveSessionStatusTone } from "./sessionStatus.helpers";

describe("sessionStatus.helpers", () => {
	it("formats remaining minutes for active sessions", () => {
		expect(formatPortalSessionStatus({ authenticated: true, remainingSeconds: 620 })).toBe("服务器会话剩余约 11 分钟");
	});

	it("formats missing status safely", () => {
		expect(formatPortalSessionStatus(null)).toBe("服务器会话未建立");
	});

	it("marks near-expiry sessions with warning tones", () => {
		expect(resolveSessionStatusTone({ authenticated: true, remainingSeconds: 1200 })).toBe("normal");
		expect(resolveSessionStatusTone({ authenticated: true, remainingSeconds: 600 })).toBe("warning");
		expect(resolveSessionStatusTone({ authenticated: true, remainingSeconds: 180 })).toBe("danger");
	});
});
