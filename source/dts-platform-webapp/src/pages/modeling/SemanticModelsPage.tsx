import { useCallback, useEffect, useState } from "react";
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
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";

type ModelType = "DWS" | "ADS" | "ALL";

const REVIEW_STATUS_COLOR: Record<string, string> = {
	DRAFT: "default",
	SUBMITTED: "processing",
	APPROVED: "success",
	REJECTED: "error",
};

export default function SemanticModelsPage() {
	const navigate = useNavigate();
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [loading, setLoading] = useState(false);
	const [typeFilter, setTypeFilter] = useState<ModelType>("ALL");
	const [createOpen, setCreateOpen] = useState(false);
	const [previewData, setPreviewData] = useState<SemanticModelPreview | null>(null);
	const [form] = Form.useForm();

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const list = await listSemanticModels(typeFilter === "ALL" ? undefined : { type: typeFilter });
			setModels(Array.isArray(list) ? list : []);
		} catch {
			/* global interceptor */
		} finally {
			setLoading(false);
		}
	}, [typeFilter]);

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
			await createSemanticModel(values);
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

	const columns: ColumnsType<SemanticModel> = [
		{ title: "名称", dataIndex: "name" },
		{ title: "表名", dataIndex: "tableName", width: 160 },
		{
			title: "类型",
			dataIndex: "type",
			width: 80,
			render: (v?: string) => (
				<Tag color={v === "DWS" ? "blue" : "green"}>{v ?? "-"}</Tag>
			),
		},
		{ title: "粒度", dataIndex: "grain", width: 100 },
		{
			title: "状态",
			dataIndex: "reviewStatus",
			width: 100,
			render: (v?: string) => (
				<Tag color={REVIEW_STATUS_COLOR[v ?? ""] ?? "default"}>{v ?? "DRAFT"}</Tag>
			),
		},
		{
			title: "操作",
			key: "actions",
			width: 280,
			render: (_: unknown, row: SemanticModel) => (
				<Space size="small">
					<Button type="link" size="small" onClick={() => handlePreview(row.id)}>
						预览
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
			title="模型管理"
			description="管理 DWS/ADS 语义模型、预览、制品生成和运行触发。"
			stats={[
				{ label: "模型", value: models.length, tone: "blue" },
				{ label: "DWS", value: models.filter((item) => item.type === "DWS").length, tone: "green" },
				{ label: "ADS", value: models.filter((item) => item.type === "ADS").length, tone: "amber" },
				{ label: "待审核", value: models.filter((item) => item.reviewStatus === "SUBMITTED").length, tone: "red" },
			]}
			actions={
				<Button
					type="primary"
					data-testid="semantic-models-create"
					onClick={() => {
						form.resetFields();
						setCreateOpen(true);
					}}
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
						<Button size="small" onClick={() => navigate("/governance/standards/elements?from=model-ledger")}>
							字段标准
						</Button>
						<Button size="small" onClick={() => navigate("/modeling/semantic/objects?from=model-ledger")}>
							粒度与关系
						</Button>
						<Button size="small" onClick={() => navigate("/modeling/semantic/publish?from=model-ledger")}>
							发布审核
						</Button>
					</Space>
				</div>
				<div className="mb-3 flex flex-wrap items-center justify-between gap-3">
					<Radio.Group
						value={typeFilter}
						onChange={(e) => setTypeFilter(e.target.value as ModelType)}
						optionType="button"
						buttonStyle="solid"
						options={[
							{ label: "全部", value: "ALL" },
							{ label: "DWS", value: "DWS" },
							{ label: "ADS", value: "ADS" },
						]}
					/>
				</div>
				<CompactTable<SemanticModel>
					rowKey="id"
					columns={columns}
					dataSource={models}
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
