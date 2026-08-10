import { Archive, CheckCircle2, Plus, RefreshCw, Save, Search, Send, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { listBusinessProcessesApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import type { WarehousePlanBusinessProcessMode } from "@/api/warehousePlanApi";
import {
	type IndicatorMetricSourceRef,
	type IndicatorSourceType,
	parseIndicatorDependencyCodes,
} from "@/features/modeling/indicators/indicatorDefinitionContract";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
	archiveIndicatorDraft,
	createIndicatorDraft,
	filterIndicators,
	type IndicatorDefinition,
	type IndicatorEditValues,
	loadIndicatorCatalog,
	type MetricSelection,
	type MetricType,
	metricSelection,
	normalizeIndicatorFailure,
	publishIndicatorDraft,
	saveAndValidateIndicatorDraft,
	saveIndicatorDraft,
	supportsIndicatorCreation,
} from "./services/indicatorProjectionService";
import { listPlanningCatalogDomains, type PlanningCatalogDomain } from "./services/planningCatalogDomainService";
import { loadPlanningContextPolicy, resolveBusinessProcessBinding } from "./services/planningContextPolicyService";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

const typeByView: Record<string, MetricType> = {
	composite: "复合指标",
	derived: "派生指标",
	atomic: "原子指标",
	modifiers: "修饰词",
	periods: "时间周期",
};

const toForm = (selected: MetricSelection): IndicatorEditValues => ({
	code: selected.code,
	name: selected.name,
	definition: selected.definition || "",
	domain: selected.domain,
	category: selected.category || "",
	businessCategoryId: selected.businessCategoryId || null,
	dataDomainId: selected.dataDomainId || null,
	businessProcessId: selected.businessProcessId || null,
	metricType: selected.metricType || null,
	metricGroupCode: selected.metricGroupCode || "",
	sourceRefs: selected.sourceRefs || [],
	owner: selected.owner || "",
	ownerDept: selected.ownerDept || "",
	unit: selected.unit || "",
	precisionScale: selected.precisionScale ?? 0,
	aggregationType: selected.aggregationType || "",
	measureField: selected.measureField || "",
	dependencyCodes: parseIndicatorDependencyCodes(selected.dependencyIndicators),
});

export function MetricsPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useDataModelingMenuGrant();
	const metricType = typeByView[route.view] || "原子指标";
	const requestEpoch = useRef(0);
	const [searchParams, setSearchParams] = useSearchParams();
	const [catalog, setCatalog] = useState<IndicatorDefinition[]>([]);
	const [selected, setSelected] = useState<MetricSelection | null>(null);
	const [values, setValues] = useState<IndicatorEditValues>({});
	const [query, setQuery] = useState("");
	const [domain, setDomain] = useState("");
	const [businessCategoryId, setBusinessCategoryId] = useState("");
	const [architecture, setArchitecture] = useState<PlanningCatalogDomain[]>([]);
	const [processes, setProcesses] = useState<Sprint64BusinessProcess[]>([]);
	const [processMode, setProcessMode] = useState<WarehousePlanBusinessProcessMode>("AUTO_SELECT_SINGLE");
	const [contextFailure, setContextFailure] = useState("");
	const processEpoch = useRef(0);
	const [loading, setLoading] = useState(true);
	const [busy, setBusy] = useState<"save" | "validate" | "publish" | "archive" | "">("");
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const previousMetricType = useRef(metricType);
	const { message, show } = useTransientMessage();
	const select = useCallback((next: MetricSelection | null) => {
		setSelected(next);
		setValues(next ? toForm(next) : {});
	}, []);
	const load = useCallback(
		async (preferredIndicatorId?: string) => {
			const epoch = ++requestEpoch.current;
			setLoading(true);
			setFailure(null);
			try {
				const next = await loadIndicatorCatalog();
				if (requestEpoch.current !== epoch) return;
				setCatalog(next);
				const requestedId = preferredIndicatorId || searchParams.get("indicatorId");
				const visible = filterIndicators(next, { type: metricType, domain: "", query: "" });
				const target = (requestedId ? visible.find((item) => item.id === requestedId) : undefined) || visible[0];
				select(target ? metricSelection(target) : null);
			} catch (error) {
				if (requestEpoch.current !== epoch) return;
				setCatalog([]);
				select(null);
				setFailure(normalizeIndicatorFailure(error));
			} finally {
				if (requestEpoch.current === epoch) setLoading(false);
			}
		},
		[metricType, searchParams, select],
	);
	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);
	useEffect(() => {
		let active = true;
		void listPlanningCatalogDomains()
			.then((items) => {
				if (active) setArchitecture(items);
			})
			.catch(() => {
				if (active) setContextFailure("业务分类与数据域读取失败；已有指标仍可查看，但稳定上下文暂不可修改。");
			});
		return () => {
			active = false;
		};
	}, []);
	useEffect(() => {
		let active = true;
		void loadPlanningContextPolicy()
			.then((contextPolicy) => {
				if (active) setProcessMode(contextPolicy.policy.businessProcessMode);
			})
			.catch(() => {
				if (active) setContextFailure("规划参数读取失败，业务过程暂按唯一自动选择处理。");
			});
		return () => {
			active = false;
		};
	}, []);
	useEffect(() => {
		const dataDomainId = String(values.dataDomainId || "");
		const epoch = ++processEpoch.current;
		if (!dataDomainId) {
			setProcesses([]);
			return;
		}
		void listBusinessProcessesApi(dataDomainId)
			.then((items) => {
				if (processEpoch.current === epoch) setProcesses(items);
			})
			.catch(() => {
				if (processEpoch.current === epoch) {
					setProcesses([]);
					setContextFailure("业务过程读取失败；请刷新后重试。");
				}
			});
	}, [values.dataDomainId]);
	useEffect(() => {
		setValues((current) => {
			const currentMetricType = String(current.metricType || "").toUpperCase();
			if (currentMetricType !== "ATOMIC") {
				return current.businessProcessId ? { ...current, businessProcessId: null } : current;
			}
			const binding = resolveBusinessProcessBinding(processMode, current.businessProcessId, processes);
			const currentIsValid = binding.processes.some((item) => item.id === current.businessProcessId);
			const nextId = binding.showSelector && currentIsValid ? String(current.businessProcessId) : binding.selectedId;
			return (current.businessProcessId || null) === nextId ? current : { ...current, businessProcessId: nextId };
		});
	}, [processMode, processes]);
	useEffect(() => {
		if (previousMetricType.current !== metricType) {
			setQuery("");
			setDomain("");
			setBusinessCategoryId("");
			previousMetricType.current = metricType;
		}
	}, [metricType]);

	const visible = useMemo(
		() => filterIndicators(catalog, { type: metricType, domain, businessCategoryId, query }),
		[businessCategoryId, catalog, domain, metricType, query],
	);
	const businessCategories = useMemo(() => architecture.filter((item) => !item.parentId), [architecture]);
	const dataDomains = useMemo(() => architecture.filter((item) => Boolean(item.parentId)), [architecture]);
	const domainLabels = useMemo(() => new Map(dataDomains.map((item) => [item.id, item.name])), [dataDomains]);
	const choose = (row: IndicatorDefinition) => {
		select(metricSelection(row));
		const next = new URLSearchParams(searchParams);
		if (row.id) next.set("indicatorId", row.id);
		setSearchParams(next, { replace: true });
	};
	const create = () => {
		const selectedDomain = dataDomains.find((item) => item.id === domain);
		const categoryId = businessCategoryId || selectedDomain?.parentId || "";
		const selectedCategory = businessCategories.find((item) => item.id === categoryId);
		select({
			...createIndicatorDraft(metricType, selectedDomain?.code || ""),
			businessCategoryId: categoryId || null,
			dataDomainId: selectedDomain?.id || null,
			category: selectedCategory?.name || null,
		});
		const next = new URLSearchParams(searchParams);
		next.delete("indicatorId");
		setSearchParams(next, { replace: true });
	};
	const mutate = async (action: "save" | "validate" | "publish" | "archive") => {
		if (!selected || !canMaintain) return;
		setBusy(action);
		setFailure(null);
		try {
			if (action === "validate") {
				const { saved, validation } = await saveAndValidateIndicatorDraft(selected, values);
				select(saved);
				if (saved.id) {
					const next = new URLSearchParams(searchParams);
					next.set("indicatorId", saved.id);
					setSearchParams(next, { replace: true });
				}
				show(
					validation.valid
						? "指标已保存并校验通过"
						: validation.issues.map((issue) => issue.message).join("；") || "指标校验未通过",
				);
				await load(saved.id);
				return;
			}
			if (action === "archive") {
				if (!window.confirm(`确认归档“${selected.name}”？`)) return;
				await archiveIndicatorDraft(selected);
				show("指标已归档");
				await load();
				return;
			}
			const saved =
				action === "publish"
					? await publishIndicatorDraft(selected, values)
					: await saveIndicatorDraft(selected, values);
			select(saved);
			if (saved.id) {
				const next = new URLSearchParams(searchParams);
				next.set("indicatorId", saved.id);
				setSearchParams(next, { replace: true });
			}
			show(action === "publish" ? "指标已发布" : "指标草稿已保存");
			await load(saved.id);
		} catch (error) {
			setFailure(normalizeIndicatorFailure(error));
		} finally {
			setBusy("");
		}
	};

	return (
		<main className="dmx-metrics-page">
			<PageHeader description={route.description} title={route.title} trail="数据建模 / 数据指标" />
			{loading ? (
				<RequestState description="正在读取指标目录与版本事实。" kind="loading" title="正在加载指标" />
			) : failure?.kind === "permission" ? (
				<RequestState description={failure.message} kind="permission" title="无权访问指标" />
			) : (
				<div className="dmx-metric-workbench">
					<aside className="dmx-metric-catalog">
						<header>
							<h2>{metricType}</h2>
							<div>
								<Button
									aria-label="新建"
									disabled={!canMaintain || !supportsIndicatorCreation(metricType)}
									onClick={create}
									title={
										!canMaintain
											? "当前账号无指标维护权限"
											: supportsIndicatorCreation(metricType)
												? "新建指标"
												: "当前 owner 不支持新建该对象"
									}
								>
									<Plus size={15} />
								</Button>
								<Button aria-label="刷新" disabled={loading} onClick={() => void load()}>
									<RefreshCw size={15} />
								</Button>
							</div>
						</header>
						<div className="dmx-metric-layer">公共层</div>
						<select
							aria-label="业务分类"
							onChange={(event) => {
								setBusinessCategoryId(event.target.value);
								if (domain && dataDomains.find((item) => item.id === domain)?.parentId !== event.target.value) {
									setDomain("");
								}
							}}
							value={businessCategoryId}
						>
							<option value="">全部业务分类</option>
							{businessCategories.map((item) => (
								<option key={item.id} value={item.id}>
									{item.name}
								</option>
							))}
						</select>
						<select aria-label="数据域" onChange={(event) => setDomain(event.target.value)} value={domain}>
							<option value="">全部数据域</option>
							{dataDomains
								.filter((item) => !businessCategoryId || item.parentId === businessCategoryId)
								.map((item) => (
									<option key={item.id} value={item.id}>
										{item.name}
									</option>
								))}
						</select>
						<div className="dmx-metric-search">
							<Search size={14} />
							<input
								aria-label="搜索指标"
								onChange={(event) => setQuery(event.target.value)}
								placeholder="搜索"
								value={query}
							/>
						</div>
						<div className="dmx-metric-tree">
							{visible.map((item) => (
								<Button
									className={selected?.id === item.id ? "active" : ""}
									key={item.id || String(item.code)}
									onClick={() => choose(item)}
								>
									<span>△</span>
									<span>
										<small>{domainLabels.get(String(item.dataDomainId || "")) || item.domain || "未归属"}</small>
										<b>{item.code}</b>
										<em>{item.name}</em>
									</span>
								</Button>
							))}
							{!visible.length ? (
								<RequestState description="当前筛选条件下没有指标。" kind="empty" title="暂无指标" />
							) : null}
						</div>
					</aside>
					<section className="dmx-metric-editor">
						<div className="dmx-editor-tab">
							<span>△</span>
							<strong>{selected?.name || `新建${metricType}`}</strong>
							{selected?.status ? (
								<Status tone={selected.status === "PUBLISHED" ? "success" : "warning"}>{selected.status}</Status>
							) : null}
						</div>
						{selected ? (
							<>
								<div className="dmx-metric-toolbar">
									<Button disabled={!canMaintain || Boolean(busy)} primary onClick={() => void mutate("save")}>
										<Save size={15} />
										{busy === "save" ? "保存中…" : "保存"}
									</Button>
									<Button
										disabled={!canMaintain || Boolean(busy) || !selected.id}
										onClick={() => void mutate("validate")}
									>
										<CheckCircle2 size={15} />
										{busy === "validate" ? "校验中…" : "校验"}
									</Button>
									<Button
										disabled={!canMaintain || Boolean(busy) || !selected.id}
										onClick={() => void mutate("publish")}
									>
										<Send size={15} />
										{busy === "publish" ? "发布中…" : "发布"}
									</Button>
									<Button
										danger
										disabled={!canMaintain || Boolean(busy) || !selected.id}
										onClick={() => void mutate("archive")}
									>
										<Archive size={15} />
										归档
									</Button>
								</div>
								{failure ? (
									<div className="dmx-inline-error" role="alert">
										{failure.message}
									</div>
								) : null}
								{contextFailure ? (
									<div className="dmx-inline-error" role="alert">
										{contextFailure}
									</div>
								) : null}
								{!canMaintain ? <div className="dmx-capability-note">当前账号只有指标查看权限。</div> : null}
								<fieldset className="dmx-editor-fieldset" disabled={!canMaintain}>
									<MetricEditor
										businessCategories={businessCategories}
										codeLocked={Boolean(selected.id)}
										dataDomains={dataDomains}
										onChange={setValues}
										processes={processes}
										processMode={processMode}
										values={values}
									/>
								</fieldset>
							</>
						) : (
							<RequestState
								description={
									supportsIndicatorCreation(metricType)
										? "点击目录中的指标，或新建指标。"
										: "当前 owner 未返回该类型指标。"
								}
								kind="empty"
								title="请选择指标"
							/>
						)}
					</section>
				</div>
			)}
			<Toast message={message} />
		</main>
	);
}

