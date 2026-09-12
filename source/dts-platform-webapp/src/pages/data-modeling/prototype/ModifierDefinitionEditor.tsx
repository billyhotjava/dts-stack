import type { IndicatorEditValues } from "@/features/modeling/indicators/indicatorDefinitionContract";
import { IndicatorAnalysisFields } from "./IndicatorAnalysisFields";
import type { PlanningCatalogDomain } from "./services/planningCatalogDomainService";

export function ModifierDefinitionEditor({
	values,
	onChange,
	codeLocked,
	businessCategories,
	dataDomains,
}: {
	values: IndicatorEditValues;
	onChange: (values: IndicatorEditValues) => void;
	codeLocked: boolean;
	businessCategories: PlanningCatalogDomain[];
	dataDomains: PlanningCatalogDomain[];
}) {
	const set = (key: keyof IndicatorEditValues, value: unknown) => onChange({ ...values, [key]: value });
	const selectedBusinessCategoryId = String(values.businessCategoryId || "");
	const selectedDomainId = String(values.dataDomainId || "");
	const availableDomains = dataDomains.filter((item) => item.parentId === selectedBusinessCategoryId);
	return (
		<div className="dmx-metric-scroll">
			<section className="dmx-metric-section">
				<h3>{values.category === "TIME_PERIOD" ? "时间周期" : "修饰词"}基本信息</h3>
				<p className="dmx-capability-note">
					修饰词用于限定指标统计范围，例如“境内”或“已验收”；时间周期使用明确起止区间。它维护可复用的业务含义和适用范围，不关联模型度量字段，也不独立提交计算。
				</p>
				<div>
					<ModifierField label="英文缩写" required>
						<input
							disabled={codeLocked}
							onChange={(event) => set("code", event.target.value)}
							placeholder="例如 CUMULATIVE"
							value={String(values.code || "")}
						/>
					</ModifierField>
					<ModifierField label="中文名称" required>
						<input onChange={(event) => set("name", event.target.value)} value={String(values.name || "")} />
					</ModifierField>
					<ModifierField label="对象类型">
						<input disabled value={values.category === "TIME_PERIOD" ? "时间周期" : "修饰词"} />
					</ModifierField>
					<ModifierField label="业务分类">
						<select
							aria-label="业务分类"
							onChange={(event) => {
								const id = event.target.value;
								const currentDomain = dataDomains.find((item) => item.id === selectedDomainId && item.parentId === id);
								onChange({
									...values,
									category: values.category || "MODIFIER",
									businessCategoryId: id || null,
									dataDomainId: currentDomain?.id || null,
									domain: currentDomain?.code || null,
								});
							}}
							value={selectedBusinessCategoryId}
						>
							<option value="">全部业务分类</option>
							{businessCategories.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name}（{item.code}）
								</option>
							))}
						</select>
					</ModifierField>
					<ModifierField label="数据域">
						<select
							disabled={!selectedBusinessCategoryId}
							onChange={(event) => {
								const id = event.target.value;
								const dataDomain = dataDomains.find((item) => item.id === id);
								onChange({
									...values,
									category: values.category || "MODIFIER",
									dataDomainId: id || null,
									domain: dataDomain?.code || null,
								});
							}}
							value={selectedDomainId}
						>
							<option value="">全部数据域</option>
							{availableDomains.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name}（{item.code}）
								</option>
							))}
						</select>
					</ModifierField>
					<ModifierField label="负责人">
						<input onChange={(event) => set("owner", event.target.value)} value={String(values.owner || "")} />
					</ModifierField>
					<ModifierField label="责任部门">
						<input onChange={(event) => set("ownerDept", event.target.value)} value={String(values.ownerDept || "")} />
					</ModifierField>
					<ModifierField label="业务含义与限定范围" required wide>
						<textarea
							onChange={(event) => set("definition", event.target.value)}
							placeholder="说明该修饰词限定哪些业务事实、适用哪些指标，以及不包含哪些范围"
							value={String(values.definition || "")}
						/>
					</ModifierField>
				</div>
			</section>
			<IndicatorAnalysisFields values={values} onChange={onChange} />
		</div>
	);
}

function ModifierField({
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
