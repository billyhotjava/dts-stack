import { detectInteractionCycles } from "./interactionGraph";
import { buildScreenPayload, validateScreenPayload } from "./screenSpec";
import type { DataSourceConfig, ScreenComponent, ScreenConfig } from "./types";

export type ScreenAuthoringIssueLevel = "blocker" | "warning";
export type ScreenAuthoringIssueCategory = "canvas" | "data" | "variable" | "interaction";
export type ScreenAuthoringIssueTab = "style" | "data" | "interaction" | "advanced";

export interface ScreenAuthoringIssue {
	id: string;
	code: string;
	level: ScreenAuthoringIssueLevel;
	category: ScreenAuthoringIssueCategory;
	message: string;
	componentId?: string;
	componentName?: string;
	pageIndex?: number;
	pageName?: string;
	tab?: ScreenAuthoringIssueTab;
}

interface ComponentLocation {
	component: ScreenComponent;
	pageIndex?: number;
	pageName?: string;
}

function sourceType(dataSource?: DataSourceConfig): string {
	const raw = String(dataSource?.sourceType ?? dataSource?.type ?? "static")
		.trim()
		.toLowerCase();
	return raw === "database" ? "sql" : raw;
}

function componentLocations(config: ScreenConfig): ComponentLocation[] {
	if (config.pages?.length) {
		return config.pages.flatMap((page, pageIndex) =>
			page.components.map((component) => ({
				component,
				pageIndex,
				pageName: page.name,
			})),
		);
	}
	return (config.components ?? []).map((component) => ({ component }));
}

function issueForComponent(
	location: ComponentLocation,
	code: string,
	message: string,
	category: ScreenAuthoringIssueCategory,
	tab: ScreenAuthoringIssueTab,
): ScreenAuthoringIssue {
	const { component, pageIndex, pageName } = location;
	return {
		id: `${code}:${pageIndex ?? "root"}:${component.id}`,
		code,
		level: "blocker",
		category,
		message,
		componentId: component.id,
		componentName: component.name || component.id,
		pageIndex,
		pageName,
		tab,
	};
}

function dataSourceIssues(location: ComponentLocation): ScreenAuthoringIssue[] {
	const dataSource = location.component.dataSource;
	const type = sourceType(dataSource);
	if (!dataSource || type === "static") return [];

	const issues: ScreenAuthoringIssue[] = [];
	const add = (code: string, message: string) => {
		issues.push(issueForComponent(location, code, message, "data", "data"));
	};
	if (type === "card" && Number(dataSource.cardConfig?.cardId ?? 0) <= 0) {
		add("DATA_SOURCE_CARD_MISSING", "尚未选择可用的分析卡片");
	} else if (type === "metric" && Number(dataSource.metricConfig?.cardId ?? 0) <= 0) {
		add("DATA_SOURCE_METRIC_CARD_MISSING", "指标数据源尚未绑定查询卡片");
	} else if (type === "api") {
		if (!dataSource.apiConfig?.url?.trim()) {
			add("DATA_SOURCE_API_URL_MISSING", "接口数据源尚未填写访问地址");
		}
		if (!dataSource.classificationSubjectKey?.trim()) {
			add("DATA_SOURCE_IDENTITY_MISSING", "接口数据源尚未绑定治理资产标识");
		}
	} else if (type === "sql") {
		const sql = dataSource.sqlConfig ?? dataSource.databaseConfig;
		const databaseId = Number(sql?.databaseId ?? sql?.connectionId);
		if (!Number.isFinite(databaseId) || databaseId <= 0) {
			add("DATA_SOURCE_DATABASE_MISSING", "SQL 数据源尚未选择有效数据库；旧版导入内容请重新绑定当前环境数据库后保存");
		}
		if (!sql?.query?.trim()) {
			add("DATA_SOURCE_SQL_MISSING", "SQL 数据源尚未填写查询语句");
		}
	} else if (type === "dataset") {
		const queryBody = dataSource.datasetConfig?.queryBody;
		if (!queryBody || typeof queryBody !== "object" || Array.isArray(queryBody)) {
			add("DATA_SOURCE_DATASET_MISSING", "数据集查询配置不完整");
		}
	}
	return issues;
}

function fieldMappingIssues(location: ComponentLocation): ScreenAuthoringIssue[] {
	const raw = location.component.config._fieldMapping;
	if (raw === undefined) return [];
	if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
		return [issueForComponent(location, "FIELD_MAPPING_INVALID", "字段映射配置格式不正确", "data", "data")];
	}
	const mapping = raw as Record<string, unknown>;
	const stringFields = ["dimension", "groupBy", "sizeField", "sortField"];
	const invalidStringField = stringFields.some((key) => mapping[key] !== undefined && typeof mapping[key] !== "string");
	const invalidMeasures =
		mapping.measures !== undefined &&
		(!Array.isArray(mapping.measures) || mapping.measures.some((item) => typeof item !== "string"));
	if (invalidStringField || invalidMeasures) {
		return [issueForComponent(location, "FIELD_MAPPING_INVALID", "字段映射包含无效字段", "data", "data")];
	}
	return [];
}

