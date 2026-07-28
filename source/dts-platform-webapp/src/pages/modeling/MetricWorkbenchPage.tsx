import { Alert, Button, Empty, Form, Input, Modal, Segmented, Select, Space, Spin, Tabs, Tag } from "antd";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { toast } from "sonner";
import { bindModelSpecMetricRef, listModelSpecs } from "@/api/modelSpecApi";
import {
	createModelFieldIndicatorDraft,
	listIndicators,
	listMeasurementUnits,
	type MeasurementUnitView,
} from "@/api/platformApi";
import { JourneyContextBar } from "@/components/journey";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import IndicatorDashboardPage from "../governance/IndicatorDashboardPage";
import IndicatorStorePage from "../governance/IndicatorStorePage";
import IndicatorTemplatePage from "../governance/IndicatorTemplatePage";
import MyIndicatorDashboard from "../governance/MyIndicatorDashboard";
import { buildMetricWorkbenchLocation } from "./indicatorDefinitionWorkflow";
import { loadAllIndicatorPages } from "./indicatorPagination";
import { IndicatorDefinitionPanel } from "./metric-workbench/IndicatorDefinitionPanel";
import {
	buildMetricWorkbenchViewLocation,
	type MetricWorkbenchView,
	resolveMetricWorkbenchView,
} from "./metricWorkbenchNavigation";
import type { CanonicalModelSpecView, ModelSpecField, ModelSpecType } from "./modelSpecV2Contract";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";

const METRIC_MODEL_TYPES: ModelSpecType[] = ["FACT", "SUMMARY", "APPLICATION"];

type IndicatorSummary = {
	id: string;
	code?: string;
	name?: string;
	status?: string;
	version?: string;
};

type IndicatorDraftForm = {
	code: string;
	name: string;
	definition?: string;
	aggregationType: string;
};

const publishedVersion = (value?: string) => {
	const match = String(value || "")
		.trim()
		.match(/^v?([1-9][0-9]*)$/i);
	return match ? Number(match[1]) : null;
};

const draftCode = (model: CanonicalModelSpecView, field: ModelSpecField) =>
	`${model.name}_${field.name}`
		.normalize("NFKD")
		.replace(/[^A-Za-z0-9_]+/g, "_")
		.replace(/^_+|_+$/g, "")
		.toUpperCase()
		.slice(0, 64) || `METRIC_${Date.now()}`;

