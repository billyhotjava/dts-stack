import {
	E2E_DATA_PRODUCT_JOURNEY,
	buildJourneyUrl,
	type DataProductJourneyContextParams,
	type JourneyContextParamKey,
} from "./journeyContext";
import { buildGateEvidence, type GateEvidence, type GateEvidenceOptions } from "./gateEvidence";

export type DataProductAcceptanceEvidenceGroupKey =
	| "gate"
	| "source"
	| "standards"
	| "model"
	| "metrics"
	| "service"
	| "quality"
	| "permission"
	| "operation"
	| "audit";

export type DataProductAcceptanceEvidenceStatus = "ready" | "missing" | "blocked";

export type DataProductAcceptanceEvidenceGroup = {
	key: DataProductAcceptanceEvidenceGroupKey;
	title: string;
	description: string;
	route: string;
	url: string;
	status: DataProductAcceptanceEvidenceStatus;
	requiredParam?: JourneyContextParamKey;
	paramValue?: string;
	missingReason?: string;
	recoveryLabel: string;
};

export type DataProductAcceptancePackage = {
	title: string;
	journey: typeof E2E_DATA_PRODUCT_JOURNEY;
	groups: DataProductAcceptanceEvidenceGroup[];
	gateEvidence: GateEvidence;
	readyCount: number;
	missingCount: number;
	blockedCount: number;
	summary: string;
};

type AcceptanceEvidenceDefinition = {
	key: DataProductAcceptanceEvidenceGroupKey;
	title: string;
	description: string;
	route: string;
	requiredParam?: JourneyContextParamKey;
	blockerWhenMissing?: boolean;
	missingReason: string;
};

export const ACCEPTANCE_EVIDENCE_GROUPS: AcceptanceEvidenceDefinition[] = [
	{
		key: "source",
		title: "来源",
		description: "数据源连接、Schema 探测和同步预检。",
		route: "/foundation/data-sources",
		requiredParam: "sourceId",
		missingReason: "缺少来源证据：请先选择数据源并完成连接测试。",
	},
	{
		key: "standards",
		title: "标准",
		description: "标准包、数据元、码表和字段落标草稿。",
		route: "/foundation/standard-package",
		requiredParam: "standardDraftId",
		missingReason: "缺少标准证据：请导入标准包或生成字段落标草稿。",
	},
	{
		key: "model",
		title: "模型",
		description: "低代码/SQL 模型草稿、字段映射和发布门禁。",
		route: "/studio/sql-modeling",
		requiredParam: "modelId",
		missingReason: "缺少模型证据：请从标准草稿生成模型候选。",
	},
	{
		key: "metrics",
		title: "指标",
		description: "指标口径、维度、粒度和责任人。",
		route: "/modeling/metric-workbench",
		requiredParam: "metricId",
		missingReason: "缺少指标证据：请绑定指标口径和业务责任人。",
	},
	{
		key: "service",
		title: "服务",
		description: "API、报表或数据产品发布入口。",
		route: "/services/apis",
		requiredParam: "serviceId",
		missingReason: "缺少服务证据：请发布 API、报表或数据产品。",
	},
	{
		key: "quality",
		title: "质量",
		description: "质量规则、检查结果和修复记录。",
		route: "/governance/quality",
		requiredParam: "runId",
		missingReason: "缺少质量证据：请执行质量检查并记录结果。",
	},
	{
		key: "permission",
		title: "权限",
		description: "资产授权、访问审批和权限审计。",
		route: "/governance/asset-grants",
		requiredParam: "serviceId",
		missingReason: "缺少权限证据：请为服务或数据产品配置授权。",
	},
	{
		key: "operation",
		title: "运行",
		description: "任务实例、运行日志、补数和告警。",
		route: "/ops/instances",
		requiredParam: "runId",
		missingReason: "缺少运行证据：请完成一次可追溯运行。",
	},
	{
		key: "audit",
		title: "审计",
		description: "审计证据链和客户验收记录。",
		route: "/ops/audit-evidence",
		requiredParam: "auditId",
		blockerWhenMissing: true,
		missingReason: "API 缺口：需要 GET /api/data-product/acceptance-package 汇总审计证据。",
	},
];

