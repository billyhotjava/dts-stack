import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Empty, Input, Modal, Row, Select, Space, Tag, Typography, message } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import {
	BranchesOutlined,
	CheckCircleOutlined,
	CloudUploadOutlined,
	DatabaseOutlined,
	SendOutlined,
	StopOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import {
	approveSemanticModelReview,
	listSemanticGeneratedArtifacts,
	listSemanticModelReviewLogs,
	listSemanticModels,
	publishSemanticModelToDbt,
	registerSemanticBiDataset,
	registerSemanticLineage,
	rejectSemanticModelReview,
	submitSemanticModelReview,
	type SemanticGeneratedArtifact,
	type SemanticModel,
	type SemanticModelReviewLog,
} from "@/api/semanticModelingApi";
import { SemanticSectionNav } from "./SemanticSectionNav";
import { asArray, isConsumableSemanticModel, semanticSectionMeta } from "./semanticModelingShared";

const { Paragraph, Text } = Typography;

const backendGaps = ["Superset 远端 Dataset 同步"];

const reviewStatusColor = (status?: string) => {
	const value = (status || "DRAFT").toUpperCase();
	if (value === "APPROVED" || value === "PUBLISHED") return "green";
	if (value === "IN_REVIEW") return "blue";
	if (value === "REJECTED") return "red";
	return "default";
};

export default function SemanticPublishPage() {
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [artifacts, setArtifacts] = useState<SemanticGeneratedArtifact[]>([]);
	const [reviewLogs, setReviewLogs] = useState<SemanticModelReviewLog[]>([]);
	const [selectedModelId, setSelectedModelId] = useState<string>();
	const [loading, setLoading] = useState(false);
	const [artifactLoading, setArtifactLoading] = useState(false);
	const [reviewLoading, setReviewLoading] = useState(false);
	const [reviewAction, setReviewAction] = useState<"submit" | "approve" | "reject" | null>(null);
	const [reviewComment, setReviewComment] = useState("");

	const selectedModel = useMemo(() => models.find((item) => item.id === selectedModelId), [models, selectedModelId]);
	const consumableModels = useMemo(() => models.filter(isConsumableSemanticModel), [models]);

	const selectedModelReviewStatus = (selectedModel?.reviewStatus || selectedModel?.status || "DRAFT").toUpperCase();
	const selectedModelStatus = (selectedModel?.status || "").toUpperCase();
	const selectedModelIsConsumable = Boolean(selectedModel && isConsumableSemanticModel(selectedModel));
	const canSubmitReview = Boolean(
		selectedModelId && !["IN_REVIEW", "APPROVED", "PUBLISHED"].includes(selectedModelReviewStatus),
	);
	const canApproveReview = Boolean(selectedModelId && selectedModelReviewStatus === "IN_REVIEW");
	const canRejectReview = canApproveReview;
	const canPublishModel = Boolean(
		selectedModelId && selectedModelIsConsumable && ["APPROVED", "PUBLISHED"].includes(selectedModelReviewStatus),
	);
	const publishedDbtCount = artifacts.filter(
		(item) =>
			["DBT_SQL", "DBT_SCHEMA_YML"].includes(String(item.artifactType || "").toUpperCase()) &&
			String(item.status || "").toUpperCase() === "PUBLISHED",
	).length;
	const dbtPublished = selectedModelStatus === "PUBLISHED" && publishedDbtCount >= 2;
	const biDatasetRegistered = artifacts.some(
		(item) =>
			String(item.artifactType || "").toUpperCase() === "BI_DATASET" &&
			String(item.status || "").toUpperCase() === "REGISTERED",
	);
	const lineageRegistered = artifacts.some(
		(item) =>
			String(item.artifactType || "").toUpperCase() === "LINEAGE" &&
			String(item.status || "").toUpperCase() === "REGISTERED",
	);
	const canRegisterBiDataset = Boolean(canPublishModel && dbtPublished);
	const canRegisterLineage = Boolean(canRegisterBiDataset && biDatasetRegistered);
	const publishNextAction = !selectedModelId
		? "先选择 DWS/ADS 模型"
		: !selectedModelIsConsumable
			? "请选择 DWS 或 ADS 模型"
			: !["APPROVED", "PUBLISHED"].includes(selectedModelReviewStatus)
				? "先提交并审核通过"
				: !dbtPublished
					? "发布 dbt 产物"
					: !biDatasetRegistered
						? "注册 BI 数据集"
						: !lineageRegistered
							? "写入血缘"
							: "发布闭环已完成";

	const loadModels = () => {
		setLoading(true);
		listSemanticModels()
			.then((resp) => setModels(asArray<SemanticModel>(resp)))
			.catch(() => setModels([]))
			.finally(() => setLoading(false));
	};

	const loadModelArtifacts = (modelId: string) => {
		setArtifactLoading(true);
		Promise.allSettled([listSemanticGeneratedArtifacts({ modelId }), listSemanticModelReviewLogs(modelId)])
			.then((results) => {
				const artifactsResp = results[0].status === "fulfilled" ? results[0].value : [];
				const logsResp = results[1].status === "fulfilled" ? results[1].value : [];
				setArtifacts(asArray<SemanticGeneratedArtifact>(artifactsResp));
				setReviewLogs(asArray<SemanticModelReviewLog>(logsResp));
			})
			.finally(() => setArtifactLoading(false));
	};

	useEffect(() => {
		loadModels();
	}, []);

	useEffect(() => {
		if (!selectedModelId) {
			setArtifacts([]);
			setReviewLogs([]);
			return;
		}
		loadModelArtifacts(selectedModelId);
	}, [selectedModelId]);

	const openReviewDialog = (action: "submit" | "approve" | "reject") => {
		setReviewAction(action);
		setReviewComment("");
	};

	const closeReviewDialog = () => {
		setReviewAction(null);
		setReviewComment("");
	};

	const submitReviewAction = async () => {
		if (!selectedModelId || !reviewAction) return;
		if (reviewAction === "reject" && !reviewComment.trim()) {
			message.warning("请输入驳回原因");
			return;
		}
		setReviewLoading(true);
		try {
			if (reviewAction === "submit") await submitSemanticModelReview(selectedModelId, reviewComment);
			if (reviewAction === "approve") await approveSemanticModelReview(selectedModelId, reviewComment);
			if (reviewAction === "reject") await rejectSemanticModelReview(selectedModelId, reviewComment);
			message.success({ submit: "已提交审核", approve: "已审核通过", reject: "已驳回" }[reviewAction]);
			closeReviewDialog();
			loadModels();
			loadModelArtifacts(selectedModelId);
		} finally {
			setReviewLoading(false);
		}
	};

	const publishArtifacts = async () => {
		if (!selectedModelId || !selectedModelIsConsumable) {
			message.warning("请选择 DWS 公共汇总模型或 ADS 应用数据集");
			return;
		}
		setArtifactLoading(true);
		try {
			const result = await publishSemanticModelToDbt(selectedModelId);
			message.success(`已发布 ${((result as any)?.publishedPaths || []).length} 个 dbt 文件`);
			loadModels();
			loadModelArtifacts(selectedModelId);
		} finally {
			setArtifactLoading(false);
		}
	};

	const registerBiDataset = async () => {
		if (!selectedModelId || !selectedModelIsConsumable) {
			message.warning("请选择 DWS 公共汇总模型或 ADS 应用数据集");
			return;
		}
		if (!canRegisterBiDataset) {
			message.warning("请先发布 dbt 产物，再注册 BI 数据集");
			return;
		}
		setArtifactLoading(true);
		try {
			const result = await registerSemanticBiDataset(selectedModelId);
			message.success(`已注册 BI 数据集：${(result as any)?.datasetName || ""}`);
			loadModelArtifacts(selectedModelId);
		} finally {
			setArtifactLoading(false);
		}
	};

	const registerLineage = async () => {
		if (!selectedModelId || !selectedModelIsConsumable) {
			message.warning("请选择 DWS 公共汇总模型或 ADS 应用数据集");
			return;
		}
		if (!canRegisterLineage) {
			message.warning("请先发布 dbt 并注册 BI 数据集，再写入血缘");
			return;
		}
		setArtifactLoading(true);
		try {
			await registerSemanticLineage(selectedModelId);
			message.success("已写入目录血缘");
			loadModelArtifacts(selectedModelId);
		} finally {
			setArtifactLoading(false);
		}
	};

	const modelColumns: ColumnsType<SemanticModel> = [
		{ title: "模型", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "类型", dataIndex: "type", width: 90, render: (value) => value || "-" },
		{ title: "表名", dataIndex: "tableName", render: (value) => value || "-" , sorter: (a, b) => (a.tableName || "").localeCompare(b.tableName || "") },
		{
			title: "审核",
			dataIndex: "reviewStatus",
			width: 120,
			render: (value, row) => (
				<Tag color={reviewStatusColor(value || row.status)}>{value || row.status || "DRAFT"}</Tag>
			),
		},
		{ title: "发布", dataIndex: "status", width: 110, render: (value) => value || "-" },
	];

	const artifactColumns: ColumnsType<SemanticGeneratedArtifact> = [
		{ title: "生成物", dataIndex: "artifactType", width: 120, render: (value) => value || "-" },
		{ title: "路径", dataIndex: "path", render: (value) => value || "-" },
		{ title: "状态", dataIndex: "status", width: 110, render: (value) => value || "-" },
	];

	return (
		<div className="space-y-5 p-5" data-testid="semantic-publish-page">
			<PageHeader
				title={semanticSectionMeta.publish.title}
				actions={
					<Space wrap>
						<Button
							icon={<SendOutlined />}
							onClick={() => openReviewDialog("submit")}
							loading={reviewLoading}
							disabled={!canSubmitReview}
						>
							提交审核
						</Button>
						<Button
							type="primary"
							icon={<CheckCircleOutlined />}
							onClick={() => openReviewDialog("approve")}
							loading={reviewLoading}
							disabled={!canApproveReview}
						>
							审核通过
						</Button>
						<Button
							danger
							icon={<StopOutlined />}
							onClick={() => openReviewDialog("reject")}
							loading={reviewLoading}
							disabled={!canRejectReview}
						>
							驳回
						</Button>
						<Button
							type="primary"
							icon={<CloudUploadOutlined />}
							onClick={publishArtifacts}
							loading={artifactLoading}
							disabled={!canPublishModel}
						>
							发布 dbt
						</Button>
						<Button
							icon={<DatabaseOutlined />}
							onClick={registerBiDataset}
							loading={artifactLoading}
							disabled={!canRegisterBiDataset}
						>
							注册 BI
						</Button>
						<Button
							icon={<BranchesOutlined />}
							onClick={registerLineage}
							loading={artifactLoading}
							disabled={!canRegisterLineage}
						>
							写血缘
						</Button>
					</Space>
				}
			/>

			<SemanticSectionNav activeSection="publish" />

			<Alert
				type="info"
				showIcon
				message="发布边界"
				description="这里处理工程师审核、dbt 发布、BI 数据集注册和血缘写入。业务人员的指标拖拽和 DWS/ADS 结构设计在前序页面完成。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} xl={15}>
					<Card title="模型审核与发布">
						<Space direction="vertical" className="w-full">
							<Select
								allowClear
								showSearch
								placeholder="选择模型"
								className="w-full"
								value={selectedModelId}
								optionFilterProp="label"
								options={consumableModels.map((item) => ({
									label: `${item.name}${item.tableName ? ` / ${item.tableName}` : ""}`,
									value: item.id,
								}))}
								onChange={setSelectedModelId}
							/>
							<CompactTable<SemanticModel>
								rowKey="id"
								size="small"
								pagination={{ pageSize: 6 }}
								loading={loading}
								rowSelection={{
									type: "radio",
									selectedRowKeys: selectedModelId ? [selectedModelId] : [],
									onChange: (keys) => setSelectedModelId(String(keys[0] || "")),
								}}
								columns={modelColumns}
								dataSource={consumableModels}
							/>
							{selectedModel ? (
								<Card size="small" title="审核记录">
									<Space direction="vertical" className="w-full">
										<Space wrap>
											<Tag color={reviewStatusColor(selectedModel.reviewStatus || selectedModel.status)}>
												{selectedModel.reviewStatus || selectedModel.status || "DRAFT"}
											</Tag>
											<Text type="secondary">
												提交：{selectedModel.submittedBy || "-"} {selectedModel.submittedAt || ""}
											</Text>
											<Text type="secondary">
												审核：{selectedModel.reviewedBy || "-"} {selectedModel.reviewedAt || ""}
											</Text>
										</Space>
										{selectedModel.reviewComment ? <Text>意见：{selectedModel.reviewComment}</Text> : null}
										<CompactTable<SemanticModelReviewLog>
											rowKey="id"
											size="small"
											pagination={false}
											columns={[
												{ title: "动作", dataIndex: "action", width: 100 },
												{ title: "人员", dataIndex: "actor", width: 140, render: (value) => value || "-" },
												{ title: "意见", dataIndex: "comment", render: (value) => value || "-" },
												{ title: "时间", dataIndex: "createdDate", width: 190, render: (value) => value || "-" , sorter: (a, b) => { const ta = a.createdDate ? new Date(a.createdDate as any).getTime() : 0; const tb = b.createdDate ? new Date(b.createdDate as any).getTime() : 0; return ta - tb; } },
											]}
											dataSource={reviewLogs}
										/>
									</Space>
								</Card>
							) : null}
							<CompactTable<SemanticGeneratedArtifact>
								rowKey="id"
								size="small"
								pagination={false}
								loading={artifactLoading}
								columns={artifactColumns}
								dataSource={artifacts}
							/>
						</Space>
					</Card>
				</Col>
				<Col xs={24} xl={9}>
					<Card title="发布闭环检查">
						<Space direction="vertical" className="w-full">
							<Space wrap>
								<Tag color={["APPROVED", "PUBLISHED"].includes(selectedModelReviewStatus) ? "green" : "default"}>
									审核
								</Tag>
								<Tag color={dbtPublished ? "green" : "default"}>dbt {publishedDbtCount}/2</Tag>
								<Tag color={biDatasetRegistered ? "green" : "default"}>BI</Tag>
								<Tag color={lineageRegistered ? "green" : "default"}>血缘</Tag>
							</Space>
							<Alert
								type={publishNextAction === "发布闭环已完成" ? "success" : "info"}
								showIcon
								message={publishNextAction}
							/>
						</Space>
					</Card>
					<Card title="后续 API 缺口">
						<Paragraph type="secondary">
							基础发布接口已接入真实数据；下面能力补齐后，BI 注册可以继续串到远端平台。
						</Paragraph>
						<Space wrap>
							{backendGaps.map((item) => (
								<Tag key={item}>{item}</Tag>
							))}
						</Space>
					</Card>
					{!selectedModelId ? (
						<Card className="mt-4">
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择模型后审核发布" />
						</Card>
					) : null}
				</Col>
			</Row>

			<Modal
				open={Boolean(reviewAction)}
				title={
					{
						submit: "提交模型审核",
						approve: "审核通过",
						reject: "驳回模型",
					}[reviewAction || "submit"]
				}
				onCancel={closeReviewDialog}
				onOk={submitReviewAction}
				confirmLoading={reviewLoading}
				okText={{ submit: "提交", approve: "通过", reject: "驳回" }[reviewAction || "submit"]}
				okButtonProps={{ danger: reviewAction === "reject" }}
				destroyOnClose
			>
				<Space direction="vertical" className="w-full">
					<Alert
						type={reviewAction === "reject" ? "warning" : "info"}
						showIcon
						message={
							reviewAction === "submit"
								? "提交后模型进入待审核状态，审核通过前不能发布 dbt。"
								: reviewAction === "approve"
									? "审核通过后先发布 dbt，模型进入已发布状态后再注册 BI 和写入血缘。"
									: "驳回后模型需要修改并重新提交审核。"
						}
					/>
					<Input.TextArea
						rows={4}
						value={reviewComment}
						onChange={(event) => setReviewComment(event.target.value)}
						placeholder={reviewAction === "reject" ? "请输入驳回原因" : "审核意见，可选"}
					/>
				</Space>
			</Modal>
		</div>
	);
}