export default function MetricWorkbenchPage() {
	const location = useLocation();
	const navigate = useNavigate();
	const canManage = useGovernanceManageAccess();
	const [form] = Form.useForm<IndicatorDraftForm>();
	const [models, setModels] = useState<CanonicalModelSpecView[]>([]);
	const [indicators, setIndicators] = useState<IndicatorSummary[]>([]);
	const [units, setUnits] = useState<MeasurementUnitView[]>([]);
	const [selectedModelId, setSelectedModelId] = useState("");
	const [selectedField, setSelectedField] = useState<ModelSpecField | null>(null);
	const [selectedIndicatorId, setSelectedIndicatorId] = useState("");
	const [typeFilter, setTypeFilter] = useState<ModelSpecType | "ALL">("ALL");
	const [keyword, setKeyword] = useState("");
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [draftOpen, setDraftOpen] = useState(false);
	const [associateOpen, setAssociateOpen] = useState(false);
	const activeView = resolveMetricWorkbenchView(location.search);

	const journeyContext = useMemo(() => {
		const params = new URLSearchParams(location.search);
		return {
			journey: params.get("journey") || "",
			modelSpecId: params.get("modelSpecId") || params.get("modelId") || "",
			standardDraftId: params.get("standardDraftId") || "",
			metricId: params.get("metricId") || "",
		};
	}, [location.search]);

	const load = useCallback(async () => {
		setLoading(true);
		const loadModels = async () => {
			try {
				const modelResult = await listModelSpecs();
				const eligible = (Array.isArray(modelResult) ? modelResult : []).filter(
					(model): model is CanonicalModelSpecView =>
						model.compatibilityMode === "CANONICAL" &&
						METRIC_MODEL_TYPES.includes(model.modelType) &&
						model.status === "PUBLISHED",
				);
				setModels(eligible);
				setSelectedModelId((current) => {
					if (eligible.some((model) => model.id === current)) return current;
					if (eligible.some((model) => model.id === journeyContext.modelSpecId)) return journeyContext.modelSpecId;
					return eligible[0]?.id || "";
				});
			} catch {
				setModels([]);
				setSelectedModelId("");
				toast.error("已发布模型加载失败，请稍后重试");
			}
		};
		const loadIndicators = async () => {
			try {
				setIndicators(
					await loadAllIndicatorPages<IndicatorSummary>(
						(page, size) =>
							listIndicators({ status: "PUBLISHED", page, size }) as Promise<
								{ content?: IndicatorSummary[]; totalPages?: number } | IndicatorSummary[]
							>,
					),
				);
			} catch {
				setIndicators([]);
				toast.error("已发布指标加载失败，模型浏览仍可继续");
			}
		};
		const loadUnits = async () => {
			try {
				const unitResult = await listMeasurementUnits();
				setUnits(Array.isArray(unitResult) ? unitResult : []);
			} catch {
				setUnits([]);
				toast.error("计量单位加载失败，指标创建前请稍后重试");
			}
		};
		try {
			await Promise.all([loadModels(), loadIndicators(), loadUnits()]);
		} finally {
			setLoading(false);
		}
	}, [journeyContext.modelSpecId]);

	useEffect(() => void load(), [load]);

	const filteredModels = useMemo(() => {
		const normalized = keyword.trim().toLowerCase();
		return models.filter((model) => {
			if (typeFilter !== "ALL" && model.modelType !== typeFilter) return false;
			return !normalized || `${model.name} ${model.description || ""}`.toLowerCase().includes(normalized);
		});
	}, [keyword, models, typeFilter]);

	const selected = models.find((model) => model.id === selectedModelId) || null;
	const metricReferenceCount = models.reduce((total, model) => total + model.metricRefs.length, 0);
	const measureFieldCount = models.reduce(
		(total, model) => total + model.fields.filter((field) => field.role === "MEASURE").length,
		0,
	);
	const unitById = useMemo(() => new Map(units.map((unit) => [unit.id, unit])), [units]);
	const indicatorById = useMemo(() => new Map(indicators.map((indicator) => [indicator.id, indicator])), [indicators]);

	const fieldUnit = (model: CanonicalModelSpecView, fieldName: string) => {
		const binding = model.standardBindings.find((item) => item.fieldName === fieldName);
		const unit = binding?.measurementUnitId ? unitById.get(binding.measurementUnitId) : null;
		return { binding, unit };
	};

	const openDraft = (field: ModelSpecField) => {
		if (!selected) return;
		setSelectedField(field);
		form.setFieldsValue({
			code: draftCode(selected, field),
			name: `${selected.name}-${field.name}`,
			definition: `基于已发布模型 ${selected.name} v${selected.revision} 的度量字段 ${field.name}`,
			aggregationType: "SUM",
		});
		setDraftOpen(true);
	};

	const createDraft = async () => {
		if (!selected || !selectedField) return;
		const values = await form.validateFields();
		const { binding, unit } = fieldUnit(selected, selectedField.name);
		setSaving(true);
		try {
			const created = (await createModelFieldIndicatorDraft({
				indicator: {
					...values,
					unit: unit?.symbol,
					precisionScale: unit?.precision,
				},
				modelSpecId: selected.id,
				modelRevision: selected.revision,
				fieldName: selectedField.name,
				measurementUnitId: binding?.measurementUnitId || null,
				measurementUnitVersion: binding?.measurementUnitVersion || null,
			})) as IndicatorSummary;
			setDraftOpen(false);
			toast.success("原子指标草稿已创建，并保留模型字段与计量单位版本来源");
			const ownerParams = new URLSearchParams(location.search);
			ownerParams.set("view", "definition");
			ownerParams.set("indicatorId", created.id);
			ownerParams.set("returnTo", location.pathname + location.search);
			navigate(buildMetricWorkbenchLocation(ownerParams.toString(), ""));
		} catch (error: any) {
			toast.error(error?.message || "指标草稿创建失败");
		} finally {
			setSaving(false);
		}
	};

	const associate = async () => {
		if (!selected) return;
		const indicator = indicatorById.get(selectedIndicatorId);
		const version = publishedVersion(indicator?.version);
		if (!indicator || indicator.status !== "PUBLISHED" || !version) {
			toast.error("只能关联带稳定版本的已发布指标");
			return;
		}
		setSaving(true);
		try {
			const updated = await bindModelSpecMetricRef(
				{ id: selected.id, revision: selected.revision, checksum: selected.checksum },
				{ metricId: indicator.id, version },
			);
			setModels((current) => current.map((model) => (model.id === updated.id ? updated : model)));
			setAssociateOpen(false);
			setSelectedIndicatorId("");
			toast.success("已将指标 owner 的发布版本写回模型新 revision");
		} catch (error: any) {
			toast.error(error?.message || "指标版本关联失败，请刷新后重试");
		} finally {
			setSaving(false);
		}
	};

	return (
		<SemanticWorkspaceFrame
			activeKey="workbench"
			title="指标工作台"
			stats={[
				{ label: "已发布模型", value: models.length, tone: "green" },
				{ label: "可关联指标", value: indicators.length, tone: "blue" },
				{ label: "指标引用", value: metricReferenceCount, tone: "amber" },
				{ label: "度量字段", value: measureFieldCount, tone: "gray" },
			]}
			actions={
				<Space wrap>
					{activeView === "model" ? (
						<>
							<Button onClick={() => void load()} loading={loading} icon={<RefreshCw size={16} />}>
								刷新
							</Button>
							<Button onClick={() => navigate("/modeling/models")}>查看模型中心</Button>
							<Button type="primary" disabled={!selected || !canManage} onClick={() => setAssociateOpen(true)}>
								关联已发布指标
							</Button>
						</>
					) : null}
				</Space>
			}
		>
			<JourneyContextBar stage="metrics" />
			{journeyContext.journey ? (
				<Alert
					className="mb-4"
					type={journeyContext.modelSpecId ? "info" : "warning"}
					showIcon
					data-testid="metric-workbench-e2e-context"
					message="来自端到端数据产品旅程"
					description={`模型 ${journeyContext.modelSpecId || "待选择"} · 标准草稿 ${journeyContext.standardDraftId || "未携带"} · 指标 ${journeyContext.metricId || "待引用"}`}
				/>
			) : null}
			<div className="mb-4 rounded-lg border border-slate-200 bg-white p-2 shadow-sm">
				<div className="grid grid-cols-2 gap-2 md:grid-cols-4" data-testid="metric-workbench-navigation">
					{[
						{ label: "1 定义与发布", value: "definition" },
						{ label: "2 从模型创建", value: "model" },
						{ label: "3 模板复用", value: "templates" },
						{ label: "4 运行与消费", value: "consumption" },
					].map((item) => (
						<Button
							key={item.value}
							block
							type={activeView === item.value ? "primary" : "text"}
							onClick={() =>
								navigate(
									buildMetricWorkbenchViewLocation(item.value as MetricWorkbenchView, location.search, location.hash),
								)
							}
						>
							{item.label}
						</Button>
					))}
				</div>
			</div>
			{activeView === "definition" ? (
				<div data-testid="metric-indicator-owner">
					<IndicatorDefinitionPanel />
				</div>
			) : null}
			{activeView === "templates" ? <IndicatorTemplatePage /> : null}
			{activeView === "consumption" ? (
				<div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
					<Tabs
						items={[
							{ key: "dashboard", label: "运行看板", children: <IndicatorDashboardPage /> },
							{ key: "store", label: "指标商店", children: <IndicatorStorePage /> },
							{ key: "mine", label: "我的订阅", children: <MyIndicatorDashboard /> },
						]}
					/>
				</div>
			) : null}
			{activeView === "model" ? (
				<div className="grid gap-4 xl:grid-cols-[340px_minmax(0,1fr)]" data-testid="metric-workbench-page">
					<section className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
						<div className="mb-3 text-sm font-semibold text-gray-900">已发布模型锚点</div>
						<Space direction="vertical" className="w-full" size={10}>
							<Input.Search
								value={keyword}
								onChange={(event) => setKeyword(event.target.value)}
								placeholder="搜索已发布模型"
								allowClear
							/>
							<Segmented
								block
								value={typeFilter}
								onChange={(value) => setTypeFilter(value as ModelSpecType | "ALL")}
								options={[
									{ label: "全部", value: "ALL" },
									{ label: "事实", value: "FACT" },
									{ label: "汇总", value: "SUMMARY" },
									{ label: "应用", value: "APPLICATION" },
								]}
							/>
						</Space>
						<div className="mt-4 space-y-2">
							{loading ? <Spin className="my-8 flex justify-center" /> : null}
							{!loading && filteredModels.length === 0 ? (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布的指标模型" />
							) : null}
							{filteredModels.map((model) => (
								<button
									key={model.id}
									type="button"
									onClick={() => setSelectedModelId(model.id)}
									className={`w-full rounded-md border p-3 text-left transition ${selectedModelId === model.id ? "border-blue-500 bg-blue-50" : "border-gray-200 hover:border-blue-300"}`}
								>
									<div className="font-medium text-gray-900">{model.name}</div>
									<div className="mt-2 flex flex-wrap gap-1">
										<Tag>{model.modelType}</Tag>
										<Tag color="green">已发布 v{model.revision}</Tag>
										<Tag>{model.metricRefs.length} 项指标引用</Tag>
									</div>
								</button>
							))}
						</div>
					</section>
					<section
						className="min-h-[560px] rounded-lg border border-gray-200 bg-white p-5 shadow-sm"
						data-testid="metric-workbench-main"
					>
						{selected ? (
							<>
								<div className="flex flex-wrap items-start justify-between gap-3 border-b border-gray-100 pb-4">
									<div>
										<div className="text-lg font-semibold text-gray-900">{selected.name}</div>
										<div className="mt-1 text-sm text-gray-500">
											模型 {selected.id} · 发布 revision {selected.revision}
										</div>
									</div>
									<Button onClick={() => navigate(`/modeling/models/${encodeURIComponent(selected.id)}`)}>
										查看模型详情
									</Button>
								</div>
								<div className="mt-5 grid gap-5 lg:grid-cols-2">
									<div>
										<div className="mb-3 text-sm font-semibold text-gray-900">已发布指标引用</div>
										{selected.metricRefs.length === 0 ? (
											<Alert
												type="warning"
												showIcon
												message="尚未绑定已发布指标"
												description="可以从右侧度量字段创建草稿；指标发布后再通过主动作绑定稳定版本。"
											/>
										) : (
											<div className="space-y-2">
												{selected.metricRefs.map((metric) => {
													const owner = indicatorById.get(metric.metricId);
													const ownerVersion = publishedVersion(owner?.version);
													const current = owner?.status === "PUBLISHED" && ownerVersion === metric.version;
													return (
														<div
															key={`${metric.metricId}-${metric.version}`}
															className="rounded-md border border-gray-200 p-3"
														>
															<div className="font-medium text-gray-900">{owner?.name || metric.metricId}</div>
															<div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
																引用 v{metric.version}
																<Tag color={current ? "green" : "red"}>{current ? "当前" : "已漂移"}</Tag>
															</div>
														</div>
													);
												})}
											</div>
										)}
									</div>
									<div>
										<div className="mb-3 text-sm font-semibold text-gray-900">度量字段</div>
										{selected.fields.filter((field) => field.role === "MEASURE").length === 0 ? (
											<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无度量字段" />
										) : (
											<div className="space-y-2">
												{selected.fields
													.filter((field) => field.role === "MEASURE")
													.map((field) => {
														const { binding, unit } = fieldUnit(selected, field.name);
														const currentUnit =
															unit?.status === "ACTIVE" && unit.version === binding?.measurementUnitVersion;
														return (
															<div key={field.name} className="rounded-md border border-gray-200 p-3">
																<div className="flex items-center justify-between gap-2">
																	<span className="font-medium text-gray-900">{field.name}</span>
																	<Tag>{field.dataType || "未声明类型"}</Tag>
																</div>
																<div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
																	<span>
																		计量单位：
																		{unit
																			? `${unit.name} (${unit.symbol}) v${binding?.measurementUnitVersion}`
																			: "未绑定"}
																	</span>
																	{unit ? (
																		<Tag color={currentUnit ? "green" : "red"}>{currentUnit ? "当前" : "已漂移"}</Tag>
																	) : null}
																</div>
																<div className="mt-3">
																	<Button
																		size="small"
																		type="primary"
																		disabled={!canManage}
																		onClick={() => openDraft(field)}
																	>
																		创建原子指标草稿
																	</Button>
																	{!unit ? (
																		<Button
																			size="small"
																			type="link"
																			onClick={() =>
																				navigate(`/modeling/models/${encodeURIComponent(selected.id)}?tab=standards`)
																			}
																		>
																			先绑定单位
																		</Button>
																	) : null}
																</div>
															</div>
														);
													})}
											</div>
										)}
									</div>
								</div>
							</>
						) : (
							<Empty className="mt-24" description="请选择一个已发布模型锚点" />
						)}
					</section>
				</div>
			) : null}

			<Modal
				title="创建原子指标草稿"
				open={draftOpen}
				onCancel={() => setDraftOpen(false)}
				onOk={() => void createDraft()}
				confirmLoading={saving}
				forceRender
				destroyOnClose
			>
				<Form form={form} layout="vertical" preserve={false}>
					<Form.Item name="code" label="指标编码" rules={[{ required: true }]}>
						<Input />
					</Form.Item>
					<Form.Item name="name" label="指标名称" rules={[{ required: true }]}>
						<Input />
					</Form.Item>
					<Form.Item name="definition" label="业务口径">
						<Input.TextArea rows={3} />
					</Form.Item>
					<Form.Item name="aggregationType" label="聚合方式" rules={[{ required: true }]}>
						<Select options={["SUM", "COUNT", "AVG", "MAX", "MIN"].map((value) => ({ value, label: value }))} />
					</Form.Item>
				</Form>
			</Modal>
			<Modal
				title="关联已发布指标版本"
				open={associateOpen}
				onCancel={() => setAssociateOpen(false)}
				onOk={() => void associate()}
				okButtonProps={{ disabled: !selectedIndicatorId }}
				confirmLoading={saving}
				destroyOnClose
			>
				<Alert
					className="mb-3"
					showIcon
					type="info"
					message="模型会生成一个新的已发布 revision，旧 revision 保持不可变。"
				/>
				<Select
					className="w-full"
					showSearch
					optionFilterProp="label"
					value={selectedIndicatorId || undefined}
					onChange={setSelectedIndicatorId}
					placeholder="选择指标 owner 的发布版本"
					options={indicators
						.filter((item) => item.status === "PUBLISHED" && publishedVersion(item.version))
						.map((item) => ({ value: item.id, label: `${item.name || item.code || item.id} · ${item.version}` }))}
				/>
			</Modal>
		</SemanticWorkspaceFrame>
	);
}
