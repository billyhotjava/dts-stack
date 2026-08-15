import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type {
	IndicatorDefinition,
	IndicatorEditValues,
} from "@/features/modeling/indicators/indicatorDefinitionContract";
import {
	bindAtomicMetricModel,
	bindIndicatorDependencies,
	bindMetricImplementationModel,
	governedMetricModels,
	metricModelKey,
	selectedDependencyIds,
	selectedImplementationModelKey,
	selectedMetricModelKey,
} from "./services/indicatorDefinitionBindingService";

const AGGREGATIONS = ["SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MAX", "MIN"];

export function MetricDefinitionBindingFields({
	values,
	onChange,
	models,
	indicators,
}: {
	values: IndicatorEditValues;
	onChange: (values: IndicatorEditValues) => void;
	models: readonly ModelSpecView[];
	indicators: readonly IndicatorDefinition[];
}) {
	const metricType = String(values.metricType || "ATOMIC").toUpperCase();
	const availableModels = governedMetricModels(models);
	const selectedModelKey = selectedMetricModelKey(values);
	const selectedModel = availableModels.find((model) => metricModelKey(model) === selectedModelKey) || null;
	const measureFields = selectedModel?.fields.filter((field) => field.role === "MEASURE") || [];
	const dependencyOptions = indicators.filter(
		(indicator) =>
			indicator.id &&
			indicator.code &&
			indicator.version &&
			indicator.businessCategoryId &&
			String(indicator.status || "").toUpperCase() === "PUBLISHED" &&
			String(indicator.code).toUpperCase() !== String(values.code || "").toUpperCase(),
	);
	const selectedImplementationKey = selectedImplementationModelKey(values, availableModels);
	const selectedImplementation =
		availableModels.find((model) => metricModelKey(model) === selectedImplementationKey) || null;
	const implementationFields = selectedImplementation?.fields.filter((field) => field.role === "MEASURE") || [];

	if (metricType === "ATOMIC") {
		return (
			<>
				<BindingField label="来源模型" required>
					<select
						aria-label="来源模型"
						onChange={(event) =>
							onChange(
								bindAtomicMetricModel(
									values,
									availableModels.find((model) => metricModelKey(model) === event.target.value) || null,
								),
							)
						}
						value={selectedModelKey}
					>
						<option value="">请选择已发布事实表、汇总表或应用表</option>
						{availableModels.map((model) => (
							<option key={metricModelKey(model)} value={metricModelKey(model)}>
								{model.name}（{model.layer} · r{model.revision}）
							</option>
						))}
					</select>
					{!availableModels.length ? <small>暂无可绑定的已发布模型，请先完成模型发布。</small> : null}
				</BindingField>
				<BindingField label="度量字段" required>
					<select
						aria-label="度量字段"
						disabled={!selectedModel}
						onChange={(event) => onChange({ ...values, measureField: event.target.value || null })}
						value={String(values.measureField || "")}
					>
						<option value="">请选择 MEASURE 字段</option>
						{measureFields.map((field) => (
							<option key={field.name} value={field.name}>
								{field.displayName || field.name}（{field.name}）
							</option>
						))}
					</select>
				</BindingField>
				<BindingField label="聚合方式" required>
					<select
						aria-label="聚合方式"
						onChange={(event) => onChange({ ...values, aggregationType: event.target.value })}
						value={String(values.aggregationType || "")}
					>
						<option value="">请选择聚合方式</option>
						{AGGREGATIONS.map((aggregation) => (
							<option key={aggregation} value={aggregation}>
								{aggregation}
							</option>
						))}
					</select>
				</BindingField>
			</>
		);
	}

	return (
		<>
			<BindingField label="上游指标版本" required wide>
				<select
					aria-label="上游指标版本"
					multiple
					onChange={(event) =>
						onChange(
							bindIndicatorDependencies(
								values,
								Array.from(event.currentTarget.selectedOptions, (option) => option.value),
								indicators,
							),
						)
					}
					value={selectedDependencyIds(values)}
				>
					{dependencyOptions.map((indicator) => (
						<option key={indicator.id} value={indicator.id}>
							{indicator.name}（{indicator.code} · {indicator.version}）
						</option>
					))}
				</select>
				<small>
					{metricType === "COMPOSITE" ? "复合指标至少选择两个已发布指标版本。" : "派生指标至少选择一个已发布指标版本。"}
				</small>
			</BindingField>
			<BindingField label="受控计算公式" required wide>
				<textarea
					aria-label="受控计算公式"
					onChange={(event) => onChange({ ...values, expressionSql: event.target.value })}
					placeholder="例如 {{metric:BUDGET_EXECUTED}} / nullif({{metric:BUDGET_TOTAL}}, 0)"
					value={String(values.expressionSql || "")}
				/>
				<small>仅允许引用所选指标编码，不在此处录入原始 SQL。</small>
			</BindingField>
			<BindingField label="实现模型" required>
				<select
					aria-label="实现模型"
					onChange={(event) =>
						onChange(
							bindMetricImplementationModel(
								values,
								availableModels.find((model) => metricModelKey(model) === event.target.value) || null,
							),
						)
					}
					value={selectedImplementationKey}
				>
					<option value="">请选择已发布的指标实现模型</option>
					{availableModels.map((model) => (
						<option key={metricModelKey(model)} value={metricModelKey(model)}>
							{model.name}（{model.layer} · r{model.revision}）
						</option>
					))}
				</select>
			</BindingField>
			<BindingField label="结果字段" required>
				<select
					aria-label="结果字段"
					disabled={!selectedImplementation}
					onChange={(event) => onChange({ ...values, measureField: event.target.value || null })}
					value={String(values.measureField || "")}
				>
					<option value="">请选择 MEASURE 结果字段</option>
					{implementationFields.map((field) => (
						<option key={field.name} value={field.name}>
							{field.displayName || field.name}（{field.name}）
						</option>
					))}
				</select>
				<small>业务公式定义语义；实现模型字段用于上线后的真实计算与追溯。</small>
			</BindingField>
		</>
	);
}

function BindingField({
	label,
	required = false,
	wide = false,
	children,
}: {
	label: string;
	required?: boolean;
	wide?: boolean;
	children: React.ReactNode;
}) {
	return (
		<div className={`dmx-metric-field${wide ? " wide" : ""}`}>
			<span className={required ? "required" : ""}>{label}：</span>
			<div>{children}</div>
		</div>
	);
}
