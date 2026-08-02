export type QualityDataset = {
	id: string;
	name: string;
	schemaName?: string;
	tableName?: string;
	hiveDatabase?: string;
	hiveTable?: string;
	sourceId?: string;
	domainId?: string;
	domainName?: string;
};

export type QualityRule = {
	id: string;
	code?: string;
	name?: string;
	type?: string;
	severity?: string;
	datasetId?: string;
	enabled?: boolean;
	category?: string;
	description?: string;
	owner?: string;
	dataLevel?: string;
	frequencyCron?: string;
	executor?: string;
	template?: boolean;
	definition?: unknown;
	bindings?: Array<{ id?: string; ruleVersionId?: string; datasetId?: string }>;
	latestVersion?: { id?: string; ruleId?: string; version?: number; status?: string; definition?: string };
};

export type QualityTemplate = {
	id: string;
	code?: string;
	name?: string;
	description?: string;
	category?: string;
	sqlTemplate?: string;
	paramSchema?: unknown;
	severityDefault?: string;
	actionDefault?: string;
	builtin?: boolean;
};

export type QualityTask = {
	id: string;
	name?: string;
	datasetId?: string;
	ruleId?: string;
	ownerDept?: string;
	intervalMinutes?: number;
	enabled?: boolean;
	lastTriggeredAt?: string;
};

export type QualityRun = {
	id: string;
	runId?: string;
	time?: string;
	passRate?: number | null;
	ruleId?: string;
	ruleVersionId?: string;
	datasetId?: string;
	triggerType?: string;
	status?: string;
	startedAt?: string;
	finishedAt?: string;
	durationMs?: number;
	message?: string;
	errorCategory?: string;
	executedSql?: string;
	rowsTotal?: number;
	failingRowCount?: number;
	failingRows?: number;
	metrics?: Array<Record<string, unknown>>;
	failingRowsSample?: Array<Record<string, unknown>>;
};

export const getQualityRunCounts = (
	run?: Pick<QualityRun, "rowsTotal" | "failingRowCount" | "status" | "errorCategory">,
) => {
	const total = Math.max(0, Number(run?.rowsTotal || 0));
	const failed = Math.min(total, Math.max(0, Number(run?.failingRowCount || 0)));
	const status = String(run?.status || "").toUpperCase();
	const errorCategory = String(run?.errorCategory || "").toUpperCase();
	const pending = ["QUEUED", "RUNNING"].includes(status);
	const executionFailed =
		status === "FAILED" &&
		(errorCategory ? errorCategory !== "QUALITY_VIOLATION" : Number(run?.failingRowCount || 0) <= 0);
	const passed = executionFailed ? 0 : Math.max(0, total - failed);
	const passRate = total && !executionFailed ? Math.round((passed / total) * 10000) / 100 : 0;
	const hasStatistics = !pending && !executionFailed && total > 0;
	return { total, failed, passed, passRate, hasStatistics };
};

export const isExecutableQualityRule = (
	rule?: Pick<QualityRule, "bindings" | "enabled" | "latestVersion">,
	datasetId?: string,
) => {
	if (!rule || rule.enabled === false || String(rule.latestVersion?.status || "").toUpperCase() !== "PUBLISHED") {
		return false;
	}
	const boundDatasetIds = (rule.bindings || []).map((binding) => String(binding.datasetId || "")).filter(Boolean);
	return datasetId ? boundDatasetIds.includes(datasetId) : boundDatasetIds.length > 0;
};

export const hasEffectiveQualityScore = (score?: { dimensions?: unknown[]; trend?: unknown[] }) =>
	Boolean(score && ((score.dimensions?.length || 0) > 0 || (score.trend?.length || 0) > 0));

export const toList = <T>(response: unknown): T[] => {
	if (Array.isArray(response)) return response as T[];
	if (response && typeof response === "object" && Array.isArray((response as { content?: unknown }).content)) {
		return (response as { content: T[] }).content;
	}
	return [];
};

export const collectCompletePages = async <T extends { id?: unknown }>(
	fetchPage: (page: number, size: number) => Promise<unknown>,
	pageSize = 200,
): Promise<{ rows: T[]; complete: boolean }> => {
	const firstResponse = await fetchPage(0, pageSize);
	if (Array.isArray(firstResponse)) return { rows: firstResponse as T[], complete: false };
	if (!firstResponse || typeof firstResponse !== "object") return { rows: [], complete: false };

	const firstPage = firstResponse as { content?: unknown; total?: unknown; totalElements?: unknown };
	if (!Array.isArray(firstPage.content)) return { rows: [], complete: false };
	const rawTotal = firstPage.totalElements ?? firstPage.total;
	const total = Number(rawTotal);
	if (!Number.isSafeInteger(total) || total < 0) return { rows: firstPage.content as T[], complete: false };
	if (total === 0) return { rows: [], complete: firstPage.content.length === 0 };

	const rows: T[] = [];
	const seen = new Set<string>();
	const append = (items: T[]) => {
		let added = 0;
		for (const item of items) {
			const id = String(item?.id ?? "").trim();
			if (!id) return -1;
			if (seen.has(id)) continue;
			seen.add(id);
			rows.push(item);
			added += 1;
		}
		return added;
	};

	const firstAdded = append(firstPage.content as T[]);
	if (firstAdded < 0 || rows.length > total) return { rows, complete: false };
	if (firstPage.content.length === 0) return { rows, complete: false };
	const maximumPages = Math.min(Math.ceil(total / firstPage.content.length) + 1, 1_000);
	for (let page = 1; rows.length < total && page < maximumPages; page += 1) {
		const response = await fetchPage(page, pageSize);
		if (!response || typeof response !== "object" || Array.isArray(response)) return { rows, complete: false };
		const payload = response as { content?: unknown; total?: unknown; totalElements?: unknown };
		const nextTotal = Number(payload.totalElements ?? payload.total);
		if (!Array.isArray(payload.content) || nextTotal !== total || payload.content.length === 0) {
			return { rows, complete: false };
		}
		if (append(payload.content as T[]) <= 0 || rows.length > total) return { rows, complete: false };
	}
	return { rows, complete: rows.length === total };
};

export const displayName = (value: unknown, fallback = "-") => {
	const text = String(value ?? "").trim();
	return text || fallback;
};
