import { Archive, CheckCircle2, Plus, RefreshCw, Save, Search, Send } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { useCatalogMaintainerAccess } from "@/hooks/useModuleManageAccess";
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
	owner: selected.owner || "",
	ownerDept: selected.ownerDept || "",
	unit: selected.unit || "",
	precisionScale: selected.precisionScale ?? 0,
	aggregationType: selected.aggregationType || "",
	measureField: selected.measureField || "",
	dependencyCodes: String(selected.dependencyIndicators || "")
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean),
});

export function MetricsPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useCatalogMaintainerAccess();
	const metricType = typeByView[route.view] || "原子指标";
	const requestEpoch = useRef(0);
	const [searchParams, setSearchParams] = useSearchParams();
	const [catalog, setCatalog] = useState<IndicatorDefinition[]>([]);
	const [selected, setSelected] = useState<MetricSelection | null>(null);
	const [values, setValues] = useState<IndicatorEditValues>({});
	const [query, setQuery] = useState("");
	const [domain, setDomain] = useState("");
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
		if (previousMetricType.current !== metricType) {
			setQuery("");
			setDomain("");
			previousMetricType.current = metricType;
		}
	}, [metricType]);

	const visible = useMemo(
		() => filterIndicators(catalog, { type: metricType, domain, query }),
		[catalog, domain, metricType, query],
	);
	const domains = useMemo(
		() => Array.from(new Set(catalog.map((item) => String(item.domain || "")).filter(Boolean))).sort(),
		[catalog],
	);
	const choose = (row: IndicatorDefinition) => {
		select(metricSelection(row));
		const next = new URLSearchParams(searchParams);
		if (row.id) next.set("indicatorId", row.id);
		setSearchParams(next, { replace: true });
	};
	const create = () => {
		select(createIndicatorDraft(metricType, domain));
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
								<button
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
									type="button"
								>
									<Plus size={15} />
								</button>
								<button aria-label="刷新" disabled={loading} onClick={() => void load()} type="button">
									<RefreshCw size={15} />
								</button>
							</div>
						</header>
						<div className="dmx-metric-layer">公共层</div>
						<select aria-label="数据域" onChange={(event) => setDomain(event.target.value)} value={domain}>
							<option value="">全部数据域</option>
							{domains.map((item) => (
								<option key={item}>{item}</option>
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
								<button
									className={selected?.id === item.id ? "active" : ""}
									key={item.id || String(item.code)}
									onClick={() => choose(item)}
									type="button"
								>
									<span>△</span>
									<span>
										<small>{item.domain || "未归属"}</small>
										<b>{item.code}</b>
										<em>{item.name}</em>
									</span>
								</button>
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
								{!canMaintain ? <div className="dmx-capability-note">当前账号只有指标查看权限。</div> : null}
								<fieldset className="dmx-editor-fieldset" disabled={!canMaintain}>
									<MetricEditor codeLocked={Boolean(selected.id)} values={values} onChange={setValues} />
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

function MetricEditor({
	values,
	onChange,
	codeLocked,
}: {
	values: IndicatorEditValues;
	onChange: (values: IndicatorEditValues) => void;
	codeLocked: boolean;
}) {
	const set = (key: keyof IndicatorEditValues, value: unknown) => onChange({ ...values, [key]: value });
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
					<MetricField label="数据域">
						<input onChange={(event) => set("domain", event.target.value)} value={String(values.domain || "")} />
					</MetricField>
					<MetricField label="指标分类">
						<input onChange={(event) => set("category", event.target.value)} value={String(values.category || "")} />
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
