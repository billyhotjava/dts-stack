import { summaryMeasureFields } from "./services/summaryMeasureFields";
import type {
	ModelImplementationAggregation,
	ModelImplementationAggregationFunction,
	ModelImplementationCastType,
	ModelImplementationFilter,
	ModelImplementationFilterOperator,
	ModelImplementationFilterValueType,
	ModelImplementationJoin,
} from "@/features/modeling/contracts/modelImplementationContract";
import { useEffect, useRef } from "react";
import { Button } from "./PrototypePrimitives";
import type { ModelSpecDraft, ModelWorkbenchContext } from "./services/modelWorkbenchService";
import { implementationInputs } from "./services/modelWorkbenchService";
import { defaultNameMappings, uniqueSourceField } from "./services/visualFieldMapping";
import { ModelSourceFieldInput, useModelSourceFields } from "./useModelSourceFields";

type Props = {
	draft: ModelSpecDraft;
	context: ModelWorkbenchContext;
	validationMessage?: string;
	onChange: (draft: ModelSpecDraft) => void;
};

type InputAlias = {
	index: number;
	label: string;
	role: "基础来源" | "上游模型" | "维度引用";
};

const CAST_TYPES: Array<{ value: ModelImplementationCastType; label: string }> = [
	{ value: "string", label: "文本" },
	{ value: "integer", label: "整数" },
	{ value: "bigint", label: "长整数" },
	{ value: "decimal", label: "小数" },
	{ value: "date", label: "日期" },
	{ value: "timestamp", label: "时间戳" },
	{ value: "boolean", label: "布尔" },
];

const FILTER_OPERATORS: Array<{ value: ModelImplementationFilterOperator; label: string }> = [
	{ value: "EQ", label: "等于" },
	{ value: "NE", label: "不等于" },
	{ value: "GT", label: "大于" },
	{ value: "GTE", label: "大于等于" },
	{ value: "LT", label: "小于" },
	{ value: "LTE", label: "小于等于" },
	{ value: "IN", label: "包含任一" },
	{ value: "NOT_IN", label: "不包含" },
	{ value: "IS_NULL", label: "为空" },
	{ value: "IS_NOT_NULL", label: "不为空" },
	{ value: "BETWEEN", label: "介于" },
];

const FILTER_VALUE_TYPES: Array<{ value: ModelImplementationFilterValueType; label: string }> = [
	{ value: "STRING", label: "文本" },
	{ value: "NUMBER", label: "数字" },
	{ value: "BOOLEAN", label: "布尔" },
	{ value: "DATE", label: "日期" },
	{ value: "TIMESTAMP", label: "时间戳" },
];

const AGGREGATION_FUNCTIONS: Array<{ value: ModelImplementationAggregationFunction; label: string }> = [
	{ value: "SUM", label: "求和" },
	{ value: "COUNT", label: "计数" },
	{ value: "COUNT_DISTINCT", label: "去重计数" },
	{ value: "MIN", label: "最小值" },
	{ value: "MAX", label: "最大值" },
	{ value: "AVG", label: "平均值" },
];

const modelLabel = (context: ModelWorkbenchContext, modelSpecId: string): string =>
	context.models.find((model) => model.id === modelSpecId)?.name || modelSpecId;

export const visualTransformationInputAliases = (
	draft: ModelSpecDraft,
	context: ModelWorkbenchContext,
): InputAlias[] => {
	const inputs = implementationInputs(draft, { models: context.models })?.inputs || [];
	return inputs.flatMap<InputAlias>((input, index) => {
		if ("sourceBindingId" in input)
			return [
				{
					index,
					label: context.sources.find((source) => source.bindingId === input.sourceBindingId)?.displayName || "源表",
					role: "基础来源" as const,
				},
			];
		if ("modelSpecId" in input)
			return [
				{ index, label: `${modelLabel(context, input.modelSpecId)} · r${input.revision}`, role: "上游模型" as const },
			];
		return [];
	});
};

const filterValueText = (filter: ModelImplementationFilter): string => {
	if (filter.operator === "IS_NULL" || filter.operator === "IS_NOT_NULL") return "";
	return Array.isArray(filter.value) ? filter.value.join(", ") : String(filter.value ?? "");
};

const parseScalar = (valueType: ModelImplementationFilterValueType, value: string): string | number | boolean => {
	const normalized = value.trim();
	if (valueType === "NUMBER") {
		const number = Number(normalized);
		return normalized && Number.isFinite(number) ? number : value;
	}
	if (valueType === "BOOLEAN") {
		if (normalized.toLowerCase() === "true") return true;
		if (normalized.toLowerCase() === "false") return false;
	}
	return value;
};

