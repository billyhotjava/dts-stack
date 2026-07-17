import {
	type BusinessObject,
	type LegacyModelRef,
	MODELING_CONTRACT_VERSION,
	type ModelingContractFixture,
	type ModelSpec,
} from "./modelingVnextContract.ts";

export type PjmGoldenPathFixture = {
	contractVersion: typeof MODELING_CONTRACT_VERSION;
	businessObject: BusinessObject;
	modelSpecs: ModelSpec[];
	legacyRefs: LegacyModelRef[];
};

export const buildPjmProjectNodeFixture = (): ModelingContractFixture => {
	const businessObject: BusinessObject = {
		id: "pjm-project-node",
		code: "project_node",
		name: "项目节点",
		description: "项目节点计划闭环中的明细事实对象。",
		objectKind: "FACT",
		processId: "project-node-plan-loop",
		businessKey: ["project_no", "subsystem", "node_task", "plan_date"],
		grain: {
			statement: "一行代表一个项目在一个计划日期上的节点任务",
			keys: ["project_no", "subsystem", "node_task", "plan_date"],
		},
		sourceRefs: [{ kind: "TABLE", ref: "ods_project_subject_domain_v2", layer: "ODS" }],
		status: "DRAFT",
		implementationMode: "DESIGNER_GENERATED",
	};

	const modelSpec: ModelSpec = {
		id: "pjm-project-node-dwd",
		objectId: businessObject.id,
		processId: businessObject.processId,
		layer: "DWD",
		modelType: "FACT",
		implementationMode: "DESIGNER_GENERATED",
		name: "project_node_detail",
		grain: businessObject.grain,
		standardBindings: [
			{ fieldName: "project_no", standardElementId: "std.project.code" },
			{ fieldName: "plan_date", standardElementId: "std.date" },
			{ fieldName: "node_type", standardElementId: "std.node.type", referenceCode: "NODE_TYPE" },
			{
				fieldName: "completion_status",
				standardElementId: "std.completion.status",
				referenceCode: "COMPLETION_STATUS",
			},
			{ fieldName: "risk_level", standardElementId: "std.risk.level", referenceCode: "RISK_LEVEL" },
			{ fieldName: "delay_days", standardElementId: "std.delay.days" },
		],
		sourceRefs: [{ kind: "TABLE", ref: "ods_project_subject_domain_v2", layer: "ODS" }],
		materialization: "table",
		revision: 1,
	};

	return { contractVersion: MODELING_CONTRACT_VERSION, businessObject, modelSpec };
};

export const buildPjmGoldenPathFixture = (): PjmGoldenPathFixture => {
	const base = buildPjmProjectNodeFixture();
	const dwd: ModelSpec = {
		...base.modelSpec,
		dependsOn: [],
		legacyRef: "model.pm_analytics_v3.biz_dwd_project_node_v2",
	};
	const dws: ModelSpec = {
		id: "pjm-project-node-dws",
		objectId: base.businessObject.id,
		processId: base.businessObject.processId,
		layer: "DWS",
		modelType: "SUMMARY",
		implementationMode: "DESIGNER_GENERATED",
		name: "project_progress_monthly",
		grain: { statement: "一行代表一个项目在一个月份的进度汇总", keys: ["project_no", "plan_month"] },
		standardBindings: [],
		sourceRefs: [{ kind: "DBT_MODEL", ref: "project_node_detail", layer: "DWD" }],
		dimensions: ["project_no", "plan_month"],
		metrics: ["due_node_count", "completed_node_count", "delay_node_count"],
		materialization: "table",
		revision: 1,
		dependsOn: [dwd.id],
		legacyRef: "model.pm_analytics_v3.biz_dws_progress_monthly_v2",
	};
	const ads: ModelSpec = {
		id: "pjm-project-node-ads",
		objectId: base.businessObject.id,
		processId: base.businessObject.processId,
		layer: "ADS",
		modelType: "APPLICATION",
		implementationMode: "DESIGNER_GENERATED",
		name: "project_progress_kpi",
		grain: { statement: "一行代表一个项目在一个月份的经营进度 KPI", keys: ["project_no", "plan_month"] },
		standardBindings: [],
		sourceRefs: [{ kind: "DBT_MODEL", ref: "project_progress_monthly", layer: "DWS" }],
		dimensions: ["project_no", "plan_month"],
		metrics: ["on_time_rate", "delay_rate", "progress_health_score"],
		materialization: "table",
		revision: 1,
		dependsOn: [dws.id],
		legacyRef: "model.pm_analytics_v3.biz_ads_progress_kpi_v2",
	};

	return {
		contractVersion: MODELING_CONTRACT_VERSION,
		businessObject: base.businessObject,
		modelSpecs: [dwd, dws, ads],
		legacyRefs: [
			{
				modelId: dwd.id,
				dbtUniqueId: dwd.legacyRef || "",
				path: "models/dwd/biz_dwd_project_node_v2.sql",
				status: "LEGACY_READONLY",
			},
			{
				modelId: dws.id,
				dbtUniqueId: dws.legacyRef || "",
				path: "models/dws/biz_dws_progress_monthly_v2.sql",
				status: "LEGACY_READONLY",
			},
			{
				modelId: ads.id,
				dbtUniqueId: ads.legacyRef || "",
				path: "models/ads/biz_ads_progress_kpi_v2.sql",
				status: "LEGACY_READONLY",
			},
		],
	};
};
