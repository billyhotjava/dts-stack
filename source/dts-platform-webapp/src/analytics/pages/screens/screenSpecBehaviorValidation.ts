const DRILL_TARGET_DATA_SOURCE_TYPES = new Set(["api", "card", "sql", "dataset", "metric", "database"]);
const INTERACTION_TRANSFORMS = new Set(["raw", "string", "number", "lowercase", "uppercase"]);
const COMPONENT_ACTION_TYPES = new Set([
	"set-variable",
	"drill-down",
	"drill-up",
	"drill-view",
	"jump-url",
	"open-panel",
	"emit-intent",
]);
const JUMP_OPEN_MODES = new Set(["self", "new-tab"]);

function asTrimmedString(value: unknown): string | undefined {
	if (typeof value !== "string") return undefined;
	const output = value.trim();
	return output.length > 0 ? output : undefined;
}

function isPositiveNumber(value: unknown): boolean {
	const numberValue = Number(value);
	return Number.isFinite(numberValue) && numberValue > 0;
}

function validateInteractionMappings(input: unknown, path: string, errors: string[]) {
	if (!Array.isArray(input)) {
		errors.push(`${path} 必须是数组`);
		return;
	}
	const seenVariableKeys = new Set<string>();
	input.forEach((mapping, mappingIndex) => {
		const mappingPath = `${path}[${mappingIndex}]`;
		if (!mapping || typeof mapping !== "object") {
			errors.push(`${mappingPath} 必须是对象`);
			return;
		}
		const mappingRow = mapping as Record<string, unknown>;
		const variableKey = asTrimmedString(mappingRow.variableKey);
		const sourcePath = asTrimmedString(mappingRow.sourcePath);
		if (!variableKey) {
			errors.push(`${mappingPath}.variableKey 不能为空`);
		} else if (seenVariableKeys.has(variableKey)) {
			errors.push(`${mappingPath}.variableKey 重复: ${variableKey}`);
		} else {
			seenVariableKeys.add(variableKey);
		}
		if (!sourcePath) {
			errors.push(`${mappingPath}.sourcePath 不能为空`);
		}
		const transform = String(mappingRow.transform ?? "raw")
			.trim()
			.toLowerCase();
		if (!INTERACTION_TRANSFORMS.has(transform)) {
			errors.push(`${mappingPath}.transform 非法: ${transform}`);
		}
	});
}

export function validateComponentBehavior(component: Record<string, unknown>, path: string, errors: string[]) {
	const interaction = component.interaction;
	if (interaction !== undefined && interaction !== null) {
		if (typeof interaction !== "object") {
			errors.push(`${path}.interaction 必须是对象`);
		} else {
			const mappings = (interaction as Record<string, unknown>).mappings;
			if (mappings !== undefined && mappings !== null) {
				validateInteractionMappings(mappings, `${path}.interaction.mappings`, errors);
			}
		}
	}

	const drillDown = component.drillDown;
	let hasExecutableDrillChain = false;
	if (drillDown !== undefined && drillDown !== null) {
		if (typeof drillDown !== "object" || Array.isArray(drillDown)) {
			errors.push(`${path}.drillDown 必须是对象`);
		} else {
			hasExecutableDrillChain = validateDrillDown(drillDown as Record<string, unknown>, path, errors);
		}
	}

	validateActions(component.actions, path, errors, hasExecutableDrillChain);
}

export function validateDrillTargetDataSource(input: unknown, path: string, errors: string[]): boolean {
	const errorCount = errors.length;
	if (!input || typeof input !== "object" || Array.isArray(input)) {
		errors.push(`${path} 必须是对象`);
		return false;
	}
	const dataSource = input as Record<string, unknown>;
	const sourceType = String(dataSource.sourceType ?? dataSource.type ?? "")
		.trim()
		.toLowerCase();
	if (!DRILL_TARGET_DATA_SOURCE_TYPES.has(sourceType)) {
		errors.push(`${path}.sourceType 非法: ${sourceType}`);
		return false;
	}

	if (sourceType === "sql" || sourceType === "database") {
		const config =
			dataSource.sqlConfig && typeof dataSource.sqlConfig === "object"
				? (dataSource.sqlConfig as Record<string, unknown>)
				: dataSource.databaseConfig && typeof dataSource.databaseConfig === "object"
					? (dataSource.databaseConfig as Record<string, unknown>)
					: undefined;
		if (!config) {
			errors.push(`${path} 必须配置 sqlConfig/databaseConfig`);
		} else {
			const databaseId = Number(config.databaseId ?? config.connectionId);
			if (!Number.isFinite(databaseId) || databaseId <= 0) errors.push(`${path}.databaseId 必须为正整数`);
			if (!asTrimmedString(config.query)) errors.push(`${path}.query 不能为空`);
		}
	}
	if (sourceType === "card") {
		const config = dataSource.cardConfig as Record<string, unknown> | undefined;
		if (!config || !isPositiveNumber(config.cardId)) errors.push(`${path}.cardConfig.cardId 必须为正整数`);
	}
	if (sourceType === "metric") {
		const config = dataSource.metricConfig as Record<string, unknown> | undefined;
		if (!config || !isPositiveNumber(config.cardId)) errors.push(`${path}.metricConfig.cardId 必须为正整数`);
	}
	if (sourceType === "api") {
		const config = dataSource.apiConfig as Record<string, unknown> | undefined;
		if (!config || !asTrimmedString(config.url)) errors.push(`${path}.apiConfig.url 不能为空`);
	}
	if (sourceType === "dataset") {
		const config = dataSource.datasetConfig as Record<string, unknown> | undefined;
		const queryBody = config?.queryBody as Record<string, unknown> | undefined;
		if (!queryBody || typeof queryBody !== "object" || Array.isArray(queryBody)) {
			errors.push(`${path}.datasetConfig.queryBody 必须是对象`);
		} else {
			if (!isPositiveNumber(queryBody.database)) errors.push(`${path}.datasetConfig.queryBody.database 必须为正整数`);
			const queryType = String(queryBody.type ?? "query")
				.trim()
				.toLowerCase();
			if (queryType === "native") {
				const nativeQuery = queryBody.native as Record<string, unknown> | undefined;
				if (!nativeQuery || !asTrimmedString(nativeQuery.query)) {
					errors.push(`${path}.datasetConfig.queryBody.native.query 不能为空`);
				}
			} else if (!queryBody.query || typeof queryBody.query !== "object" || Array.isArray(queryBody.query)) {
				errors.push(`${path}.datasetConfig.queryBody.query 必须是对象`);
			}
		}
	}
	return errors.length === errorCount;
}

