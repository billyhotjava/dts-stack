import { useEffect, useState } from "react";
import { Alert, Button, Card, Empty, Select, Space, Table, Tag, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlayCircleOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import {
	listSemanticModelRuns,
	listSemanticModels,
	triggerSemanticModelRun,
	type SemanticModel,
	type SemanticModelRun,
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
					/>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择模型查看运行记录" />
				)}
			</Card>
		</div>
	);
}
