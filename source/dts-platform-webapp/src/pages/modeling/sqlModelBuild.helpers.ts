export type BuildOperation = "compile" | "test" | "docs" | "build";

export type BuildSummaryLike = {
	present?: boolean;
	invocationId?: string;
	generatedAt?: string;
	command?: string;
	status?: string;
	total?: number;
	success?: number;
	failed?: number;
	skipped?: number;
	failures?: Array<{ name?: string; message?: string; status?: string }>;
};

import { normalizeText } from "@/utils/textUtils";

export type BuildStatusPresentation = {
	type: "success" | "error" | "warning" | "info";
	message: string;
};

const normalizeLower = (value?: string) => normalizeText(value).toLowerCase();

export function buildCommandHint(operation: BuildOperation, selector?: string) {
	const normalizedSelector = normalizeText(selector);
	return normalizedSelector ? `dbt ${operation} --select ${normalizedSelector}` : `dbt ${operation}`;
}

export function buildReleaseSelector(selector?: string) {
	const normalizedSelector = normalizeText(selector);
	if (!normalizedSelector || normalizedSelector === "all") {
		return normalizedSelector || "all";
	}
	if (normalizedSelector.startsWith("+")) {
		return normalizedSelector;
	}
	if (
		normalizedSelector.startsWith("tag:") ||
		normalizedSelector.startsWith("model:") ||
		normalizedSelector.startsWith("source:")
	) {
		return `+${normalizedSelector}`;
	}
	return normalizedSelector;
}

export function inferBuildOperationFromCommand(command?: string): BuildOperation | "run" | "build" | null {
	const normalizedCommand = normalizeLower(command);
	if (!normalizedCommand) {
		return null;
	}
	if (normalizedCommand === "compile" || normalizedCommand.startsWith("compile ") || normalizedCommand.includes("dbt compile")) {
		return "compile";
	}
	if (normalizedCommand === "test" || normalizedCommand.startsWith("test ") || normalizedCommand.includes("dbt test")) {
		return "test";
	}
	if (normalizedCommand === "docs" || normalizedCommand.startsWith("docs ") || normalizedCommand.includes("dbt docs")) {
		return "docs";
	}
	if (normalizedCommand === "build" || normalizedCommand.startsWith("build ") || normalizedCommand.includes("dbt build")) {
		return "build";
	}
	if (normalizedCommand === "run" || normalizedCommand.startsWith("run ") || normalizedCommand.includes("dbt run")) {
		return "run";
	}
	return null;
}

export function createPendingBuildSummary(operation: BuildOperation, selector?: string): BuildSummaryLike {
	return {
		present: true,
		command: buildCommandHint(operation, selector),
		status: "RUNNING",
		generatedAt: new Date().toISOString(),
		total: 0,
		success: 0,
		failed: 0,
		skipped: 0,
		failures: [],
	};
}

export function createFailedBuildSummary(operation: BuildOperation, selector?: string, message?: string): BuildSummaryLike {
	return {
		present: true,
		command: buildCommandHint(operation, selector),
		status: "FAILED",
		generatedAt: new Date().toISOString(),
		total: 0,
		success: 0,
		failed: 1,
		skipped: 0,
		failures: [
			{
				name: "dbt_run",
				status: "FAILED",
				message: message || `dbt ${operation} 失败，请查看执行日志`,
			},
		],
	};
}

export function matchesTriggeredBuildSummary(
	summary: BuildSummaryLike | null | undefined,
	options: {
		operation: BuildOperation;
		selector?: string;
		baselineGeneratedAt?: string;
		baselineInvocationId?: string;
	},
) {
	if (!summary?.present) return false;
	if (!commandMatchesOperation(summary.command, options.operation)) return false;
	if (!isNewerBuild(summary, options.baselineGeneratedAt, options.baselineInvocationId)) return false;
	return commandMatchesSelector(summary.command, options.selector);
}

export function describeBuildSummary(
	summary: BuildSummaryLike | null | undefined,
	options: { operationLabel: string; fallbackMessage: string },
): BuildStatusPresentation {
	if (!summary) {
		return {
			type: "info",
			message: options.fallbackMessage,
		};
	}
	const status = normalizeLower(summary.status);
	if (status === "success") {
		const failureCount = Array.isArray(summary.failures) ? summary.failures.length : 0;
		if (failureCount === 0) {
			return {
				type: "success",
				message: `${options.operationLabel}通过，未发现失败节点`,
			};
		}
	}
	if (status === "failed" || status === "error") {
		return {
			type: "error",
			message: `${options.operationLabel}失败，请查看失败节点或执行日志`,
		};
	}
	if (status === "running" || status === "queued" || status === "submitted") {
		return {
			type: "info",
			message: `${options.operationLabel}进行中，请等待 Airflow 任务完成`,
		};
	}
	if (status === "skipped") {
		return {
			type: "warning",
			message: `最近一次${options.operationLabel}结果为 SKIPPED，不能作为发布依据，请重新执行`,
		};
	}
	return {
		type: "info",
		message: `${options.operationLabel}结果尚未就绪，请等待任务完成并刷新`,
	};
}

function commandMatchesOperation(command?: string, operation?: BuildOperation) {
	const normalizedCommand = normalizeLower(command);
	return !!normalizedCommand && normalizedCommand.includes(`dbt ${normalizeLower(operation)}`);
}

function commandMatchesSelector(command?: string, selector?: string) {
	const normalizedSelector = normalizeLower(selector);
	if (!normalizedSelector || normalizedSelector === "all") {
		return true;
	}
	const normalizedCommand = normalizeLower(command);
	return (
		normalizedCommand.includes(`--select ${normalizedSelector}`) ||
		normalizedCommand.includes(`--models ${normalizedSelector}`)
	);
}

function isNewerBuild(summary: BuildSummaryLike, baselineGeneratedAt?: string, baselineInvocationId?: string) {
	const nextInvocationId = normalizeText(summary.invocationId);
	const baselineInvocation = normalizeText(baselineInvocationId);
	if (nextInvocationId && baselineInvocation && nextInvocationId !== baselineInvocation) {
		return true;
	}
	const nextGeneratedAt = normalizeText(summary.generatedAt);
	const baselineGenerated = normalizeText(baselineGeneratedAt);
	if (!baselineGenerated) {
		return true;
	}
	return !!nextGeneratedAt && nextGeneratedAt !== baselineGenerated;
}
