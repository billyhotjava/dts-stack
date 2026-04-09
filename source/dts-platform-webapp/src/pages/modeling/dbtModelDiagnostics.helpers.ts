import type { DbtModelDiagnostics } from "./sqlModeling.types";

export function resolveDiagnosticsStatus(diagnostics: DbtModelDiagnostics | null | undefined): "success" | "warning" | "error" | "default" {
	if (!diagnostics?.success) {
		return "error";
	}
	if (diagnostics.current?.exists === false) {
		return "error";
	}
	if ((diagnostics.current?.rowCount ?? null) === 0) {
		return "warning";
	}
	const meaningfulFindings = (diagnostics.findings || []).filter((item) => !item.includes("未发现明显断链信号"));
	if (meaningfulFindings.length > 0) {
		return "warning";
	}
	return "success";
}

export function formatDiagnosticsRowCount(value: number | null | undefined) {
	if (value == null || Number.isNaN(value)) {
		return "未知";
	}
	return String(value);
}

export function buildDiagnosticsTitle(modelName?: string | null) {
	const normalized = (modelName || "").trim();
	return normalized ? `${normalized} 断链诊断` : "模型断链诊断";
}
