// 发布门禁证据结构化：对齐 dbt build 的门禁语义——落标约束(schema)→编译(compile)→测试(test)→运行(run results)。
// 每项 check 三态：ready=证据可查，missing=证据缺失（含后端 API 缺口，apiName 必填），blocked=上下文对象无效。
// 聚合 verdict：任一 blocked→fail；有 missing→warn；全 ready→pass。
import {
	buildJourneyUrl,
	type DataProductJourneyContextParams,
	type JourneyContextParamKey,
} from "./journeyContext";
import {
	toArtifactValidationMap,
	type ArtifactValidationMap,
	type ArtifactValidationResult,
} from "./journeyArtifactValidation";

export type GateCheckKey = "standardsCoverage" | "compile" | "test" | "run";

export type GateCheckStatus = "ready" | "missing" | "blocked";

export type GateCheck = {
	key: GateCheckKey;
	label: string;
	status: GateCheckStatus;
	detail: string;
	evidenceUrl?: string;
	apiName?: string;
};

export type GateVerdict = "pass" | "warn" | "fail";

export type GateEvidence = {
	checks: GateCheck[];
	verdict: GateVerdict;
};

export type GateEvidenceOptions = {
	validations?: ArtifactValidationMap | ArtifactValidationResult[];
};

type GateCheckDefinition = {
	key: GateCheckKey;
	label: string;
	artifactParam?: JourneyContextParamKey;
	readyDetail: (value: string) => string;
	missingDetail: string;
	evidenceRoute: string;
	apiName?: string;
};

const GATE_CHECK_DEFINITIONS: GateCheckDefinition[] = [
	{
		key: "standardsCoverage",
		label: "落标覆盖",
		artifactParam: "standardDraftId",
		readyDetail: (value) => `字段落标草稿 ${value} 已生成，标准约束可追溯`,
		missingDetail: "缺少字段落标草稿：请先套用标准包生成落标草稿",
		evidenceRoute: "/foundation/standard-package",
	},
	{
		key: "compile",
		label: "模型编译",
		artifactParam: "modelId",
		readyDetail: (value) => `模型 ${value} 已生成 SQL 草稿，可进入编译验证`,
		missingDetail: "缺少模型：请从标准草稿生成模型候选",
		evidenceRoute: "/studio/sql-modeling",
	},
	{
		key: "test",
		label: "数据测试",
		readyDetail: () => "测试结果可查",
		missingDetail: "测试结果接口未接入：暂无法自动出示测试证据",
		evidenceRoute: "/governance/quality",
		apiName: "GET /api/dbt/test-results",
	},
	{
		key: "run",
		label: "运行结果",
		artifactParam: "runId",
		readyDetail: (value) => `运行 ${value} 的实例与日志可查`,
		missingDetail: "缺少运行记录：请编排开发任务并执行一次运行",
		evidenceRoute: "/ops/instances",
		apiName: "GET /api/ops/run-evidence",
	},
];

export const resolveGateVerdict = (checks: GateCheck[]): GateVerdict => {
	if (checks.some((check) => check.status === "blocked")) return "fail";
	if (checks.some((check) => check.status === "missing")) return "warn";
	return "pass";
};

export const buildGateEvidence = (
	params: DataProductJourneyContextParams,
	options: GateEvidenceOptions = {},
): GateEvidence => {
	const validationMap = Array.isArray(options.validations)
		? toArtifactValidationMap(options.validations)
		: (options.validations ?? {});
	const checks: GateCheck[] = GATE_CHECK_DEFINITIONS.map((definition) => {
		const value = definition.artifactParam ? params[definition.artifactParam] : undefined;
		const validation = definition.artifactParam && value ? validationMap[definition.artifactParam] : undefined;
		const evidenceUrl = buildJourneyUrl(definition.evidenceRoute, params as Record<string, string | null | undefined>);
		if (validation?.status === "invalid") {
			return {
				key: definition.key,
				label: definition.label,
				status: "blocked",
				detail: validation.reason ?? `${definition.label}对应的上下文对象无效`,
				evidenceUrl,
				apiName: validation.apiName ?? definition.apiName,
			};
		}
		// test 项当前没有可推导的前端证据，接口未接入前恒为 missing 并标注 apiName。
		const ready = definition.key === "test" ? false : Boolean(value);
		return {
			key: definition.key,
			label: definition.label,
			status: ready ? "ready" : "missing",
			detail: ready && value ? definition.readyDetail(value) : definition.missingDetail,
			evidenceUrl,
			apiName: ready ? undefined : definition.apiName,
		};
	});
	return { checks, verdict: resolveGateVerdict(checks) };
};
