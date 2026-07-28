import { Alert, App, Button, Card, Empty, Input, Select, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PackageOpen, Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { createModelSpec, deleteModelSpec, listModelSpecs } from "@/api/modelSpecApi";
import { listWarehousePlans, type WarehousePlanHeader } from "@/api/warehousePlanApi";
import { JourneyContextBar } from "@/components/journey";
import { CompactTable } from "@/components/table";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { useSearchParams } from "@/routes/hooks";
import { useUserRoles } from "@/store/userStore";
import { ModelSpecCreateDrawer } from "./components/ModelSpecCreateDrawer";
import { ImportModelPackageWizard } from "./model-package-import/ImportModelPackageWizard";
import { buildModelPackageImportQuery } from "./model-package-import/modelPackageImportNavigation";
import { modelSpecDetailPath } from "./modelSpecDetailNavigation";
import type { ModelSpecType, ModelSpecView } from "./modelSpecV2Contract";
import { MODEL_STATUS_LABELS, MODEL_TYPE_LABELS } from "./modelSpecWorkbench";
import { hasWarehousePlanCreateAccess } from "./warehousePlanCreateFlow";
import { canEditWarehousePlanHeader } from "./warehousePlanViewModel";

const { Title } = Typography;
type ModelTypeFilter = ModelSpecType | "ALL";

const modelTypeOptions = [
	{ value: "ALL", label: "全部表类型" },
	...Object.entries(MODEL_TYPE_LABELS).map(([value, label]) => ({ value, label })),
];

const requestedModelType = (value: string | null): ModelSpecType | null =>
	value && Object.hasOwn(MODEL_TYPE_LABELS, value) ? (value as ModelSpecType) : null;

const deleteErrorMessage = (error: unknown) => {
	const candidate = error as { response?: { data?: { code?: string; message?: string } }; message?: string };
	const code = candidate.response?.data?.code;
	if (code === "MODEL_SPEC_DELETE_REFERENCED") return "该草稿已被其他活动模型引用，请先解除依赖";
	if (code === "MODEL_SPEC_DELETE_STATUS_INVALID") return "只有草稿模型可以删除";
	if (code === "MODEL_SPEC_REVISION_CONFLICT") return "模型已被其他人更新，请刷新后重试";
	if (code === "MODEL_SPEC_PLAN_FORBIDDEN") return "当前账号无权删除此建设计划中的模型";
	return candidate.response?.data?.message || candidate.message || "草稿模型删除失败，请稍后重试";
};

export default function ModelCenterPage() {
	const navigate = useNavigate();
	const { message: toast, modal } = App.useApp();
	const searchParams = useSearchParams();
	const userRoles = useUserRoles();
	const canEdit = hasWarehousePlanCreateAccess(userRoles);
	const planId = searchParams.get("planId")?.trim() || "";
	const domainId = searchParams.get("domainId")?.trim() || "";
	const compatibilityView = searchParams.get("view")?.trim() || "";
	const lightweightCreate = searchParams.get("create") === "lightweight";
	const importOpen = searchParams.get("modelImport") === "open";
	const importRunId = searchParams.get("importRunId")?.trim() || "";
	const initialType = requestedModelType(searchParams.get("modelType")) || undefined;
	const [models, setModels] = useState<ModelSpecView[]>([]);
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const [actionError, setActionError] = useState("");
	const [search, setSearch] = useState("");
	const [typeFilter, setTypeFilter] = useState<ModelTypeFilter>(
		requestedModelType(searchParams.get("modelType")) || "ALL",
	);
	const [createType, setCreateType] = useState<ModelSpecType | undefined>(initialType);
	const [createOpen, setCreateOpen] = useState(false);
	const guidedViewOpened = useRef(false);
	const { labelByKey } = useCatalogDomainOptions();

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError("");
		setActionError("");
		const modelRequest = listModelSpecs({ ...(planId ? { planId } : {}), ...(domainId ? { domainId } : {}) });
		const [modelResult, planResult] = await Promise.allSettled([modelRequest, listWarehousePlans()]);
		if (modelResult.status === "fulfilled") setModels(Array.isArray(modelResult.value) ? modelResult.value : []);
		else {
			setModels([]);
			setLoadError("模型中心加载失败，请稍后重试");
		}
		if (planResult.status === "fulfilled") setPlans(Array.isArray(planResult.value) ? planResult.value : []);
		setLoading(false);
	}, [domainId, planId]);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => {
		if ((compatibilityView === "guided" || lightweightCreate) && canEdit && !guidedViewOpened.current) {
			guidedViewOpened.current = true;
			setCreateOpen(true);
		}
	}, [canEdit, compatibilityView, lightweightCreate]);

	const planNameById = useMemo(() => new Map(plans.map((plan) => [plan.id, plan.name])), [plans]);
	const lockedImportPlan = useMemo(() => plans.find((plan) => plan.id === planId) || null, [planId, plans]);
	const canImport =
		canEdit &&
		(!planId || Boolean(lockedImportPlan && canEditWarehousePlanHeader(true, lockedImportPlan.lifecycleStatus)));
	const visibleModels = useMemo(() => {
		const keyword = search.trim().toLowerCase();
		return models.filter((model) => {
			if (model.status === "ARCHIVED") return false;
			if (typeFilter !== "ALL" && model.modelType !== typeFilter) return false;
			if (!keyword) return true;
			return [model.name, model.description, model.grain?.statement]
				.filter(Boolean)
				.some((value) => String(value).toLowerCase().includes(keyword));
		});
	}, [models, search, typeFilter]);

	const openCreate = () => {
		setCreateType(typeFilter === "ALL" ? initialType : typeFilter);
		setCreateOpen(true);
	};

	const confirmDelete = (model: ModelSpecView) => {
		modal.confirm({
			title: "删除草稿模型？",
			content: `确认删除“${model.name}”吗？删除后模型将从模型中心移除，历史版本仍保留用于审计。`,
			okText: "删除",
			cancelText: "取消",
			okButtonProps: { danger: true },
			onOk: async () => {
				setActionError("");
				try {
					await deleteModelSpec(model);
					setModels((current) => current.filter((item) => item.id !== model.id));
					toast.success("草稿模型已删除");
				} catch (error) {
					setActionError(deleteErrorMessage(error));
				}
			},
		});
	};

	const setImportRoute = (open: boolean, runId = importRunId) => {
		navigate(
			buildModelPackageImportQuery("/modeling/models", searchParams, {
				open,
				planId: planId || undefined,
				runId: runId || undefined,
			}),
			{ replace: !open },
		);
	};

	const columns: ColumnsType<ModelSpecView> = [
		{
			title: "模型",
			dataIndex: "name",
			width: 220,
			render: (_value, model) => (
				<div>
					<div className="font-medium text-gray-900">{model.name}</div>
					<div className="text-xs text-gray-500">
						r{model.revision} · {model.grain?.statement || "待补粒度"}
					</div>
				</div>
			),
		},
		{
			title: "表类型",
			dataIndex: "modelType",
			width: 100,
			render: (value: ModelSpecType) => <Tag color="blue">{MODEL_TYPE_LABELS[value]}</Tag>,
		},
		{ title: "分层", dataIndex: "layer", width: 80, render: (value) => <Tag>{value}</Tag> },
		{
			title: "所属计划 / 业务分类",
			width: 260,
			render: (_value, model) => (
				<div>
					<div>{model.planId ? planNameById.get(model.planId) || "可访问计划" : "历史模型"}</div>
					<div className="text-xs text-gray-500">
						{model.domainId ? labelByKey[model.domainId] || "已绑定业务分类" : "历史分类待迁移"}
					</div>
				</div>
			),
		},
		{
			title: "状态",
			width: 110,
			render: (_value, model) => (
				<Tag color={model.compatibilityMode === "LEGACY_READONLY" ? "default" : "processing"}>
					{model.compatibilityMode === "LEGACY_READONLY"
						? "兼容只读"
						: MODEL_STATUS_LABELS[model.status] || model.status}
				</Tag>
			),
		},
		{
			title: "操作",
			dataIndex: "actions",
			width: 150,
			render: (_value, model) => {
				const editable = canEdit && model.status === "DRAFT" && model.compatibilityMode === "CANONICAL";
				return (
					<Space size={0}>
						{editable ? (
							<Button
								type="link"
								size="small"
								icon={<Pencil size={14} />}
								onClick={() => navigate(modelSpecDetailPath(model.id, "logical", model.planId))}
							>
								编辑
							</Button>
						) : (
							<Button
								type="link"
								size="small"
								onClick={() => navigate(`/modeling/models/${encodeURIComponent(model.id)}`)}
							>
								查看
							</Button>
						)}
						{editable ? (
							<Button type="link" danger size="small" icon={<Trash2 size={14} />} onClick={() => confirmDelete(model)}>
								删除
							</Button>
						) : null}
					</Space>
				);
			},
		},
	];

	return (
		<div className="p-4" data-testid="model-center-page">
			<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
				<div>
					<Title level={3} className="!mb-1">
						模型中心
					</Title>
				</div>
				<Space wrap>
					<Button
						icon={<PackageOpen size={16} />}
						disabled={!canImport}
						title={canImport ? "导入 dbt 生成的 DTS 模型包" : "当前计划不可编辑、不可访问或账号没有计划维护权限"}
						onClick={() => setImportRoute(true)}
					>
						导入模型包
					</Button>
					<Button type="primary" disabled={!canEdit} onClick={openCreate}>
						<Plus size={16} />
						新建模型
					</Button>
				</Space>
			</div>
			<JourneyContextBar stage="modeling" />

			{compatibilityView === "guided" ? (
				<Alert
					className="mb-3"
					type="info"
					showIcon
					message="旧低代码入口已并入模型中心"
					description="请选择维度表、明细表、汇总表或应用表，按统一表单直接创建模型。"
				/>
			) : null}
			{compatibilityView === "release" ? (
				<Alert
					className="mb-3"
					type="info"
					showIcon
					message="发布审核已归入模型详情"
					description="请选择目标模型，在详情页核对实现与发布门禁。"
				/>
			) : null}

			{planId ? (
				<Alert className="mb-3" type="info" showIcon message="已锁定当前建设计划；新建模型时只需选择计划内业务分类" />
			) : null}
			{!canEdit ? (
				<Alert className="mb-3" type="info" showIcon message="当前账号为只读浏览；新建或编辑模型需要计划维护权限" />
			) : null}
			{loadError ? (
				<Alert
					className="mb-3"
					type="error"
					showIcon
					message={loadError}
					action={
						<Button size="small" onClick={() => void load()}>
							<RefreshCw size={14} />
							重试
						</Button>
					}
				/>
			) : null}
			{actionError ? <Alert className="mb-3" type="error" showIcon message={actionError} closable /> : null}

			<Card>
				<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
					<Select
						className="w-40"
						value={typeFilter}
						options={modelTypeOptions}
						onChange={(value) => setTypeFilter(value as ModelTypeFilter)}
					/>
					<Input.Search
						allowClear
						className="max-w-xs"
						placeholder="搜索模型名称或粒度"
						value={search}
						onChange={(event) => setSearch(event.target.value)}
					/>
				</div>
				<CompactTable<ModelSpecView>
					rowKey="id"
					loading={loading}
					columns={columns}
					dataSource={visibleModels}
					locale={{ emptyText: <Empty description="暂无模型" /> }}
				/>
			</Card>

			<ModelSpecCreateDrawer
				open={createOpen}
				initialModelType={createType}
				lockedPlanId={planId || undefined}
				initialDomainId={domainId || undefined}
				initialDataMartId={searchParams.get("dataMartId")?.trim() || undefined}
				initialDimensionDefinitionId={searchParams.get("dimensionDefinitionId")?.trim() || undefined}
				initialDimensionDefinitionRevision={Number(searchParams.get("dimensionDefinitionRevision")) || undefined}
				createCommand={createModelSpec}
				onClose={() => setCreateOpen(false)}
				onCreated={(model) => navigate(`/modeling/models/${encodeURIComponent(model.id)}?activeStage=logical`)}
			/>
			<ImportModelPackageWizard
				open={importOpen}
				lockedPlanId={planId || undefined}
				initialRunId={importRunId || undefined}
				plans={plans}
				canEdit={canImport}
				returnSurface="model-center"
				onClose={(runId) => setImportRoute(false, runId)}
				onRunIdChange={(runId) => setImportRoute(true, runId)}
				onApplied={() => void load()}
				onNavigate={navigate}
			/>
		</div>
	);
}
