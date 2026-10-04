/** biome-ignore-all lint/suspicious/noArrayIndexKey: controlled ordered rows have no local state and must retain focus while their key text is edited. */
import type {
	IndicatorAnalysisConfig,
	IndicatorDefinition,
	IndicatorEditValues,
	IndicatorPredicate,
} from "@/features/modeling/indicators/indicatorDefinitionContract";

export const emptyAnalysisConfig = (): IndicatorAnalysisConfig => ({
	dimensionBindings: {},
	resultGrain: [],
	allowedAggregations: ["SUM"],
	modifierRefs: [],
	predicates: [],
	missingGroupsAsZero: false,
	periodMode: "RANGE",
});

export function PredicateFields({
	values,
	fields,
	onChange,
}: {
	values: IndicatorPredicate[];
	fields: string[];
	onChange: (value: IndicatorPredicate[]) => void;
}) {
	return (
		<div className="dmx-analysis-predicates">
			{values.map((rule, index) => (
				<div key={index} className="dmx-metric-field">
					<select
						aria-label="限定维度"
						value={rule.fieldRef}
						onChange={(e) => onChange(values.map((r, i) => (i === index ? { ...r, fieldRef: e.target.value } : r)))}
					>
						<option value="">选择维度</option>
						{fields.map((field) => (
							<option key={field}>{field}</option>
						))}
					</select>
					<select
						aria-label="限定方式"
						value={rule.op}
						onChange={(e) =>
							onChange(
								values.map((r, i) => (i === index ? { ...r, op: e.target.value as IndicatorPredicate["op"] } : r)),
							)
						}
					>
						<option value="EQ">等于</option>
						<option value="IN">属于</option>
						<option value="BETWEEN">介于</option>
					</select>
					<input
						aria-label="限定值"
						placeholder="多个值用逗号分隔"
						value={Array.isArray(rule.value) ? rule.value.join(",") : String(rule.value ?? "")}
						onChange={(e) =>
							onChange(
								values.map((r, i) =>
									i === index
										? {
												...r,
												value: rule.op === "EQ" ? e.target.value : e.target.value.split(",").map((v) => v.trim()),
											}
										: r,
								),
							)
						}
					/>
					<select
						aria-label="值类型"
						value={typeof (Array.isArray(rule.value) ? rule.value[0] : rule.value)}
						onChange={(e) => {
							const convert = (v: unknown) =>
								e.target.value === "number"
									? Number(v)
									: e.target.value === "boolean"
										? String(v) === "true"
										: String(v);
							onChange(
								values.map((r, i) =>
									i === index ? { ...r, value: Array.isArray(r.value) ? r.value.map(convert) : convert(r.value) } : r,
								),
							);
						}}
					>
						<option value="string">文本</option>
						<option value="number">数值</option>
						<option value="boolean">布尔</option>
					</select>
					<button type="button" onClick={() => onChange(values.filter((_, i) => i !== index))}>
						删除条件
					</button>
				</div>
			))}
			<button
				type="button"
				disabled={values.length >= 32}
				onClick={() => onChange([...values, { fieldRef: fields[0] || "", op: "EQ", value: "" }])}
			>
				添加限定条件
			</button>
		</div>
	);
}

