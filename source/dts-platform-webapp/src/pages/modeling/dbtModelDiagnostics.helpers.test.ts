import { describe, expect, test } from "vitest";
import {
	buildDiagnosticsTitle,
	formatDiagnosticsRowCount,
	resolveDiagnosticsStatus,
} from "./dbtModelDiagnostics.helpers";

describe("dbtModelDiagnostics.helpers", () => {
	test("resolveDiagnosticsStatus returns error when backend marks diagnostics failed", () => {
		expect(resolveDiagnosticsStatus({ success: false })).toBe("error");
	});

	test("resolveDiagnosticsStatus returns warning when current model has zero rows", () => {
		expect(resolveDiagnosticsStatus({ success: true, current: { exists: true, rowCount: 0 } })).toBe("warning");
	});

	test("resolveDiagnosticsStatus returns success when model has rows and no findings", () => {
		expect(resolveDiagnosticsStatus({ success: true, current: { exists: true, rowCount: 12 }, findings: [] })).toBe("success");
	});

	test("resolveDiagnosticsStatus ignores benign no-issue finding", () => {
		expect(
			resolveDiagnosticsStatus({
				success: true,
				current: { exists: true, rowCount: 12 },
				findings: ["未发现明显断链信号，可继续查看上游 relation 行数和最近日志"],
			}),
		).toBe("success");
	});

	test("formatDiagnosticsRowCount falls back to unknown for nullish values", () => {
		expect(formatDiagnosticsRowCount(null)).toBe("未知");
		expect(formatDiagnosticsRowCount(undefined)).toBe("未知");
	});

	test("buildDiagnosticsTitle includes model name when present", () => {
		expect(buildDiagnosticsTitle("biz_dws_progress_monthly_v2")).toBe("biz_dws_progress_monthly_v2 断链诊断");
	});
});
