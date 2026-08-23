import { Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { getModelSpecRevision, listModelSpecs } from "@/api/modelSpecApi";
import { listBusinessProcessesApi, type Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";
import { actionColumn, type CompactColumns, CompactTable } from "@/components/table";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { parseIndicatorDependencyCodes } from "@/features/modeling/indicators/indicatorDefinitionContract";
import { statusLabel } from "@/utils/customerDisplayLabels";
import type { DataModelingRoute } from "../types";
import { MetricEditor } from "./MetricEditor";
import { Button, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import {
	archiveIndicatorDraft,
	createIndicatorDraft,
	filterIndicators,
	type IndicatorCalculationBatch,
	type IndicatorCalculationHistory,
	type IndicatorDefinition,
	type IndicatorEditValues,
	loadIndicatorCalculationHistory,
	loadIndicatorCatalog,
	type MetricSelection,
	type MetricType,
	metricSelection,
	normalizeIndicatorFailure,
	publishIndicatorDraft,
	saveAndValidateIndicatorDraft,
	saveIndicatorDraft,
	submitIndicatorCalculation,
	supportsIndicatorCalculation,
	supportsIndicatorCreation,
} from "./services/indicatorProjectionService";
import { listPlanningCatalogDomains, type PlanningCatalogDomain } from "./services/planningCatalogDomainService";
import { resolveBusinessProcessBinding } from "./services/planningContextPolicyService";
import { useDataModelingMenuGrant } from "./useDataModelingMenuGrant";

export { MetricEditor } from "./MetricEditor";

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
	expressionSql: selected.expressionSql || "",
	sourceTable: selected.sourceTable || null,
	sourceLayer: selected.sourceLayer || null,
	targetLayer: selected.targetLayer || null,
	targetModelName: selected.targetModelName || null,
	dateColumn: selected.dateColumn || null,
	dependencyCodes: parseIndicatorDependencyCodes(selected.dependencyIndicators),
});

export function resolveMetricCatalogSelection(
	rows: IndicatorDefinition[],
	requestedIndicatorId: string,
): MetricSelection | null {
	if (!requestedIndicatorId) return null;
	const target = rows.find((item) => item.id === requestedIndicatorId);
	return target ? metricSelection(target) : null;
}

export function reconcileMetricBusinessProcessContext(
	current: IndicatorEditValues,
	processes: Sprint64BusinessProcess[],
): IndicatorEditValues {
	const currentMetricType = String(current.metricType || "").toUpperCase();
	if (currentMetricType !== "ATOMIC") {
		return current;
	}
	const binding = resolveBusinessProcessBinding(current.businessProcessId, processes);
	const currentIsValid = binding.processes.some((item) => item.id === current.businessProcessId);
	const nextId = binding.showSelector && currentIsValid ? String(current.businessProcessId) : binding.selectedId;
	return (current.businessProcessId || null) === nextId ? current : { ...current, businessProcessId: nextId };
}

export function MetricsPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useDataModelingMenuGrant();
	const metricType = typeByView[route.view] || "原子指标";
	const canCalculate = supportsIndicatorCalculation(metricType);
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
	const [metricModels, setMetricModels] = useState<ModelSpecView[]>([]);
	const [selectedCalculationIds, setSelectedCalculationIds] = useState<string[]>([]);
	const [calculationBatch, setCalculationBatch] = useState<IndicatorCalculationBatch | null>(null);
	const [calculationHistory, setCalculationHistory] = useState<IndicatorCalculationHistory[]>([]);
	const [contextFailure, setContextFailure] = useState("");
	const processEpoch = useRef(0);
	const [loading, setLoading] = useState(true);
	const [busy, setBusy] = useState<"save" | "validate" | "publish" | "archive" | "calculate" | "">("");
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const previousMetricType = useRef(metricType);
	const { message, show } = useTransientMessage();
	const select = useCallback((next: MetricSelection | null) => {
		setSelected(next);
		setValues(next ? toForm(next) : {});
	}, []);
	useEffect(() => {
		if (!canCalculate) return;
		let active = true;
		void listModelSpecs()
			.then((items) => {
				if (active) {
					setMetricModels((current) => [
						...items,
						...current.filter(
							(existing) => !items.some((item) => item.id === existing.id && item.revision === existing.revision),
						),
					]);
				}
			})
			.catch(() => {
				if (active) setContextFailure("已发布模型读取失败；原子指标来源暂不可选择，请刷新后重试。");
			});
		return () => {
			active = false;
		};
	}, [canCalculate]);
	const pinnedModelSource = values.sourceRefs?.find((ref) => ref.sourceType === "SEMANTIC_MODEL_REVISION");
	const pinnedModelRevision = String(pinnedModelSource?.sourceVersion || "").match(/^r([1-9][0-9]*)$/i)?.[1] || "";
	useEffect(() => {
		if (!pinnedModelSource?.sourceId || !pinnedModelRevision) return;
		let active = true;
		void getModelSpecRevision(pinnedModelSource.sourceId, Number(pinnedModelRevision))
			.then((model) => {
				if (!active) return;
				setMetricModels((current) => [
					...current.filter((item) => item.id !== model.id || item.revision !== model.revision),
					model,
				]);
			})
			.catch(() => {
				if (active) setContextFailure("指标固定的模型版本读取失败；请确认该版本仍可访问后重试。");
			});
		return () => {
			active = false;
		};
	}, [pinnedModelRevision, pinnedModelSource?.sourceId]);
	const load = useCallback(
		async (preferredIndicatorId?: string | null) => {
			const epoch = ++requestEpoch.current;
			setLoading(true);
			setFailure(null);
			try {
				const next = await loadIndicatorCatalog();
				if (requestEpoch.current !== epoch) return;
				setCatalog(next);
				const requestedId =
					preferredIndicatorId === undefined ? searchParams.get("indicatorId") || "" : preferredIndicatorId || "";
				const visible = filterIndicators(next, { type: metricType, domain: "", query: "" });
				select(resolveMetricCatalogSelection(visible, requestedId));
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
		setValues((current) => reconcileMetricBusinessProcessContext(current, processes));
	}, [processes]);
	useEffect(() => {
		if (previousMetricType.current !== metricType) {
			setQuery("");
			setDomain("");
			setBusinessCategoryId("");
			setSelectedCalculationIds([]);
			setCalculationBatch(null);
			previousMetricType.current = metricType;
		}
	}, [metricType]);
	useEffect(() => {
		if (!canCalculate || !selected?.id || String(selected.status || "").toUpperCase() !== "PUBLISHED") {
			setCalculationHistory([]);
			return;
		}
		let active = true;
		void loadIndicatorCalculationHistory(selected.id)
			.then((items) => {
				if (active) setCalculationHistory(items);
			})
			.catch(() => {
				if (active) setCalculationHistory([]);
			});
		return () => {
			active = false;
		};
	}, [canCalculate, selected?.id, selected?.status]);

	const visible = useMemo(
		() => filterIndicators(catalog, { type: metricType, domain, businessCategoryId, query }),
		[businessCategoryId, catalog, domain, metricType, query],
	);
	const businessCategories = useMemo(() => architecture.filter((item) => !item.parentId), [architecture]);
	const dataDomains = useMemo(() => architecture.filter((item) => Boolean(item.parentId)), [architecture]);
	const domainLabels = useMemo(() => new Map(dataDomains.map((item) => [item.id, item.name])), [dataDomains]);
	const businessCategoryLabels = useMemo(
		() => new Map(businessCategories.map((item) => [item.id, item.name])),
		[businessCategories],
	);
	const choose = (row: IndicatorDefinition) => {
		select(metricSelection(row));
		const next = new URLSearchParams(searchParams);
		if (row.id) next.set("indicatorId", row.id);
		setSearchParams(next, { replace: true });
	};
	const returnToList = () => {
		select(null);
		const next = new URLSearchParams(searchParams);
		next.delete("indicatorId");
		setSearchParams(next, { replace: true });
	};
	const create = () => {
		const isModifier = metricType === "修饰词";
		const selectedDomain = dataDomains.find((item) => item.id === domain);
		const categoryId = businessCategoryId || selectedDomain?.parentId || "";
		const selectedCategory = businessCategories.find((item) => item.id === categoryId);
		select({
			...createIndicatorDraft(metricType, selectedDomain?.code || ""),
			businessCategoryId: categoryId || null,
			dataDomainId: selectedDomain?.id || null,
			category: isModifier ? "MODIFIER" : selectedCategory?.name || null,
		});
		const next = new URLSearchParams(searchParams);
		next.delete("indicatorId");
		setSearchParams(next, { replace: true });
	};
	const catalogColumns: CompactColumns<IndicatorDefinition> = [
		{
			title: "指标编码",
			dataIndex: "code",
			width: 180,
			render: (value) => <strong className="dmx-code-cell">{String(value || "—")}</strong>,
		},
		{ title: "指标名称", dataIndex: "name", width: 180 },
		{
			title: "业务分类",
			dataIndex: "businessCategoryId",
			width: 150,
			render: (value) => businessCategoryLabels.get(String(value || "")) || "—",
		},
		{
			title: "数据域",
			dataIndex: "dataDomainId",
			width: 150,
			render: (value, row) => domainLabels.get(String(value || "")) || row.domain || "未归属",
		},
		{ title: "数仓分层", key: "layer", width: 100, render: () => "公共层" },
		{ title: "负责人", dataIndex: "owner", width: 130, render: (value) => String(value || "—") },
		{ title: "版本", dataIndex: "version", width: 90, render: (value) => String(value || "—") },
		{
			title: "状态",
			dataIndex: "status",
			width: 110,
			render: (value) => {
				const status = String(value || "DRAFT");
				return <Status tone={status === "PUBLISHED" ? "success" : "warning"}>{statusLabel(status)}</Status>;
			},
		},
		actionColumn<IndicatorDefinition>((row) => [
			{ key: "open", label: canMaintain ? "编辑" : "查看", onClick: () => choose(row) },
		]),
	];
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
				returnToList();
				await load(null);
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
	const calculate = async (ids: string[]) => {
		if (!canMaintain || !ids.length) return;
		setBusy("calculate");
		setFailure(null);
		try {
			const batch = await submitIndicatorCalculation(ids);
			setCalculationBatch(batch);
			show(`计算已完成：成功 ${batch.successCount} 项，失败 ${batch.failedCount} 项`);
			if (selected?.id && ids.includes(selected.id)) {
				setCalculationHistory(await loadIndicatorCalculationHistory(selected.id));
			}
		} catch (error) {
			setFailure(normalizeIndicatorFailure(error));
		} finally {
			setBusy("");
		}
	};
	const calculationColumns: CompactColumns<IndicatorCalculationBatch["items"][number]> = [
		{ title: "指标编码", dataIndex: "code", width: 190, render: (value) => String(value || "—") },
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (value) => <Status tone={value === "SUCCESS" ? "success" : "danger"}>{statusLabel(value)}</Status>,
		},
		{ title: "计算值", dataIndex: "value", width: 120, render: (value) => String(value ?? "—") },
		{
			title: "来源",
			dataIndex: "sourceMode",
			width: 120,
			render: (value) => (value === "MODEL_FIELD" ? "模型字段" : value === "FORMULA" ? "受控公式" : "—"),
		},
		{
			title: "物理实现",
			key: "implementation",
			width: 260,
			render: (_, row) => (row.relation && row.field ? `${row.relation}.${row.field}` : "—"),
		},
		{ title: "错误", dataIndex: "errorMessage", width: 260, render: (value) => String(value || "—") },
	];
	const historyColumns: CompactColumns<IndicatorCalculationHistory> = [
		{ title: "提交时间", dataIndex: "runAt", width: 190, render: (value) => String(value || "—") },
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (value) => <Status tone={value === "SUCCESS" ? "success" : "danger"}>{statusLabel(value)}</Status>,
		},
		{ title: "计算值", dataIndex: "computedValue", width: 120, render: (value) => String(value ?? "—") },
		{ title: "上次值", dataIndex: "previousValue", width: 120, render: (value) => String(value ?? "—") },
		{ title: "处理行数", dataIndex: "rowsProcessed", width: 110, render: (value) => String(value ?? "—") },
		{ title: "耗时(ms)", dataIndex: "durationMs", width: 110, render: (value) => String(value ?? "—") },
		{ title: "请求号", dataIndex: "requestId", width: 300, render: (value) => String(value || "—") },
		{ title: "错误", dataIndex: "errorMessage", width: 260, render: (value) => String(value || "—") },
	];

	return (
		<main className="dmx-metrics-page">
			<PageHeader
				actions={
					!selected ? (
						<>
							<Button disabled={loading} onClick={() => void load(null)}>
								刷新
							</Button>
							<Button
								disabled={!canMaintain || !supportsIndicatorCreation(metricType)}
								onClick={create}
								primary
								title={
									!canMaintain
										? "当前账号无指标维护权限"
										: supportsIndicatorCreation(metricType)
											? `新建${metricType}`
											: "当前功能入口不支持新建该对象"
								}
							>
								新建{metricType}
							</Button>
						</>
					) : undefined
				}
				description={route.description}
				title={route.title}
				trail="数据建模 / 数据指标"
			/>
			{loading ? (
				<RequestState description="正在读取指标目录与版本事实。" kind="loading" title="正在加载指标" />
			) : failure?.kind === "permission" ? (
				<RequestState description={failure.message} kind="permission" title="无权访问指标" />
			) : failure?.kind === "request" && !selected && !catalog.length ? (
				<RequestState description={failure.message} kind="error" onRetry={() => void load(null)} title="指标读取失败" />
			) : selected ? (
				<div className="dmx-metric-workbench">
					<section className="dmx-metric-editor">
						<div className="dmx-editor-tab">
							<span>△</span>
							<strong>{selected.name || `新建${metricType}`}</strong>
							{selected.status ? (
								<Status tone={selected.status === "PUBLISHED" ? "success" : "warning"}>
									{statusLabel(selected.status)}
								</Status>
							) : null}
						</div>
						<div className="dmx-metric-toolbar">
							<Button disabled={Boolean(busy)} onClick={returnToList}>
								返回指标列表
							</Button>
							<Button disabled={!canMaintain || Boolean(busy)} primary onClick={() => void mutate("save")}>
								{busy === "save" ? "保存中…" : "保存"}
							</Button>
							{canCalculate ? (
								<>
									<Button
										disabled={!canMaintain || Boolean(busy) || !selected.id}
										onClick={() => void mutate("validate")}
									>
										{busy === "validate" ? "校验中…" : "校验"}
									</Button>
									<Button
										disabled={!canMaintain || Boolean(busy) || !selected.id}
										onClick={() => void mutate("publish")}
									>
										{busy === "publish" ? "发布中…" : "发布"}
									</Button>
									<Button
										disabled={
											!canMaintain ||
											Boolean(busy) ||
											!selected.id ||
											String(selected.status || "").toUpperCase() !== "PUBLISHED"
										}
										onClick={() => selected.id && void calculate([selected.id])}
										primary
									>
										{busy === "calculate" ? "计算中…" : "提交计算"}
									</Button>
								</>
							) : null}
							<Button
								danger
								disabled={!canMaintain || Boolean(busy) || !selected.id}
								onClick={() => void mutate("archive")}
							>
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
								metricModels={metricModels}
								indicators={catalog}
								values={values}
							/>
						</fieldset>
						{canCalculate && calculationBatch?.items.some((item) => item.indicatorId === selected.id) ? (
							<section className="dmx-metric-section" aria-label="本次计算结果">
								<h3>本次计算结果</h3>
								<p className="dmx-capability-note">请求号：{calculationBatch.requestId}</p>
								<CompactTable
									columns={calculationColumns}
									dataSource={calculationBatch.items.filter((item) => item.indicatorId === selected.id)}
									pagination={false}
									rowKey={(row) => `${row.indicatorId}-${row.runAt || calculationBatch.requestId}`}
								/>
							</section>
						) : null}
						{canCalculate && calculationHistory.length ? (
							<section className="dmx-metric-section dmx-metric-section--history" aria-label="计算历史">
								<h3>计算历史</h3>
								<CompactTable
									columns={historyColumns}
									dataSource={calculationHistory}
									pagination={{ pageSize: 5 }}
									rowKey={(row) => row.id}
								/>
							</section>
						) : canCalculate && String(selected.status || "").toUpperCase() === "PUBLISHED" ? (
							<div className="dmx-capability-note">该指标尚无计算记录，可点击“提交计算”生成首条运行事实。</div>
						) : null}
					</section>
				</div>
			) : (
				<section className="dmx-catalog-panel dmx-catalog-panel--list" aria-label={`${metricType}列表`}>
					{contextFailure ? (
						<div className="dmx-inline-error" role="alert">
							{contextFailure}
						</div>
					) : null}
					{!canMaintain ? <div className="dmx-capability-note">当前账号只有指标查看权限。</div> : null}
					<div className="dmx-list-toolbar">
						<label>
							<Search size={15} />
							<input
								aria-label="搜索指标列表"
								onChange={(event) => setQuery(event.target.value)}
								placeholder="搜索指标编码、名称、业务口径或负责人"
								value={query}
							/>
						</label>
						<select
							aria-label="按业务分类筛选"
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
						<select aria-label="按数据域筛选" onChange={(event) => setDomain(event.target.value)} value={domain}>
							<option value="">全部数据域</option>
							{dataDomains
								.filter((item) => !businessCategoryId || item.parentId === businessCategoryId)
								.map((item) => (
									<option key={item.id} value={item.id}>
										{item.name}
									</option>
								))}
						</select>
						{canCalculate ? (
							<Button
								disabled={!canMaintain || Boolean(busy) || !selectedCalculationIds.length}
								onClick={() => void calculate(selectedCalculationIds)}
								primary
							>
								{busy === "calculate" ? "计算中…" : `提交计算（${selectedCalculationIds.length}）`}
							</Button>
						) : null}
						<span>共 {visible.length} 条</span>
					</div>
					{visible.length ? (
						<div className="dmx-table-scroll">
							<CompactTable<IndicatorDefinition>
								className="dmx-metric-table"
								columns={catalogColumns}
								dataSource={visible}
								pagination={{ pageSize: 10 }}
								rowSelection={
									canMaintain && canCalculate
										? {
												selectedRowKeys: selectedCalculationIds,
												onChange: (keys) => setSelectedCalculationIds(keys.map(String)),
												getCheckboxProps: (row) => ({
													disabled: !row.id || String(row.status || "").toUpperCase() !== "PUBLISHED",
													name: String(row.code || row.name || "indicator"),
												}),
											}
										: undefined
								}
								rowKey={(row) => row.id || String(row.code)}
								scroll={{ x: 1180 }}
							/>
						</div>
					) : (
						<RequestState description="请调整搜索或筛选条件后重试。" kind="empty" title={`暂无${metricType}`} />
					)}
					{canCalculate && calculationBatch ? (
						<section className="dmx-metric-section" aria-label="批量计算结果">
							<h3>批量计算结果</h3>
							<p className="dmx-capability-note">
								请求号：{calculationBatch.requestId}；成功 {calculationBatch.successCount} 项，失败{" "}
								{calculationBatch.failedCount} 项。
							</p>
							<CompactTable
								columns={calculationColumns}
								dataSource={calculationBatch.items}
								pagination={{ pageSize: 10 }}
								rowKey={(row) => `${row.indicatorId}-${row.runAt || calculationBatch.requestId}`}
							/>
						</section>
					) : null}
				</section>
			)}
			<Toast message={message} />
		</main>
	);
}