export function IndicatorAnalysisFields({
	values,
	onChange,
	indicators = [],
}: {
	values: IndicatorEditValues;
	onChange: (value: IndicatorEditValues) => void;
	indicators?: IndicatorDefinition[];
}) {
	const config = values.analysisConfig || emptyAnalysisConfig();
	const set = (patch: Partial<IndicatorAnalysisConfig>) =>
		onChange({ ...values, analysisConfig: { ...config, ...patch } });
	const entries = Object.entries(config.dimensionBindings);
	const keys = Object.keys(config.dimensionBindings);
	const qualifier = ["MODIFIER", "TIME_PERIOD"].includes(String(values.category));
	const time = config.timeBinding || {
		fieldRef: "business_time",
		fieldName: "",
		timezone: "Asia/Shanghai",
		grain: "native",
	};
	return (
		<section className="dmx-metric-section dmx-analysis-config" aria-label="指标分析配置">
			<h3>{qualifier ? "限定规则" : "分析维度与结果粒度"}</h3>
			<p>公共维度编码应在上游指标间保持相同业务含义；模型字段按固定版本配置。</p>
			{entries.map(([key, field], index) => (
				<div key={index} className="dmx-metric-field dmx-analysis-dimension">
					<input
						aria-label="公共维度编码"
						value={key}
						onChange={(e) => {
							if (e.target.value !== key && Object.hasOwn(config.dimensionBindings, e.target.value)) {
								e.target.setCustomValidity("公共维度编码不能重复");
								e.target.reportValidity();
								return;
							}
							e.target.setCustomValidity("");
							set({
								dimensionBindings: Object.fromEntries(
									entries.map((entry, i) => (i === index ? [e.target.value, field] : entry)),
								),
								resultGrain: config.resultGrain.map((value) => (value === key ? e.target.value : value)),
							});
						}}
					/>
					<input
						aria-label="模型维度字段"
						value={field}
						onChange={(e) => set({ dimensionBindings: { ...config.dimensionBindings, [key]: e.target.value } })}
					/>
					<label className="dmx-analysis-toggle">
						<input
							type="checkbox"
							checked={config.resultGrain.includes(key)}
							onChange={(e) =>
								set({
									resultGrain: e.target.checked
										? [...config.resultGrain, key]
										: config.resultGrain.filter((v) => v !== key),
								})
							}
						/>
						结果粒度
					</label>
					<button
						type="button"
						onClick={() =>
							set({
								dimensionBindings: Object.fromEntries(entries.filter((_, i) => i !== index)),
								resultGrain: config.resultGrain.filter((v) => v !== key),
							})
						}
					>
						删除维度
					</button>
				</div>
			))}
			<button
				type="button"
				disabled={entries.length >= 16}
				onClick={() =>
					set({ dimensionBindings: { ...config.dimensionBindings, [`dimension_${entries.length + 1}`]: "" } })
				}
			>
				添加维度
			</button>
			{!qualifier && (
				<>
					<label>
						允许聚合
						<select
							value={config.allowedAggregations[0] || values.aggregationType || "SUM"}
							onChange={(e) => set({ allowedAggregations: [e.target.value] })}
						>
							{["SUM", "COUNT", "COUNT_DISTINCT", "AVG", "MIN", "MAX"].map((v) => (
								<option key={v}>{v}</option>
							))}
						</select>
					</label>
					<label className="dmx-analysis-toggle">
						<input
							type="checkbox"
							checked={Boolean(config.timeBinding)}
							onChange={(e) => set({ timeBinding: e.target.checked ? time : null })}
						/>
						启用时间范围
					</label>
					{config.timeBinding && (
						<div className="dmx-metric-field dmx-analysis-time">
							<input
								aria-label="业务时间编码"
								value={time.fieldRef}
								onChange={(e) => set({ timeBinding: { ...time, fieldRef: e.target.value } })}
							/>
							<input
								aria-label="时间字段"
								value={time.fieldName}
								onChange={(e) => set({ timeBinding: { ...time, fieldName: e.target.value } })}
							/>
							<input
								aria-label="业务时区"
								value={time.timezone}
								onChange={(e) => set({ timeBinding: { ...time, timezone: e.target.value } })}
							/>
						</div>
					)}
					<label className="dmx-analysis-toggle">
						<input
							type="checkbox"
							checked={config.missingGroupsAsZero}
							onChange={(e) => set({ missingGroupsAsZero: e.target.checked })}
						/>
						公式允许将缺失分组视为零（默认返回空值）
					</label>
					<label>
						固定时间周期
						<select
							disabled={!config.timeBinding}
							value={config.periodRef ? `${config.periodRef.id}@${config.periodRef.version}` : ""}
							onChange={(e) => {
								const [id, version] = e.target.value.split("@");
								set({ periodRef: id ? { id, version } : null });
							}}
						>
							<option value="">不使用周期定义</option>
							{indicators
								.filter((item) => item.category === "TIME_PERIOD" && item.status === "PUBLISHED")
								.map((item) => (
									<option key={item.id} value={`${item.id}@${item.version}`}>
										{item.name} · {item.version}
									</option>
								))}
							{config.periodRef &&
								!indicators.some(
									(item) => item.id === config.periodRef?.id && item.version === config.periodRef?.version,
								) && (
									<option value={`${config.periodRef.id}@${config.periodRef.version}`}>
										已固定周期 {config.periodRef.version}
									</option>
								)}
						</select>
					</label>
					<label>
						固定修饰词
						<select
							multiple
							value={config.modifierRefs.map((ref) => `${ref.id}@${ref.version}`)}
							onChange={(e) =>
								set({
									modifierRefs: Array.from(e.target.selectedOptions, (option) => {
										const [id, version] = option.value.split("@");
										return { id, version };
									}),
								})
							}
						>
							{indicators
								.filter(
									(item) =>
										item.category === "MODIFIER" &&
										item.status === "PUBLISHED" &&
										(!item.dataDomainId || item.dataDomainId === values.dataDomainId) &&
										Boolean(item.analysisConfig?.predicates.length) &&
										item.analysisConfig?.predicates.every((rule) => keys.includes(rule.fieldRef)),
								)
								.map((item) => (
									<option key={item.id} value={`${item.id}@${item.version}`}>
										{item.name} · {item.version}
									</option>
								))}
							{config.modifierRefs
								.filter((ref) => !indicators.some((item) => item.id === ref.id && item.version === ref.version))
								.map((ref) => (
									<option key={ref.id} value={`${ref.id}@${ref.version}`}>
										已固定版本 {ref.version}（{ref.id}）
									</option>
								))}
						</select>
					</label>
				</>
			)}
			<PredicateFields fields={keys} values={config.predicates} onChange={(predicates) => set({ predicates })} />
			{values.category === "TIME_PERIOD" && (
				<label>
					周期方式
					<select value={config.periodMode || "RANGE"} onChange={() => set({ periodMode: "RANGE" })}>
						<option value="RANGE">明确时间区间</option>
					</select>
				</label>
			)}
		</section>
	);
}