function locateValidationIssue(config: ScreenConfig, message: string): ComponentLocation | undefined {
	const pageMatch = message.match(/^pages\[(\d+)]\.components\[(\d+)]/);
	if (pageMatch) {
		const pageIndex = Number(pageMatch[1]);
		const page = config.pages?.[pageIndex];
		const component = page?.components[Number(pageMatch[2])];
		return component ? { component, pageIndex, pageName: page?.name } : undefined;
	}
	const rootMatch = message.match(/^components\[(\d+)]/);
	const component = rootMatch ? config.components?.[Number(rootMatch[1])] : undefined;
	if (!component) return undefined;
	const pageIndex = config.pages?.findIndex((page) => page.components.some((item) => item.id === component.id));
	const resolvedPageIndex = pageIndex !== undefined && pageIndex >= 0 ? pageIndex : undefined;
	return {
		component,
		pageIndex: resolvedPageIndex,
		pageName: resolvedPageIndex === undefined ? undefined : config.pages?.[resolvedPageIndex]?.name,
	};
}

function validationCategory(message: string): { category: ScreenAuthoringIssueCategory; tab: ScreenAuthoringIssueTab } {
	if (message.includes("dataSource")) return { category: "data", tab: "data" };
	if (message.includes("interaction") || message.includes("drillDown") || message.includes("actions")) {
		return { category: "interaction", tab: "interaction" };
	}
	if (message.includes("globalVariables") || message.includes("variableKey")) {
		return { category: "variable", tab: "interaction" };
	}
	return { category: "canvas", tab: "style" };
}

function readableValidationMessage(message: string, location?: ComponentLocation): string {
	const prefix = location ? `${location.component.name || location.component.id}：` : "";
	if (message.includes("variableKey 不能为空")) return `${prefix}交互映射的目标变量不能为空`;
	if (message.includes("variableKey 重复")) return `${prefix}交互映射存在重复变量`;
	if (message.includes(".width 必须大于0")) return `${prefix}组件宽度必须大于 0`;
	if (message.includes(".height 必须大于0")) return `${prefix}组件高度必须大于 0`;
	if (message.includes("globalVariables"))
		return `全局变量配置不完整：${message.replace(/^globalVariables\[\d+]\.?/, "")}`;
	return `${prefix}${message.replace(/^pages\[\d+]\.components\[\d+]\.?/, "").replace(/^components\[\d+]\.?/, "")}`;
}

export function deriveScreenAuthoringIssues(config: ScreenConfig): ScreenAuthoringIssue[] {
	const issues: ScreenAuthoringIssue[] = [];
	if (!config.classification) {
		issues.push({
			id: "SCREEN_CLASSIFICATION_MISSING",
			code: "SCREEN_CLASSIFICATION_MISSING",
			level: "blocker",
			category: "canvas",
			message: "大屏尚未设置密级",
			tab: "style",
		});
	}

	for (const location of componentLocations(config)) {
		issues.push(...dataSourceIssues(location));
		issues.push(...fieldMappingIssues(location));
	}

	const validation = validateScreenPayload(buildScreenPayload(config));
	validation.errors.forEach((message, index) => {
		const location = locateValidationIssue(config, message);
		const target = validationCategory(message);
		issues.push({
			id: `SPEC_ERROR:${index}:${message}`,
			code: "SCREEN_SPEC_INVALID",
			level: "blocker",
			category: target.category,
			message: readableValidationMessage(message, location),
			componentId: location?.component.id,
			componentName: location?.component.name,
			pageIndex: location?.pageIndex,
			pageName: location?.pageName,
			tab: target.tab,
		});
	});
	validation.warnings.forEach((message, index) => {
		issues.push({
			id: `SPEC_WARNING:${index}:${message}`,
			code: "SCREEN_SPEC_WARNING",
			level: "warning",
			category: "canvas",
			message,
			tab: "style",
		});
	});

	const cycleConfigs = config.pages?.length
		? config.pages.map((page) => ({ ...config, components: page.components, pages: undefined }))
		: [config];
	cycleConfigs.forEach((pageConfig, pageIndex) => {
		detectInteractionCycles(pageConfig).forEach((message, cycleIndex) => {
			issues.push({
				id: `INTERACTION_CYCLE:${pageIndex}:${cycleIndex}`,
				code: "INTERACTION_CYCLE",
				level: "warning",
				category: "interaction",
				message: `存在循环联动：${message}`,
				pageIndex: config.pages?.length ? pageIndex : undefined,
				pageName: config.pages?.[pageIndex]?.name,
				tab: "interaction",
			});
		});
	});
	return issues;
}
