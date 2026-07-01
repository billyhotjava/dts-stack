import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Drawer, Input, Space, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import {
	listSemanticModels,
	approveSemanticModelReview,
	rejectSemanticModelReview,
	publishSemanticModelToDbt,
	registerSemanticBiDataset,
	registerSemanticLineage,
	listSemanticModelReviewLogs,
	listSemanticGeneratedArtifacts,
	type SemanticModel,
	type SemanticModelReviewLog,
	type SemanticGeneratedArtifact,
} from "@/api/semanticModelingApi";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";

const REVIEW_STATUS_COLOR: Record<string, string> = {
	DRAFT: "default", SUBMITTED: "processing", APPROVED: "success", REJECTED: "error",
};

type PublishNotice = {
	type: "success" | "warning" | "error";
	message: string;
	description?: string;
};

export default function SemanticPublishPage() {
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [loading, setLoading] = useState(false);
	const [publishing, setPublishing] = useState<string | null>(null);
	const [publishNotice, setPublishNotice] = useState<PublishNotice | null>(null);
	const [logDrawerModel, setLogDrawerModel] = useState<string | null>(null);
	const [logs, setLogs] = useState<SemanticModelReviewLog[]>([]);
	const [artifactDrawerModel, setArtifactDrawerModel] = useState<string | null>(null);
	const [artifacts, setArtifacts] = useState<SemanticGeneratedArtifact[]>([]);
	const [rejectComment, setRejectComment] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const list = await listSemanticModels({ type: "ADS" });
			setModels(Array.isArray(list) ? (list as SemanticModel[]) : []);
		} catch {
			/* global interceptor */
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => { void load(); }, [load]);

	const handleApprove = async (modelId: string) => {
		try {
			await approveSemanticModelReview(modelId);
			toast.success("审核已通过");
			void load();
		} catch {
			/* global interceptor */
		}
	};

	const handleReject = async (modelId: string) => {
		if (!rejectComment.trim()) { toast.error("拒绝原因不能为空"); return; }
		try {
			await rejectSemanticModelReview(modelId, rejectComment);
			toast.success("审核已拒绝");
			setRejectComment("");
			void load();
		} catch {
			/* global interceptor */
		}
	};

	const handlePublish = async (modelId: string) => {
		setPublishing(modelId);
		setPublishNotice(null);
		try {
			await publishSemanticModelToDbt(modelId);
		} catch {
			setPublishNotice({
				type: "error",
				message: "dbt 发布失败",
				description: "发布未完成，请修复模型制品或任务配置后重试。",
			});
			setPublishing(null);
			return;
		}
		try {
			await registerSemanticBiDataset(modelId);
		} catch {
			setPublishNotice({
				type: "warning",
				message: "dbt 已发布，BI 数据集注册失败",
				description: "模型制品已落地，但消费侧数据集还不可用。请检查 BI 注册配置后重新点击发布。",
			});
			toast.error("dbt 已发布，BI 数据集注册失败，请重试");
			setPublishing(null);
			return;
		}
		try {
			await registerSemanticLineage(modelId);
			setPublishNotice({
				type: "success",
				message: "发布成功",
				description: "dbt 制品、BI 数据集和血缘已全部注册。",
			});
			toast.success("发布成功：dbt 发布 + BI 数据集 + 血缘已注册");
			void load();
		} catch {
			setPublishNotice({
				type: "warning",
				message: "dbt 与 BI 数据集已完成，血缘注册失败",
				description: "消费侧数据集已可用，但血缘视图暂不完整。请检查血缘注册配置后重新点击发布。",
			});
			toast.error("dbt 与 BI 数据集已完成，血缘注册失败，请重试");
		} finally {
			setPublishing(null);
		}
	};

	const openLogs = async (modelId: string) => {
		setLogDrawerModel(modelId);
		try {
			const list = await listSemanticModelReviewLogs(modelId);
			setLogs(Array.isArray(list) ? (list as SemanticModelReviewLog[]) : []);
		} catch {
			setLogs([]);
		}
	};

	const openArtifacts = async (modelId: string) => {
		setArtifactDrawerModel(modelId);
		try {
			const list = await listSemanticGeneratedArtifacts({ modelId });
			setArtifacts(Array.isArray(list) ? (list as SemanticGeneratedArtifact[]) : []);
		} catch {
			setArtifacts([]);
		}
	};

	const columns: ColumnsType<SemanticModel> = [
		{ title: "名称", dataIndex: "name" },
		{ title: "表名", dataIndex: "tableName", width: 180, ellipsis: true },
		{
			title: "审核状态", dataIndex: "reviewStatus", width: 120,
			render: (v?: string) => <Tag color={REVIEW_STATUS_COLOR[v ?? ""] ?? "default"}>{v ?? "DRAFT"}</Tag>,
		},
		{ title: "提交人", dataIndex: "submittedBy", width: 100 },
		{
			title: "操作", key: "actions", width: 340,
			render: (_: unknown, row: SemanticModel) => (
				<Space size="small" wrap>
					<Button type="link" size="small" onClick={() => handleApprove(row.id)}>通过</Button>
					<Button type="link" size="small" danger onClick={() => handleReject(row.id)}>拒绝</Button>
					<Button
						type="link" size="small"
						loading={publishing === row.id}
						onClick={() => handlePublish(row.id)}
					>
						发布 dbt
					</Button>
					<Button type="link" size="small" onClick={() => openLogs(row.id)}>审核日志</Button>
					<Button type="link" size="small" onClick={() => openArtifacts(row.id)}>查看制品</Button>
				</Space>
			),
		},
	];

	return (
			<SemanticWorkspaceFrame
				activeKey="publish"
				title="发布审核"
				description="审核 ADS 语义模型并执行发布操作。"
			stats={[
				{ label: "ADS 模型", value: models.length, tone: "blue" },
				{ label: "待审核", value: models.filter((item) => item.reviewStatus === "SUBMITTED").length, tone: "amber" },
				{ label: "已通过", value: models.filter((item) => item.reviewStatus === "APPROVED").length, tone: "green" },
				{ label: "已拒绝", value: models.filter((item) => item.reviewStatus === "REJECTED").length, tone: "red" },
			]}
		>
			<div className="space-y-4" data-testid="semantic-publish-page">
			{publishing && (
				<div style={{ color: "hsl(220,80%,55%)", padding: "4px 0", fontSize: 13 }}>
					正在发布 dbt 并注册血缘，请稍候...
				</div>
			)}
			{publishNotice && (
				<Alert
					type={publishNotice.type}
					message={publishNotice.message}
					description={publishNotice.description}
					showIcon
					closable
					onClose={() => setPublishNotice(null)}
				/>
			)}
			<div className="flex gap-2 items-center">
				<span className="text-sm text-gray-500">拒绝原因:</span>
				<Input
					style={{ width: 240 }}
					size="small"
					value={rejectComment}
					onChange={(e) => setRejectComment(e.target.value)}
					placeholder="填写后点击拒绝"
				/>
			</div>
			<div className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
				<CompactTable<SemanticModel>
					rowKey="id"
					columns={columns}
					dataSource={models}
					loading={loading}
				/>
			</div>
			<Drawer
				title="审核日志"
				open={logDrawerModel !== null}
				onClose={() => setLogDrawerModel(null)}
			>
				{logs.length === 0 ? (
					<p className="text-gray-400 text-sm">暂无审核记录</p>
				) : (
					<div className="space-y-2">
						{logs.map((log) => (
							<div key={log.id} className="text-sm border-b border-gray-100 pb-2">
								<span className="font-medium">{log.action}</span>
								<span className="text-gray-500 ml-2">{log.actor}</span>
								{log.comment && <p className="text-gray-600 mt-1">{log.comment}</p>}
								<p className="text-gray-400 text-xs">{log.createdDate}</p>
							</div>
						))}
					</div>
				)}
			</Drawer>
			<Drawer
				title="生成制品"
				open={artifactDrawerModel !== null}
				onClose={() => setArtifactDrawerModel(null)}
			>
				{artifacts.map((a) => (
					<div key={a.id} className="mb-3">
						<div className="text-xs text-gray-500 mb-1">{a.path}</div>
						<pre
							style={{
								background: "#f5f5f5",
								borderRadius: 4,
								padding: "8px",
								fontSize: 11,
								overflow: "auto",
								maxHeight: 200,
							}}
						>
							{a.content ?? "（无内容）"}
						</pre>
					</div>
				))}
			</Drawer>
			</div>
		</SemanticWorkspaceFrame>
	);
}
