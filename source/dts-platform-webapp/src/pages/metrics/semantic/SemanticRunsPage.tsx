import { useEffect, useState } from "react";
import { Alert, Button, Card, Empty, Input, Modal, Select, Space, Table, Tag, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { CheckCircleOutlined, CloseCircleOutlined, PlayCircleOutlined, ReloadOutlined, SyncOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import {
	listSemanticModelRuns,
	listSemanticModels,
	triggerSemanticModelRun,
	type SemanticModel,
	type SemanticModelRun,
	updateSemanticModelRun,
} from "@/api/semanticModelingApi";
import { SemanticSectionNav } from "./SemanticSectionNav";
import { asArray, semanticSectionMeta } from "./semanticModelingShared";

const statusColor = (status?: string) => {
	const value = (status || "").toUpperCase();
	if (value === "SUCCESS" || value === "SUCCEEDED" || value === "COMPLETED") return "green";
	if (value === "RUNNING" || value === "PENDING") return "blue";
	if (value === "FAILED" || value === "ERROR") return "red";
	return "default";
};

export default function SemanticRunsPage() {
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [runs, setRuns] = useState<SemanticModelRun[]>([]);
	const [selectedModelId, setSelectedModelId] = useState<string>();
	const [modelsLoading, setModelsLoading] = useState(false);
	const [runLoading, setRunLoading] = useState(false);
	const [statusModalOpen, setStatusModalOpen] = useState(false);
	const [selectedRun, setSelectedRun] = useState<SemanticModelRun | null>(null);
	const [nextStatus, setNextStatus] = useState<string>("SUCCESS");
	const [statusMessage, setStatusMessage] = useState("");

	const loadModels = () => {
		setModelsLoading(true);
		listSemanticModels()
			.then((resp) => setModels(asArray<SemanticModel>(resp)))
			.catch(() => setModels([]))
			.finally(() => setModelsLoading(false));
	};

	const loadRuns = (modelId = selectedModelId) => {
		if (!modelId) return;
		setRunLoading(true);
		listSemanticModelRuns(modelId)
			.then((resp) => setRuns(asArray<SemanticModelRun>(resp)))
			.catch(() => setRuns([]))
			.finally(() => setRunLoading(false));
	};

	useEffect(() => {
		loadModels();
	}, []);

	useEffect(() => {
		if (!selectedModelId) {
			setRuns([]);
			return;
		}
		loadRuns(selectedModelId);
	}, [selectedModelId]);

	const triggerRun = async () => {
		if (!selectedModelId) {
			message.warning("请选择语义模型");
			return;
		}
		setRunLoading(true);
		try {
			await triggerSemanticModelRun(selectedModelId);
			message.success("已触发模型运行");
			await listSemanticModelRuns(selectedModelId).then((resp) => setRuns(asArray<SemanticModelRun>(resp)));
		} finally {
			setRunLoading(false);
		}
	};

	const openStatusModal = (run: SemanticModelRun, status: string) => {
		setSelectedRun(run);
		setNextStatus(status);
		setStatusMessage(run.message || "");
		setStatusModalOpen(true);
	};

	const closeStatusModal = () => {
		setStatusModalOpen(false);
		setSelectedRun(null);
		setStatusMessage("");
	};

	const submitRunStatus = async () => {
		if (!selectedModelId || !selectedRun?.id) return;
		setRunLoading(true);
		try {
			await updateSemanticModelRun(selectedModelId, selectedRun.id, {
				status: nextStatus,
				message: statusMessage,
			});
			message.success("运行状态已更新");
			closeStatusModal();
			loadRuns(selectedModelId);
		} finally {
			setRunLoading(false);
		}
	};

	const columns: ColumnsType<SemanticModelRun> = [
		{ title: "状态", dataIndex: "status", width: 120, render: (value) => <Tag color={statusColor(value)}>{value || "-"}</Tag> },
		{ title: "Selector", dataIndex: "selector", render: (value) => value || "-" },
		{ title: "DAG", dataIndex: "dagId", render: (value) => value || "-" },
		{ title: "外部运行 ID", dataIndex: "externalRunId", render: (value) => value || "-" },
		{ title: "触发人", dataIndex: "triggeredBy", width: 120, render: (value) => value || "-" },
		{ title: "开始时间", dataIndex: "startedAt", width: 190, render: (value) => value || "-" },
		{ title: "结束时间", dataIndex: "finishedAt", width: 190, render: (value) => value || "-" },
		{ title: "耗时(ms)", dataIndex: "durationMs", width: 110, render: (value) => value ?? "-" },
		{ title: "消息", dataIndex: "message", render: (value) => value || "-" },
		{
			title: "操作",
			width: 210,
			fixed: "right",
			render: (_, row) => (
				<Space size={4} wrap>
					<Button size="small" icon={<SyncOutlined />} onClick={() => openStatusModal(row, "RUNNING")}>运行中</Button>
					<Button size="small" icon={<CheckCircleOutlined />} onClick={() => openStatusModal(row, "SUCCESS")}>成功</Button>
					<Button size="small" danger icon={<CloseCircleOutlined />} onClick={() => openStatusModal(row, "FAILED")}>失败</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-5 p-5" data-testid="semantic-runs-page">
			<PageHeader
				title={semanticSectionMeta.runs.title}
				actions={(
					<Space wrap>
						<Select
							placeholder="选择模型"
							style={{ minWidth: 260 }}
							loading={modelsLoading}
							value={selectedModelId}
							options={models.map((item) => ({ label: `${item.name}${item.tableName ? ` / ${item.tableName}` : ""}`, value: item.id }))}
							onChange={setSelectedModelId}
						/>
						<Button icon={<PlayCircleOutlined />} onClick={triggerRun} loading={runLoading} disabled={!selectedModelId}>触发运行</Button>
						<Button icon={<ReloadOutlined />} onClick={() => loadRuns()} loading={runLoading} disabled={!selectedModelId}>刷新</Button>
					</Space>
				)}
			/>

			<SemanticSectionNav activeSection="runs" />

			<Alert
				type="info"
				showIcon
				message="运行监控边界"
				description="这里仅处理语义模型对应 dbt/调度运行记录，模型结构和发布审核分别在 DWS/ADS 与发布页面完成。"
			/>

			<Card title="模型运行记录">
				{selectedModelId ? (
					<Table<SemanticModelRun>
						rowKey="id"
						size="small"
						loading={runLoading}
						pagination={{ pageSize: 10 }}
						columns={columns}
						dataSource={runs}
						scroll={{ x: 1280 }}
					/>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择模型查看运行记录" />
				)}
			</Card>

			<Modal
				open={statusModalOpen}
				title={`更新运行状态：${nextStatus}`}
				onCancel={closeStatusModal}
				onOk={submitRunStatus}
				confirmLoading={runLoading}
				destroyOnClose
			>
				<Space direction="vertical" className="w-full">
					<Alert
						type={nextStatus === "FAILED" ? "warning" : "info"}
						showIcon
						message="状态会同步回语义模型，用于发布治理和工作台诊断。"
					/>
					<Input.TextArea
						rows={4}
						value={statusMessage}
						onChange={(event) => setStatusMessage(event.target.value)}
						placeholder="运行说明或失败原因"
					/>
				</Space>
			</Modal>
		</div>
	);
}