const parseFilterValue = (
	valueType: ModelImplementationFilterValueType,
	operator: ModelImplementationFilterOperator,
	value: string,
): ModelImplementationFilter["value"] => {
	if (operator === "IS_NULL" || operator === "IS_NOT_NULL") return "";
	if (["IN", "NOT_IN", "BETWEEN"].includes(operator)) {
		return value.split(",").map((item) => parseScalar(valueType, item));
	}
	return parseScalar(valueType, value);
};

const defaultFilterValue = (valueType: ModelImplementationFilterValueType): ModelImplementationFilter["value"] => {
	if (valueType === "BOOLEAN") return true;
	if (valueType === "NUMBER") return 0;
	return "";
};

export function ModelVisualTransformationFields({ draft, context, validationMessage, onChange }: Props) {
	const aliases = visualTransformationInputAliases(draft, context);
	const directory = useModelSourceFields(draft, context);
	const sourceLabels = aliases.map((input) => input.label);
	const namedFields = draft.fields.filter((field) => field.name.trim());
	const patch = (next: Partial<ModelSpecDraft>) => onChange({ ...draft, ...next });
	const canEdit = Boolean(draft.implementationInputMode) && draft.implementationInputMode !== "GENERATED";
	const directoryResolved =
		!directory.loading &&
		directory.sources.length > 0 &&
		directory.sources.every((source) => source.schemaState === "RESOLVED");

	// Once the pinned field directory is known, unmapped target fields default to their same-name source field.
	const autoMappedKey = useRef("");
	useEffect(() => {
		if (!canEdit || !directoryResolved) return;
		const next = defaultNameMappings(
			draft.fields.map((field) => field.name.trim()).filter(Boolean),
			directory.sources,
			draft.fieldMappings,
		);
		if (!next) return;
		const key = JSON.stringify(next);
		if (autoMappedKey.current === key) return;
		autoMappedKey.current = key;
		onChange({ ...draft, fieldMappings: next });
	}, [canEdit, directoryResolved, directory.sources, draft, onChange]);

	const setMapping = (targetField: string, sourceField: string) => {
		const next = draft.fieldMappings.filter((mapping) => mapping.targetField !== targetField);
		if (sourceField.trim()) next.push({ sourceField, targetField });
		patch({ fieldMappings: next });
	};

	const setCast = (targetField: string, cast: ModelImplementationCastType | "") => {
		const next = { ...draft.casts };
		if (cast) next[targetField] = cast;
		else delete next[targetField];
		patch({ casts: next });
	};

	const setAggregation = (targetField: string, functionName: ModelImplementationAggregationFunction | "") => {
		const next = draft.aggregations.filter((aggregation) => aggregation.targetField !== targetField);
		if (functionName) {
			next.push({
				targetField,
				function: functionName,
				sourceField:
					draft.fieldMappings.find((mapping) => mapping.targetField === targetField)?.sourceField ||
					`${aliases[0] ? `src_${aliases[0].index}.` : ""}${targetField}`,
				distinct: false,
			});
		}
		patch({
			aggregations: next,
			fields: summaryMeasureFields({ ...draft, aggregations: next }),
			groupBy: functionName ? draft.groupBy.filter((field) => field !== targetField) : draft.groupBy,
		});
	};

	const updateAggregation = (targetField: string, next: Partial<ModelImplementationAggregation>) =>
		patch({
			aggregations: draft.aggregations.map((aggregation) =>
				aggregation.targetField === targetField ? { ...aggregation, ...next } : aggregation,
			),
		});

	const updateFilter = (index: number, next: Partial<ModelImplementationFilter>) =>
		patch({
			filters: draft.filters.map((filter, itemIndex) => (itemIndex === index ? { ...filter, ...next } : filter)),
		});

	const updateJoin = (inputIndex: number, next: Partial<ModelImplementationJoin>) => {
		const current = draft.joins.find((join) => join.inputIndex === inputIndex) || {
			inputIndex,
			type: "LEFT" as const,
			leftField: "",
			rightField: "",
		};
		patch({
			joins: [...draft.joins.filter((join) => join.inputIndex !== inputIndex), { ...current, ...next }].sort(
				(left, right) => left.inputIndex - right.inputIndex,
			),
		});
	};

	if (!canEdit) {
		return (
			<div className="dmx-visual-transform dmx-workbench-editor__wide-field">
				<strong>可视化转换</strong>
				<small>
					{draft.implementationInputMode === "GENERATED"
						? "受控生成器不接受字段映射、过滤、关联或聚合配置。"
						: "当前模型保留原始代码；如需结构化调整，请先选择数据来源方式，或切换到代码模式。"}
				</small>
			</div>
		);
	}

	return (
		<div className="dmx-visual-transform dmx-workbench-editor__wide-field">
			<div className="dmx-visual-transform__heading">
				<div>
					<strong>可视化转换</strong>
				</div>
				<Button
					disabled={!aliases.length || !namedFields.length}
					onClick={() =>
						patch({
							fieldMappings: namedFields.map((field) => ({
								targetField: field.name,
								sourceField:
									uniqueSourceField(field.name, directory.sources) ||
									draft.fieldMappings.find((mapping) => mapping.targetField === field.name)?.sourceField ||
									field.name,
							})),
						})
					}
				>
					按名称一一映射
				</Button>
			</div>

			<div className="dmx-visual-transform__aliases">
				{aliases.map((input) => (
					<span key={`${input.role}:${input.index}`}>
						<code>src_{input.index}</code> · {input.role} · {input.label}
					</span>
				))}
				{!aliases.length ? <small>请先选择基础来源或上游模型。</small> : null}
			</div>

			{directory.loading && <output>正在读取固定版本的来源字段…</output>}
			{directory.error && (
				<div role="alert">
					{directory.error}
					<button type="button" onClick={directory.refresh}>
						重试读取字段
					</button>
				</div>
			)}
			<div className="dmx-visual-transform__table-wrap">
				<table className="dmx-visual-transform__table">
					<thead>
						<tr>
							<th>目标字段</th>
							<th>来源字段</th>
							<th>类型转换</th>
							<th>去重键</th>
							<th>分组字段</th>
							<th>聚合</th>
							<th>聚合来源</th>
						</tr>
					</thead>
					<tbody>
						{namedFields.map((field) => {
							const mapping = draft.fieldMappings.find((item) => item.targetField === field.name);
							const aggregation = draft.aggregations.find((item) => item.targetField === field.name);
							return (
								<tr key={field.name}>
									<td>
										<strong>{field.name}</strong>
										<small>{field.dataType}</small>
									</td>
									<td>
										<ModelSourceFieldInput
											label={`来源字段 ${field.name}`}
											value={mapping?.sourceField || ""}
											onChange={(value) => setMapping(field.name, value)}
											sources={directory.sources}
											labels={sourceLabels}
											selectWhenResolved
										/>
									</td>
									<td>
										<select
											aria-label={`类型转换 ${field.name}`}
											onChange={(event) => setCast(field.name, event.target.value as ModelImplementationCastType | "")}
											value={draft.casts[field.name] || ""}
										>
											<option value="">不转换</option>
											{CAST_TYPES.map((type) => (
												<option key={type.value} value={type.value}>
													{type.label}
												</option>
											))}
										</select>
									</td>
									<td>
										<input
											aria-label={`去重键 ${field.name}`}
											checked={draft.deduplicateBy.includes(field.name)}
											disabled={Boolean(draft.aggregations.length)}
											onChange={(event) =>
												patch({
													deduplicateBy: event.target.checked
														? [...draft.deduplicateBy, field.name]
														: draft.deduplicateBy.filter((item) => item !== field.name),
												})
											}
											type="checkbox"
										/>
									</td>
									<td>
										<input
											aria-label={`分组字段 ${field.name}`}
											checked={draft.groupBy.includes(field.name)}
											onChange={(event) =>
												patch({
													groupBy: event.target.checked
														? [...draft.groupBy, field.name]
														: draft.groupBy.filter((item) => item !== field.name),
													aggregations: event.target.checked
														? draft.aggregations.filter((item) => item.targetField !== field.name)
														: draft.aggregations,
												})
											}
											type="checkbox"
										/>
									</td>
									<td>
										<select
											aria-label={`聚合函数 ${field.name}`}
											onChange={(event) =>
												setAggregation(field.name, event.target.value as ModelImplementationAggregationFunction | "")
											}
											value={aggregation?.function || ""}
										>
											<option value="">不聚合</option>
											{AGGREGATION_FUNCTIONS.map((item) => (
												<option key={item.value} value={item.value}>
													{item.label}
												</option>
											))}
										</select>
										{aggregation?.function === "COUNT" ? (
											<label className="dmx-visual-transform__distinct">
												<input
													aria-label={`聚合去重 ${field.name}`}
													checked={aggregation.distinct}
													onChange={(event) => updateAggregation(field.name, { distinct: event.target.checked })}
													type="checkbox"
												/>
												去重
											</label>
										) : null}
									</td>
									<td>
										<ModelSourceFieldInput
											label={`聚合来源 ${field.name}`}
											value={aggregation?.sourceField || ""}
											onChange={(value) => updateAggregation(field.name, { sourceField: value })}
											sources={directory.sources}
											labels={sourceLabels}
											disabled={!aggregation}
										/>
									</td>
								</tr>
							);
						})}
					</tbody>
				</table>
			</div>

			{aliases.length > 1 ? (
				<div className="dmx-visual-transform__block">
					<strong>关联关系</strong>
					<small>每个附加输入必须连接到一个更早的输入；仅开放 INNER 和 LEFT。</small>
					{aliases.slice(1).map((input) => {
						const join = draft.joins.find((item) => item.inputIndex === input.index);
						return (
							<div className="dmx-visual-transform__join-row" key={input.index}>
								<code>src_{input.index}</code>
								<select
									aria-label={`关联类型 src_${input.index}`}
									onChange={(event) => updateJoin(input.index, { type: event.target.value as "INNER" | "LEFT" })}
									value={join?.type || "LEFT"}
								>
									<option value="LEFT">LEFT</option>
									<option value="INNER">INNER</option>
								</select>
								<ModelSourceFieldInput
									label={`左关联字段 src_${input.index}`}
									value={join?.leftField || ""}
									onChange={(value) => updateJoin(input.index, { leftField: value })}
									sources={directory.sources}
									labels={sourceLabels}
								/>
								<span>=</span>
								<ModelSourceFieldInput
									label={`右关联字段 src_${input.index}`}
									value={join?.rightField || ""}
									onChange={(value) => updateJoin(input.index, { rightField: value })}
									sources={directory.sources}
									labels={sourceLabels}
								/>
							</div>
						);
					})}
				</div>
			) : null}

			<div className="dmx-visual-transform__block">
				<div className="dmx-visual-transform__heading">
					<div>
						<strong>过滤条件</strong>
					</div>
					<Button
						disabled={!draft.fields.length}
						onClick={() =>
							patch({
								filters: [
									...draft.filters,
									{ field: draft.fields[0]?.name || "", operator: "EQ", valueType: "STRING", value: "" },
								],
							})
						}
					>
						添加条件
					</Button>
				</div>
				{draft.filters.map((filter, index) => {
					const valueDisabled = filter.operator === "IS_NULL" || filter.operator === "IS_NOT_NULL";
					return (
						<div className="dmx-visual-transform__filter-row" key={`${index}:${filter.field}`}>
							<input
								aria-label={`过滤字段 ${index + 1}`}
								onChange={(event) => updateFilter(index, { field: event.target.value })}
								placeholder="目标字段或 src_0.source_field"
								value={filter.field}
							/>
							<select
								aria-label={`过滤操作符 ${index + 1}`}
								onChange={(event) => {
									const operator = event.target.value as ModelImplementationFilterOperator;
									updateFilter(index, {
										operator,
										value: parseFilterValue(filter.valueType, operator, filterValueText(filter)),
									});
								}}
								value={filter.operator}
							>
								{FILTER_OPERATORS.map((operator) => (
									<option key={operator.value} value={operator.value}>
										{operator.label}
									</option>
								))}
							</select>
							<select
								aria-label={`过滤值类型 ${index + 1}`}
								onChange={(event) => {
									const valueType = event.target.value as ModelImplementationFilterValueType;
									updateFilter(index, { valueType, value: defaultFilterValue(valueType) });
								}}
								value={filter.valueType}
							>
								{FILTER_VALUE_TYPES.map((valueType) => (
									<option key={valueType.value} value={valueType.value}>
										{valueType.label}
									</option>
								))}
							</select>
							<input
								aria-label={`过滤值 ${index + 1}`}
								disabled={valueDisabled}
								onChange={(event) =>
									updateFilter(index, {
										value: parseFilterValue(filter.valueType, filter.operator, event.target.value),
									})
								}
								placeholder={["IN", "NOT_IN", "BETWEEN"].includes(filter.operator) ? "以逗号分隔" : "过滤值"}
								value={filterValueText(filter)}
							/>
							<Button
								danger
								onClick={() => patch({ filters: draft.filters.filter((_, itemIndex) => itemIndex !== index) })}
							>
								删除
							</Button>
						</div>
					);
				})}
				{!draft.filters.length ? <small>未配置过滤条件。</small> : null}
			</div>

			{validationMessage ? (
				<small className="dmx-workbench-editor__validation" role="alert">
					{validationMessage}
				</small>
			) : null}
		</div>
	);
}