function validateDrillDown(drillDown: Record<string, unknown>, path: string, errors: string[]): boolean {
	if (drillDown.enabled !== undefined && typeof drillDown.enabled !== "boolean") {
		errors.push(`${path}.drillDown.enabled 必须是布尔值`);
	}
	const levels = drillDown.levels;
	if (!Array.isArray(levels)) {
		errors.push(`${path}.drillDown.levels 必须是数组`);
		return false;
	}
	let hasExecutableLevel = false;
	levels.forEach((level, levelIndex) => {
		const levelErrorCount = errors.length;
		const levelPath = `${path}.drillDown.levels[${levelIndex}]`;
		if (!level || typeof level !== "object" || Array.isArray(level)) {
			errors.push(`${levelPath} 必须是对象`);
			return;
		}
		const levelRow = level as Record<string, unknown>;
		if (!asTrimmedString(levelRow.label)) {
			errors.push(`${levelPath}.label 不能为空`);
		}
		if (levelRow.inheritContext !== undefined && typeof levelRow.inheritContext !== "boolean") {
			errors.push(`${levelPath}.inheritContext 必须是布尔值`);
		}

		const cardId = Number(levelRow.cardId);
		const isLegacy = Number.isFinite(cardId) && cardId > 0 && Boolean(asTrimmedString(levelRow.paramName));
		const levelDataSource = levelRow.dataSource;
		const levelDataSourceRow =
			levelDataSource && typeof levelDataSource === "object" && !Array.isArray(levelDataSource)
				? (levelDataSource as Record<string, unknown>)
				: undefined;
		const sourceType = String(levelDataSourceRow?.sourceType ?? levelDataSourceRow?.type ?? "")
			.trim()
			.toLowerCase();
		const hasGenericTarget = DRILL_TARGET_DATA_SOURCE_TYPES.has(sourceType);
		const hasGenericMappings = Array.isArray(levelRow.mappings) && levelRow.mappings.length > 0;
		if (!isLegacy && !(hasGenericTarget && hasGenericMappings)) {
			errors.push(`${levelPath} 必须配置下一层数据源和字段映射，或提供旧版 cardId + paramName`);
		}
		if (levelDataSourceRow) {
			validateDrillTargetDataSource(levelDataSourceRow, `${levelPath}.dataSource`, errors);
		}
		if (levelRow.mappings !== undefined && levelRow.mappings !== null) {
			validateInteractionMappings(levelRow.mappings, `${levelPath}.mappings`, errors);
		}
		if ((isLegacy || (hasGenericTarget && hasGenericMappings)) && errors.length === levelErrorCount) {
			hasExecutableLevel = true;
		}
	});
	return drillDown.enabled === true && hasExecutableLevel;
}

function validateActions(input: unknown, path: string, errors: string[], hasExecutableDrillChain: boolean) {
	if (input === undefined || input === null) return;
	if (!Array.isArray(input)) {
		errors.push(`${path}.actions 必须是数组`);
		return;
	}
	input.forEach((action, actionIndex) => {
		const actionPath = `${path}.actions[${actionIndex}]`;
		if (!action || typeof action !== "object") {
			errors.push(`${actionPath} 必须是对象`);
			return;
		}
		const actionRow = action as Record<string, unknown>;
		const actionType = asTrimmedString(actionRow.type);
		if (!actionType || !COMPONENT_ACTION_TYPES.has(actionType)) {
			errors.push(`${actionPath}.type 非法: ${String(actionRow.type ?? "")}`);
		}
		if ((actionType === "drill-down" || actionType === "drill-up") && !hasExecutableDrillChain) {
			errors.push(`${actionPath} 必须关联启用有效下钻链路`);
		}
		const mappings = actionRow.mappings;
		if (mappings !== undefined && mappings !== null) {
			validateInteractionMappings(mappings, `${actionPath}.mappings`, errors);
		}
		if (actionType === "jump-url") {
			if (!asTrimmedString(actionRow.jumpUrlTemplate)) {
				errors.push(`${actionPath}.jumpUrlTemplate 不能为空`);
			}
			const openMode = String(actionRow.jumpOpenMode ?? "new-tab")
				.trim()
				.toLowerCase();
			if (!JUMP_OPEN_MODES.has(openMode)) {
				errors.push(`${actionPath}.jumpOpenMode 非法: ${openMode}`);
			}
		}
		if (actionType === "open-panel" && !asTrimmedString(actionRow.panelTitle)) {
			errors.push(`${actionPath}.panelTitle 不能为空`);
		}
		if (actionType === "drill-view" && !asTrimmedString(actionRow.drillViewId)) {
			errors.push(`${actionPath}.drillViewId 不能为空`);
		}
		if (actionType === "emit-intent" && !asTrimmedString(actionRow.intentName)) {
			errors.push(`${actionPath}.intentName 不能为空`);
		}
	});
}
