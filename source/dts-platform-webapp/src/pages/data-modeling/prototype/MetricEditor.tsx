import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type {
	IndicatorDefinition,
	IndicatorEditValues,
} from "@/features/modeling/indicators/indicatorDefinitionContract";
import { IndicatorAnalysisFields } from "./IndicatorAnalysisFields";
import { MetricDefinitionBindingFields } from "./MetricDefinitionBindingFields";
import { ModifierDefinitionEditor } from "./ModifierDefinitionEditor";
import type { PlanningCatalogDomain } from "./services/planningCatalogDomainService";
import { resolveBusinessProcessBinding } from "./services/planningContextPolicyService";

export function MetricEditor({
	values,
	onChange,
	codeLocked,
	businessCategories,
	dataDomains,
	processes,
	metricModels = [],
	indicators = [],
}: {
	values: IndicatorEditValues;
	onChange: (values: IndicatorEditValues) => void;
	codeLocked: boolean;
	businessCategories: PlanningCatalogDomain[];
	dataDomains: PlanningCatalogDomain[];
	processes: Sprint64BusinessProcess[];
	metricModels?: ModelSpecView[];
	indicators?: IndicatorDefinition[];
}) {
	const set = (key: keyof IndicatorEditValues, value: unknown) => onChange({ ...values, [key]: value });
	const isModifier = ["MODIFIER", "TIME_PERIOD"].includes(String(values.category || "").toUpperCase());
	if (isModifier) {
		return (
			<ModifierDefinitionEditor
				businessCategories={businessCategories}
				codeLocked={codeLocked}
				dataDomains={dataDomains}
				onChange={onChange}
				values={values}
			/>
		);
	}
	const metricType = String(values.metricType || "ATOMIC").toUpperCase();
	const selectedBusinessCategoryId = String(values.businessCategoryId || "");
	const selectedDomainId = String(values.dataDomainId || "");
	const availableDomains = dataDomains.filter((item) => item.parentId === selectedBusinessCategoryId);
	const processBinding = resolveBusinessProcessBinding(values.businessProcessId, processes);
	return (
		<div className="dmx-metric-scroll">
			<section className="dmx-metric-section">
				<h3>指标基本信息</h3>
				<div>
					<MetricField label="英文缩写" required>
						<input
							disabled={codeLocked}
							onChange={(event) => set("code", event.target.value)}
							value={String(values.code || "")}
						/>
					</MetricField>
					<MetricField label="中文名称" required>
						<input onChange={(event) => set("name", event.target.value)} value={String(values.name || "")} />
					</MetricField>
					<MetricField label="指标类型" required>
						<input
							disabled
							value={{ ATOMIC: "原子指标", DERIVED: "派生指标", COMPOSITE: "复合指标" }[metricType] || metricType}
						/>
					</MetricField>
					<MetricField label="业务分类" required>
						<select
							aria-label="业务分类"
							onChange={(event) => {
								const id = event.target.value;
								const category = businessCategories.find((item) => item.id === id);
								const currentDomain = dataDomains.find((item) => item.id === selectedDomainId && item.parentId === id);
								onChange({
									...values,
									businessCategoryId: id || null,
									category: category?.name || null,
									dataDomainId: currentDomain?.id || null,
									businessProcessId: currentDomain ? values.businessProcessId : null,
									domain: currentDomain?.code || null,
								});
							}}
							value={selectedBusinessCategoryId}
						>
							<option value="">请选择业务分类</option>
							{businessCategories.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name}（{item.code}）
								</option>
							))}
						</select>
					</MetricField>
					<MetricField label="数据域" required={metricType !== "COMPOSITE"}>
						<select
							disabled={!selectedBusinessCategoryId}
							onChange={(event) => {
								const id = event.target.value;
								const dataDomain = dataDomains.find((item) => item.id === id);
								const category = businessCategories.find((item) => item.id === dataDomain?.parentId);
								onChange({
									...values,
									businessCategoryId: category?.id || null,
									category: category?.name || null,
									dataDomainId: id || null,
									businessProcessId: null,
									domain: dataDomain?.code || null,
								});
							}}
							value={selectedDomainId}
						>
							<option value="">{metricType === "COMPOSITE" ? "跨域时留空" : "请选择数据域"}</option>
							{availableDomains.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name}（{item.code}）
								</option>
							))}
						</select>
					</MetricField>
					{metricType === "ATOMIC" ? (
						<MetricField label="业务过程" required>
							{processBinding.showSelector ? (
								<select
									aria-label="业务过程"
									onChange={(event) => set("businessProcessId", event.target.value || null)}
									value={String(values.businessProcessId || "")}
								>
									<option value="">请选择业务过程</option>
									{processBinding.processes.map((item) => (
										<option key={item.id} value={item.id}>
											{item.name}（{item.processId}）
										</option>
									))}
								</select>
							) : (
								<small>{processBinding.message}</small>
							)}
						</MetricField>
					) : null}
					<MetricField label="指标分组编码">
						<input
							onChange={(event) => set("metricGroupCode", event.target.value)}
							placeholder="例如 finance.budget"
							value={String(values.metricGroupCode || "")}
						/>
					</MetricField>
					<MetricField label="负责人">
						<input onChange={(event) => set("owner", event.target.value)} value={String(values.owner || "")} />
					</MetricField>
					<MetricField label="责任部门">
						<input onChange={(event) => set("ownerDept", event.target.value)} value={String(values.ownerDept || "")} />
					</MetricField>
					<MetricField label="业务口径" required wide>
						<textarea
							onChange={(event) => set("definition", event.target.value)}
							value={String(values.definition || "")}
						/>
					</MetricField>
					<MetricField label="兼容文本" wide>
						<small>
							旧业务分类：{String(values.category || "—")}；旧数据域：{String(values.domain || "—")}
							。兼容字段只展示，不再作为关系主键。
						</small>
					</MetricField>
				</div>
			</section>
			<section className="dmx-metric-section">
				<h3>业务计算语义</h3>
				<p className="dmx-capability-note">
					公式计算复用固定版本的上游指标；预计算模式读取已完成加工的结果字段。请明确计算方式和统计范围。
				</p>
				<div>
					<MetricDefinitionBindingFields
						indicators={indicators}
						models={metricModels}
						onChange={onChange}
						values={values}
					/>
					<MetricField label="数据单位">
						<input onChange={(event) => set("unit", event.target.value)} value={String(values.unit || "")} />
					</MetricField>
					<MetricField label="小数位数">
						<input
							min="0"
							onChange={(event) => set("precisionScale", Number(event.target.value))}
							type="number"
							value={Number(values.precisionScale || 0)}
						/>
					</MetricField>
				</div>
			</section>
			<IndicatorAnalysisFields values={values} onChange={onChange} indicators={indicators} />
		</div>
	);
}

function MetricField({
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
