import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Form, Input, Modal, Popconfirm, Radio, Select, Space, Tag } from "antd";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { useNavigate } from "react-router";
import {
	listSemanticModels,
	createSemanticModel,
	previewSemanticModelData,
	generateSemanticModelArtifacts,
	triggerSemanticModelRun,
	submitSemanticModelReview,
	type SemanticModel,
	type SemanticModelPreview,
} from "@/api/semanticModelingApi";
import { getModelSpecReleaseGate, listModelingModelSpecs, type ModelingReleaseGate } from "@/api/modelingApi";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";
import { useSearchParams } from "@/routes/hooks";
import { resolveWarehousePlanningContext } from "../governance/warehousePlanningContext";
import { DEFAULT_WAREHOUSE_LAYER_SCHEME } from "../governance/warehouseLayerRegistry";
import { buildBusinessModelingRoute, resolveBusinessModelingContext } from "./businessModelingContext";
import { buildModelLedgerRows, toLegacySemanticModel, type ModelLedgerRow } from "./modelingLedger";
import type { ModelingLayer } from "./modelingVnextContract";

type ModelType = ModelingLayer | "ALL";

const REVIEW_STATUS_COLOR: Record<string, string> = {
	DRAFT: "default",
	SUBMITTED: "processing",
	APPROVED: "success",
	REJECTED: "error",
};

