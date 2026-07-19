import { Alert, Button, Card, Empty, Input, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Plus, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { createModelSpec, listModelSpecs } from "@/api/modelSpecApi";
import { listWarehousePlans, type WarehousePlanHeader } from "@/api/warehousePlanApi";
import { CompactTable } from "@/components/table";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import { useSearchParams } from "@/routes/hooks";
import { useUserRoles } from "@/store/userStore";
import { ModelSpecCreateDrawer } from "./components/ModelSpecCreateDrawer";
import { dimensionCatalogEmptyText } from "./dimensionCatalogViewState";
import type { CanonicalModelSpecView, ModelSpecView } from "./modelSpecV2Contract";
import { MODEL_STATUS_LABELS } from "./modelSpecWorkbench";
import { hasWarehousePlanCreateAccess } from "./warehousePlanCreateFlow";

const { Text, Title } = Typography;

export default function DimensionCatalogPage() {
	const navigate = useNavigate();
	const searchParams = useSearchParams();
	const userRoles = useUserRoles();
	const canEdit = hasWarehousePlanCreateAccess(userRoles);
	const planId = searchParams.get("planId")?.trim() || "";
	const domainId = searchParams.get("domainId")?.trim() || "";
	const [dimensions, setDimensions] = useState<ModelSpecView[]>([]);
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const [search, setSearch] = useState("");
	const [createOpen, setCreateOpen] = useState(false);
	const { labelByKey } = useCatalogDomainOptions();

	const load = useCallback(async () => {
		setLoading(true);
		setLoadError("");
		const [modelResult, planResult] = await Promise.allSettled([
			listModelSpecs({
				modelType: "DIMENSION",
				...(planId ? { planId } : {}),
				...(domainId ? { domainId } : {}),
			}),
			listWarehousePlans(),
		]);
		if (modelResult.status === "fulfilled") {
			setDimensions(Array.isArray(modelResult.value) ? modelResult.value : []);
		} else {
			setDimensions([]);
			setLoadError("维度目录加载失败，请稍后重试");
		}
		if (planResult.status === "fulfilled") setPlans(Array.isArray(planResult.value) ? planResult.value : []);
		setLoading(false);
	}, [domainId, planId]);

	useEffect(() => {
		void load();
	}, [load]);

	const planNameById = useMemo(() => new Map(plans.map((plan) => [plan.id, plan.name])), [plans]);
	const visibleDimensions = useMemo(() => {
		const keyword = search.trim().toLowerCase();
		if (!keyword) return dimensions;
		return dimensions.filter((model) =>
			[model.name, model.description, model.grain?.statement]
				.filter(Boolean)
				.some((value) => String(value).toLowerCase().includes(keyword)),
		);
	}, [dimensions, search]);
	const canonicalDimensions = useMemo(
		() => dimensions.filter((model): model is CanonicalModelSpecView => model.compatibilityMode === "CANONICAL"),
		[dimensions],
	);
	const emptyText = dimensionCatalogEmptyText({
		totalCount: dimensions.length,
		visibleCount: visibleDimensions.length,
		search,
		canEdit,
	});

	const columns: ColumnsType<ModelSpecView> = [
		{
			title: "维度",
			dataIndex: "name",
			width: 220,
			render: (_value, model) => (
				<div>
					<div className="font-medium text-gray-900">{model.name}</div>
					<div className="text-xs text-gray-500">{model.grain?.statement || "待补每行含义"}</div>
				</div>
			),
		},
		{
			title: "维度键",
			width: 180,
			render: (_value, model) => model.grain?.keys.join("、") || "待补",
		},
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
		{ title: "分层", dataIndex: "layer", width: 80, render: (value) => <Tag>{value}</Tag> },
		{
			title: "状态",
			width: 110,
			render: (_value, model) => (
				<Tag color={model.compatibilityMode === "LEGACY_READONLY" ? "default" : "blue"}>
					{model.compatibilityMode === "LEGACY_READONLY"
						? "兼容只读"
						: MODEL_STATUS_LABELS[model.status] || model.status}
				</Tag>
			),
		},
		{
			title: "操作",
			dataIndex: "actions",
			width: 90,
			render: (_value, model) => (
				<Button type="link" size="small" onClick={() => navigate(`/modeling/models/${encodeURIComponent(model.id)}`)}>
					查看
				</Button>
			),
		},
	];

	return (
		<div className="p-4" data-testid="dimension-catalog-page">
			<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
				<div>
					<Title level={3} className="!mb-1">
						维度目录
					</Title>
					<Text type="secondary">统一登记可复用的分析角度，保存后直接形成维度表模型草稿。</Text>
				</div>
				<Button type="primary" disabled={!canEdit} onClick={() => setCreateOpen(true)}>
					<Plus size={16} />
					登记维度
				</Button>
			</div>

			{planId ? (
				<Alert
					className="mb-3"
					type="info"
					showIcon
					message="已锁定当前建设计划；登记时可在计划已确认的业务分类内选择"
				/>
			) : null}
			{!canEdit ? (
				<Alert className="mb-3" type="info" showIcon message="当前账号为只读浏览；登记维度需要计划维护权限" />
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

			{!loadError ? (
				<Card>
					<div className="mb-3 flex justify-end">
						<Input.Search
							allowClear
							className="max-w-xs"
							placeholder="搜索维度名称或每行含义"
							value={search}
							onChange={(event) => setSearch(event.target.value)}
						/>
					</div>
					<CompactTable<ModelSpecView>
						rowKey="id"
						loading={loading}
						columns={columns}
						dataSource={visibleDimensions}
						locale={{ emptyText: <Empty description={emptyText} /> }}
					/>
				</Card>
			) : null}

			<ModelSpecCreateDrawer
				open={createOpen}
				initialModelType="DIMENSION"
				lockModelType
				lockedPlanId={planId || undefined}
				initialDomainId={domainId || undefined}
				availableModels={canonicalDimensions}
				createCommand={createModelSpec}
				onClose={() => setCreateOpen(false)}
				onCreated={(model) => navigate(`/modeling/models/${encodeURIComponent(model.id)}`)}
			/>
		</div>
	);
}
