import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Modal,
	Space,
	Table,
	Tabs,
	Tag,
	Typography,
	Input,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import {
	listMyDatasetAccessRequests,
	listPendingDatasetAccessTasks,
	listDoneDatasetAccessTasks,
	approveDatasetAccessTask,
	rejectDatasetAccessTask,
} from "@/api/platformApi";

const { Text } = Typography;

type AccessRequest = any;
type TaskView = { task: any; request?: any };

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function Page() {
	const [loading, setLoading] = useState(false);
	const [requests, setRequests] = useState<AccessRequest[]>([]);
	const [pendingTasks, setPendingTasks] = useState<TaskView[]>([]);
	const [doneTasks, setDoneTasks] = useState<TaskView[]>([]);
	const [decisionModal, setDecisionModal] = useState<{
		open: boolean;
		action?: "approve" | "reject";
		taskId?: string;
	}>({ open: false });
	const [decisionNotes, setDecisionNotes] = useState("");

	const loadAll = async () => {
		setLoading(true);
		try {
			const [myReq, pending, done] = await Promise.all([
				listMyDatasetAccessRequests(),
				listPendingDatasetAccessTasks(),
				listDoneDatasetAccessTasks(),
			]);
			setRequests(Array.isArray(myReq) ? (myReq as AccessRequest[]) : []);
			setPendingTasks(Array.isArray(pending) ? (pending as TaskView[]) : []);
			setDoneTasks(Array.isArray(done) ? (done as TaskView[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "审批数据加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadAll();
	}, []);

	const openDecision = (taskId: string, action: "approve" | "reject") => {
		setDecisionNotes("");
		setDecisionModal({ open: true, action, taskId });
	};

	const handleDecision = async () => {
		if (!decisionModal.taskId || !decisionModal.action) return;
		try {
			if (decisionModal.action === "approve") {
				await approveDatasetAccessTask(decisionModal.taskId, decisionNotes || undefined);
				toast.success("已同意申请");
			} else {
				await rejectDatasetAccessTask(decisionModal.taskId, decisionNotes || undefined);
				toast.success("已驳回申请");
			}
			setDecisionModal({ open: false });
			await loadAll();
		} catch (error: any) {
			toast.error(error?.message || "操作失败");
		}
	};

	const requestColumns: ColumnsType<AccessRequest> = [
		{ title: "数据集", dataIndex: "datasetName", render: (v) => v || "-" },
		{ title: "申请人", dataIndex: "requesterName", render: (v) => v || "-" },
		{ title: "目标用户", dataIndex: "targetName", render: (v) => v || "-" },
		{ title: "权限", dataIndex: "canQuery", render: (_, record) => {
			const tags = [] as string[];
			if (record?.canQuery) tags.push("查询");
			if (record?.canPreview) tags.push("预览");
			return tags.length ? tags.map((item) => <Tag key={item}>{item}</Tag>) : "-";
		} },
		{ title: "状态", dataIndex: "status", render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "有效期", render: (_, record) => `${formatDate(record?.validFrom)} ~ ${formatDate(record?.validTo)}` },
	];

	const taskColumns: ColumnsType<TaskView> = [
		{ title: "数据集", dataIndex: ["request", "datasetName"], render: (v) => v || "-" },
		{ title: "申请人", dataIndex: ["request", "requesterName"], render: (v) => v || "-" },
		{ title: "步骤", dataIndex: ["task", "stepOrder"], width: 80, render: (v) => v ?? "-" },
		{ title: "状态", dataIndex: ["task", "status"], render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "创建时间", dataIndex: ["task", "createdDate"], render: (v) => formatDate(v) },
		{ title: "操作", render: (_, record) => (
			<Space>
				<Button size="small" type="primary" onClick={() => openDecision(record.task?.id, "approve")}>同意</Button>
				<Button size="small" danger onClick={() => openDecision(record.task?.id, "reject")}>驳回</Button>
			</Space>
		) },
	];

	const doneColumns: ColumnsType<TaskView> = [
		{ title: "数据集", dataIndex: ["request", "datasetName"], render: (v) => v || "-" },
		{ title: "申请人", dataIndex: ["request", "requesterName"], render: (v) => v || "-" },
		{ title: "审批人", dataIndex: ["task", "decidedBy"], render: (v) => v || "-" },
		{ title: "结果", dataIndex: ["task", "status"], render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "审批时间", dataIndex: ["task", "decidedAt"], render: (v) => formatDate(v) },
		{ title: "备注", dataIndex: ["task", "decisionNotes"], render: (v) => v || "-" },
	];

	return (
		<div className="space-y-6">
			<PageHeader title="数据资产门户 / 权限申请" description="表级与字段级权限审批。" />
			<Card>
				<Tabs
					items={[
						{
							key: "requests",
							label: `我的申请 (${requests.length})`,
							children: (
								<Table rowKey={(record) => record.id} dataSource={requests} columns={requestColumns} loading={loading} />
							),
						},
						{
							key: "pending",
							label: `待我审批 (${pendingTasks.length})`,
							children: (
								<Table rowKey={(record) => record.task?.id} dataSource={pendingTasks} columns={taskColumns} loading={loading} />
							),
						},
						{
							key: "done",
							label: `已处理 (${doneTasks.length})`,
							children: (
								<Table rowKey={(record) => record.task?.id} dataSource={doneTasks} columns={doneColumns} loading={loading} />
							),
						},
					]}
				/>
			</Card>

			<Modal
				open={decisionModal.open}
				title={decisionModal.action === "approve" ? "同意申请" : "驳回申请"}
				onCancel={() => setDecisionModal({ open: false })}
				onOk={handleDecision}
				okText="确认"
				destroyOnClose
			>
				<Text>审批意见（可选）</Text>
				<Input.TextArea
					rows={4}
					value={decisionNotes}
					onChange={(event) => setDecisionNotes(event.target.value)}
					placeholder="请输入备注"
				/>
			</Modal>
		</div>
	);
}