export function MetricEditor({
	values,
	onChange,
	codeLocked,
	businessCategories,
	dataDomains,
	processes,
	processMode = "AUTO_SELECT_SINGLE",
}: {
	values: IndicatorEditValues;
	onChange: (values: IndicatorEditValues) => void;
	codeLocked: boolean;
	businessCategories: PlanningCatalogDomain[];
	dataDomains: PlanningCatalogDomain[];
	processes: Sprint64BusinessProcess[];
	processMode?: WarehousePlanBusinessProcessMode;
}) {
	const set = (key: keyof IndicatorEditValues, value: unknown) => onChange({ ...values, [key]: value });
	const metricType = String(values.metricType || "ATOMIC").toUpperCase();
	const selectedDomainId = String(values.dataDomainId || "");
	const availableDomains = dataDomains;
	const processBinding = resolveBusinessProcessBinding(processMode, values.businessProcessId, processes);
	const sourceRefs = values.sourceRefs || [];
	const sourceTypes: Array<{ value: IndicatorSourceType; label: string }> =
		metricType === "ATOMIC"
			? [
					{ value: "SEMANTIC_MODEL_REVISION", label: "语义模型修订" },
					{ value: "PHYSICAL_ASSET", label: "物理资产" },
				]
			: [{ value: "INDICATOR_VERSION", label: "指标版本" }];
	const updateSourceRef = (index: number, patch: Partial<IndicatorMetricSourceRef>) =>
		set(
			"sourceRefs",
			sourceRefs.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
		);
	const removeSourceRef = (index: number) =>
		set(
			"sourceRefs",
			sourceRefs.filter((_, itemIndex) => itemIndex !== index),
		);
	const addSourceRef = () =>
		set("sourceRefs", [...sourceRefs, { sourceType: sourceTypes[0].value, sourceId: "", sourceVersion: "" }]);
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
					<MetricField label="数据域" required={metricType !== "COMPOSITE"}>
						<select
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
					普通指标页不显示或编辑原始 SQL；SQL/Jinja 只在具备维护权限的高级 dbt 实现中处理。当前 owner
					尚未提供独立业务表达式契约，因此此处只维护聚合、度量和依赖语义。
				</p>
				<div>
					<MetricField label="聚合方式">
						<input
							onChange={(event) => set("aggregationType", event.target.value)}
							value={String(values.aggregationType || "")}
						/>
					</MetricField>
					<MetricField label="度量字段">
						<input
							onChange={(event) => set("measureField", event.target.value)}
							value={String(values.measureField || "")}
						/>
					</MetricField>
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
					<MetricField label="依赖指标" wide>
						<input
							onChange={(event) =>
								set(
									"dependencyCodes",
									event.target.value
										.split(",")
										.map((item) => item.trim())
										.filter(Boolean),
								)
							}
							value={Array.isArray(values.dependencyCodes) ? values.dependencyCodes.join(", ") : ""}
						/>
					</MetricField>
					<MetricField label="固定来源版本" required wide>
						<div className="dmx-source-refs">
							{sourceRefs.map((ref, index) => (
								<div className="dmx-source-ref-row" key={`${ref.sourceType}-${index}`}>
									<select
										onChange={(event) =>
											updateSourceRef(index, { sourceType: event.target.value as IndicatorSourceType })
										}
										value={ref.sourceType}
									>
										{sourceTypes.map((item) => (
											<option key={item.value} value={item.value}>
												{item.label}
											</option>
										))}
									</select>
									<input
										onChange={(event) => updateSourceRef(index, { sourceId: event.target.value })}
										placeholder="稳定 ID"
										value={ref.sourceId}
									/>
									<input
										onChange={(event) => updateSourceRef(index, { sourceVersion: event.target.value })}
										placeholder="固定版本，如 r7 / v3"
										value={ref.sourceVersion}
									/>
									<Button aria-label={`删除来源 ${index + 1}`} onClick={() => removeSourceRef(index)}>
										<Trash2 size={15} />
									</Button>
								</div>
							))}
							<Button className="dmx-source-ref-add" onClick={addSourceRef}>
								<Plus size={14} /> 添加固定来源
							</Button>
							<small>
								{metricType === "ATOMIC"
									? "原子指标绑定语义模型修订或物理资产；发布时至少一项。"
									: "派生/复合指标只绑定已固定的上游指标版本。"}
							</small>
						</div>
					</MetricField>
				</div>
			</section>
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