const toParams = (input?: URLSearchParams | DataProductJourneyContextParams): DataProductJourneyContextParams => {
	if (!input) return {};
	if (!(input instanceof URLSearchParams)) return input;
	return Object.fromEntries(
		["sourceId", "standardDraftId", "modelId", "metricId", "serviceId", "runId", "auditId"].flatMap((key) => {
			const value = input.get(key);
			return value ? [[key, value]] : [];
		}),
	) as DataProductJourneyContextParams;
};

const resolveGroupStatus = (
	definition: AcceptanceEvidenceDefinition,
	params: DataProductJourneyContextParams,
): DataProductAcceptanceEvidenceStatus => {
	if (!definition.requiredParam || params[definition.requiredParam]) return "ready";
	return definition.blockerWhenMissing ? "blocked" : "missing";
};

const GATE_VERDICT_TO_STATUS: Record<GateEvidence["verdict"], DataProductAcceptanceEvidenceStatus> = {
	pass: "ready",
	warn: "missing",
	fail: "blocked",
};

export const buildDataProductAcceptancePackage = (
	input?: URLSearchParams | DataProductJourneyContextParams,
	options: GateEvidenceOptions = {},
): DataProductAcceptancePackage => {
	const params = toParams(input);
	const gateEvidence = buildGateEvidence(params, options);
	const groups = ACCEPTANCE_EVIDENCE_GROUPS.map<DataProductAcceptanceEvidenceGroup>((definition) => {
		const status = resolveGroupStatus(definition, params);
		return {
			key: definition.key,
			title: definition.title,
			description: definition.description,
			route: definition.route,
			url: buildJourneyUrl(definition.route, params),
			status,
			requiredParam: definition.requiredParam,
			paramValue: definition.requiredParam ? params[definition.requiredParam] : undefined,
			missingReason: status === "ready" ? undefined : definition.missingReason,
			recoveryLabel: status === "ready" ? "查看证据" : "补齐入口",
		};
	});
	const gateMissing = gateEvidence.checks.filter((check) => check.status !== "ready");
	const gateGroup: DataProductAcceptanceEvidenceGroup = {
		key: "gate",
		title: "发布门禁",
		description: "落标覆盖、模型编译、数据测试、运行结果四项门禁。",
		route: "/studio/sql-modeling",
		url: buildJourneyUrl("/studio/sql-modeling", params),
		status: GATE_VERDICT_TO_STATUS[gateEvidence.verdict],
		missingReason:
			gateMissing.length > 0
				? `门禁未齐：${gateMissing.map((check) => `${check.label}(${check.status})`).join("、")}`
				: undefined,
		recoveryLabel: gateEvidence.verdict === "pass" ? "查看证据" : "补齐入口",
	};
	const allGroups = [gateGroup, ...groups];
	const readyCount = allGroups.filter((group) => group.status === "ready").length;
	const missingCount = allGroups.filter((group) => group.status === "missing").length;
	const blockedCount = allGroups.filter((group) => group.status === "blocked").length;

	return {
		title: "客户验收包",
		journey: E2E_DATA_PRODUCT_JOURNEY,
		groups: allGroups,
		gateEvidence,
		readyCount,
		missingCount,
		blockedCount,
		summary: `客户验收包：${readyCount} 项已具备，${missingCount} 项缺失，${blockedCount} 项阻断；发布门禁 ${gateEvidence.verdict}。`,
	};
};

export const buildAcceptancePackageMarkdown = (acceptancePackage: DataProductAcceptancePackage) => {
	const lines = [`# ${acceptancePackage.title}`, "", acceptancePackage.summary, ""];
	for (const group of acceptancePackage.groups) {
		lines.push(`- ${group.title}: ${group.status}`);
		lines.push(`  - 证据: ${group.url}`);
		if (group.missingReason) lines.push(`  - 缺失项: ${group.missingReason}`);
	}
	lines.push("", "## 发布门禁明细", "");
	lines.push("| 门禁项 | 状态 | 说明 | 证据/待补接口 |");
	lines.push("| --- | --- | --- | --- |");
	for (const check of acceptancePackage.gateEvidence.checks) {
		const evidenceCell = check.status === "ready" ? (check.evidenceUrl ?? "") : (check.apiName ?? check.evidenceUrl ?? "");
		lines.push(`| ${check.label} | ${check.status} | ${check.detail} | ${evidenceCell} |`);
	}
	return lines.join("\n");
};

export const buildAcceptancePackageJson = (acceptancePackage: DataProductAcceptancePackage) =>
	JSON.stringify(acceptancePackage, null, 2);