export default function SemanticModelsPage() {
	const navigate = useNavigate();
	const searchParams = useSearchParams();
	const planningResolution = useMemo(() => resolveWarehousePlanningContext(searchParams), [searchParams]);
	const enabledModelLayers = useMemo(() => {
		const enabled = planningResolution.context?.enabledLayers ?? DEFAULT_WAREHOUSE_LAYER_SCHEME.enabledLayers;
		const mapped = enabled.map((layer) => (layer === "ODS_RAW" || layer === "ODS_STANDARDIZED" ? "ODS" : layer));
		return [...new Set(mapped)].filter((layer): layer is ModelingLayer => ["ODS", "STG", "DWD", "DWS", "ADS"].includes(layer));
	}, [planningResolution.context]);
	const context = useMemo(
		() => resolveBusinessModelingContext(searchParams, planningResolution.context),
		[planningResolution.context, searchParams],
	);
	const processId = context.processId;
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [loading, setLoading] = useState(false);
	const [typeFilter, setTypeFilter] = useState<ModelType>("ALL");
	const [showStg, setShowStg] = useState(false);
	const [createOpen, setCreateOpen] = useState(false);
	const [previewData, setPreviewData] = useState<SemanticModelPreview | null>(null);
	const [releaseGates, setReleaseGates] = useState<Record<string, ModelingReleaseGate>>({});
	const [form] = Form.useForm();
	const ledgerRows = useMemo(() => buildModelLedgerRows(models), [models]);
	const visibleLedgerRows = useMemo(
		() => (showStg || typeFilter === "STG" ? ledgerRows : ledgerRows.filter((row) => row.layer !== "STG")),
		[ledgerRows, showStg, typeFilter],
	);
	const layerFilterOptions = useMemo(
		() => [
			{ label: "全部", value: "ALL" },
			...enabledModelLayers.map((layer) => ({ label: layer, value: layer })),
		],
		[enabledModelLayers],
	);

	const load = useCallback(async () => {
		setLoading(true);
		if (!processId) {
			setModels([]);
			setReleaseGates({});
			setLoading(false);
			return;
		}
		try {
			try {
				const vnext = await listModelingModelSpecs({ processId, layer: typeFilter === "ALL" ? undefined : typeFilter });
				if (Array.isArray(vnext) && vnext.length > 0) {
					const mapped = vnext.map(toLegacySemanticModel);
					setModels(mapped);
					const gateEntries = await Promise.all(
						mapped.map(async (model) => {
							try {
								return [model.id, await getModelSpecReleaseGate(model.id)] as const;
							} catch {
								return null;
							}
						}),
					);
					setReleaseGates(Object.fromEntries(gateEntries.filter((entry): entry is readonly [string, ModelingReleaseGate] => entry !== null)));
					return;
				}
			} catch {
				// Keep the legacy read path available while a tenant is migrating.
			}
				const list = await listSemanticModels({
					processId,
					...(typeFilter === "DWS" || typeFilter === "ADS" ? { type: typeFilter } : {}),
				});
				setReleaseGates({});
				const safeList = Array.isArray(list) ? list : [];
				setModels(typeFilter === "ALL" || typeFilter === "DWS" || typeFilter === "ADS" ? safeList : safeList.filter((item) => item.type === typeFilter));
		} catch {
			/* global interceptor */
		} finally {
			setLoading(false);
		}
	}, [processId, typeFilter]);

	useEffect(() => {
		void load();
	}, [load]);

	const handlePreview = async (modelId: string) => {
		try {
			const data = await previewSemanticModelData(modelId, 50);
			setPreviewData(data);
		} catch {
			/* global interceptor */
		}
	};

	const handleGenerate = async (modelId: string) => {
		try {
			const result = await generateSemanticModelArtifacts(modelId);
			const paths = result?.artifacts?.map((a) => a.path).filter(Boolean) ?? [];
			toast.success(`已生成 ${paths.length} 个制品: ${paths.join(", ")}`);
		} catch {
			/* global interceptor */
		}
	};

	const handleTrigger = async (modelId: string) => {
		try {
			await triggerSemanticModelRun(modelId);
			toast.success("运行已触发");
		} catch {
			/* global interceptor */
		}
	};

	const handleSubmitReview = async (modelId: string) => {
		try {
			await submitSemanticModelReview(modelId);
			toast.success("已提交审核");
			void load();
		} catch {
			/* global interceptor */
		}
	};

	const handleCreate = async () => {
		try {
			const values = await form.validateFields();
			await createSemanticModel({ ...values, processId });
			toast.success("模型已创建");
			setCreateOpen(false);
			void load();
		} catch (err: unknown) {
			if (err && typeof err === "object" && "errorFields" in err) return;
		}
	};

	const previewColumns =
		previewData?.headers?.map((h: string) => ({
			title: h,
			dataIndex: h,
			key: h,
			ellipsis: true,
			width: 120,
		})) ?? [];

	const columns: ColumnsType<ModelLedgerRow> = [
		{ title: "模型名称", dataIndex: "name" },
		{ title: "产物 / 表名", dataIndex: "artifactLabel", width: 180 },
		{
			title: "分层",
			dataIndex: "layer",
			width: 80,
			render: (v?: string) => (
				<Tag color={v === "DWD" ? "purple" : v === "DWS" ? "blue" : v === "ADS" ? "green" : v === "STG" ? "orange" : "default"}>{v ?? "-"}</Tag>
			),
		},
		{ title: "粒度", dataIndex: "grain", width: 100 },
		{
			title: "实现模式",
			dataIndex: "implementationMode",
			width: 120,
			render: (v?: string) => v === "DBT_MANAGED" ? "dbt 原生" : v === "LEGACY_READONLY" ? "兼容只读" : "设计器生成",
		},
		{
			title: "审核 / 运行",
			dataIndex: "reviewStatus",
			width: 100,
			render: (v?: string) => (
				<Tag color={REVIEW_STATUS_COLOR[v ?? ""] ?? "default"}>{v ?? "DRAFT"}</Tag>
			),
		},
		{
			title: "发布门禁",
			key: "releaseGate",
			width: 110,
			render: (_: unknown, row: ModelLedgerRow) => {
				const gate = releaseGates[row.id];
				if (!gate) return <span style={{ color: "#aaa" }}>未接入</span>;
				return (
					<Tag color={gate.publishable ? "success" : "error"} title={gate.blockers.join("、") || "parse/test/drift 均通过"}>
						{gate.publishable ? "可发布" : `阻断 ${gate.blockers.length}`}
					</Tag>
				);
			},
		},
		{ title: "下一步", dataIndex: "nextAction", width: 120 },
		{
			title: "操作",
			key: "actions",
			width: 280,
			render: (_: unknown, row: ModelLedgerRow) => (
				<Space size="small">
					<Button type="link" size="small" onClick={() => handlePreview(row.id)}>
						预览
					</Button>
					<Button
						type="link"
						size="small"
						data-testid="semantic-model-dbt-entry"
						onClick={() => navigate(`/modeling/dbt-files?modelId=${encodeURIComponent(row.id)}&from=model-ledger`)}
					>
						高级 dbt SQL
					</Button>
					<Button type="link" size="small" onClick={() => handleGenerate(row.id)}>
						生成制品
					</Button>
					<Button type="link" size="small" onClick={() => handleTrigger(row.id)}>
						触发运行
					</Button>
					<Popconfirm title="确认提交审核？" onConfirm={() => handleSubmitReview(row.id)}>
						<Button type="link" size="small">
							提交审核
						</Button>
					</Popconfirm>
				</Space>
			),
		},
	];

	return (
		<SemanticWorkspaceFrame
			activeKey="models"
			title="模型台账"
			description="统一登记 ODS、STG、DWD、DWS、ADS 模型、实现所有权、dbt 产物和运行下一步。"
			context={context}
			stats={[
				{ label: "模型", value: models.length, tone: "blue" },
				{ label: "ODS", value: models.filter((item) => item.type === "ODS").length, tone: "gray" },
				{ label: "DWD", value: models.filter((item) => item.type === "DWD").length, tone: "gray" },
				{ label: "DWS", value: models.filter((item) => item.type === "DWS").length, tone: "green" },
				{ label: "ADS", value: models.filter((item) => item.type === "ADS").length, tone: "amber" },
				{ label: "STG", value: models.filter((item) => item.type === "STG").length, tone: "amber" },
				{ label: "待审核", value: models.filter((item) => item.reviewStatus === "SUBMITTED").length, tone: "red" },
			]}
			actions={
				<Button
					type="primary"
					data-testid="semantic-models-create"
					onClick={() => {
						form.resetFields();
						form.setFieldsValue({ processId });
						setCreateOpen(true);
					}}
					disabled={!processId}
				>
					<Plus size={16} />
					新建模型
				</Button>
			}
		>
			<div className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm" data-testid="semantic-models-page">
				<div
					className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-md border border-blue-100 bg-blue-50/40 px-3 py-2"
					data-testid="semantic-model-ledger-links"
				>
					<div>
						<div className="text-sm font-medium text-gray-900">模型台账 · 专业入口</div>
						<div className="mt-1 text-xs text-gray-500">
							台账记录模型归属、粒度和状态；字段标准、粒度关系与发布门禁在专业页面维护。
						</div>
					</div>
					<Space size="small" wrap>
						<Button size="small" onClick={() => navigate(buildBusinessModelingRoute("/governance/standards/elements?from=model-ledger", context))}>
							字段标准
						</Button>
						<Button size="small" onClick={() => navigate(buildBusinessModelingRoute("/modeling/semantic/objects?from=model-ledger", context))}>
							粒度与关系
						</Button>
						<Button size="small" onClick={() => navigate(buildBusinessModelingRoute("/modeling/semantic/publish?from=model-ledger", context))}>
							发布审核
						</Button>
					</Space>
				</div>
				<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
					<Space wrap>
					<Radio.Group
						data-testid="modeling-layer-filter"
						value={typeFilter}
						onChange={(e) => setTypeFilter(e.target.value as ModelType)}
						optionType="button"
						buttonStyle="solid"
					options={layerFilterOptions}
					/>
					<Button size="small" onClick={() => setShowStg((value) => !value)}>
						{showStg ? "折叠 STG" : "展开 STG"}
					</Button>
					</Space>
				</div>
				<CompactTable<ModelLedgerRow>
					rowKey="id"
					columns={columns}
					dataSource={visibleLedgerRows}
					loading={loading}
				/>
			</div>

			{/* 数据预览 Modal */}
			<Modal
				title="数据预览"
				open={previewData !== null}
				onCancel={() => setPreviewData(null)}
				footer={null}
				width={800}
			>
				{previewData?.success ? (
					<CompactTable
						rowKey={(_, i) => String(i)}
						columns={previewColumns}
						dataSource={previewData.rows ?? []}
						pagination={false}
						scroll={{ x: true }}
					/>
				) : (
					<p className="text-red-500">{previewData?.errorMessage ?? "预览失败"}</p>
				)}
			</Modal>

			{/* 新建模型 Modal */}
			<Modal
				title="新建模型"
				open={createOpen}
				onCancel={() => setCreateOpen(false)}
				onOk={handleCreate}
				destroyOnClose
			>
				<Form form={form} layout="vertical" className="pt-4">
					<Form.Item name="processId" hidden>
						<Input />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true }]}>
						<Input placeholder="月销售汇总" />
					</Form.Item>
					<Form.Item name="tableName" label="表名" rules={[{ required: true }]}>
						<Input placeholder="dws_sales_monthly" />
					</Form.Item>
					<Form.Item name="type" label="类型" initialValue="DWS">
						<Radio.Group
							options={[
								{ label: "DWS", value: "DWS" },
								{ label: "ADS", value: "ADS" },
							]}
						/>
					</Form.Item>
					<Form.Item name="grain" label="粒度">
						<Input placeholder="DAY / MONTH" />
					</Form.Item>
					<Form.Item name="materialization" label="物化方式">
						<Select
							options={[
								{ label: "table", value: "table" },
								{ label: "incremental", value: "incremental" },
								{ label: "view", value: "view" },
							]}
						/>
					</Form.Item>
				</Form>
			</Modal>
		</SemanticWorkspaceFrame>
	);
}
