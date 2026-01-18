import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Modal, Space, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	approveDatasetAccessTask,
	listDoneDatasetAccessTasks,
	listMyDatasetAccessRequests,
	listPendingDatasetAccessTasks,
	rejectDatasetAccessTask,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

const { Text, Paragraph } = Typography;

type AccessRequest = {
	id: string;
	datasetId?: string;
	datasetName?: string;
	ownerDept?: string;
	classification?: string;
	warehouseLayer?: string;
	requesterUsername?: string;
	requesterName?: string;
	requesterDept?: string;
	targetUsername?: string;
	targetName?: string;
	targetDept?: string;
	canQuery?: boolean;
	canPreview?: boolean;
	reason?: string;
	status?: string;
	validFrom?: string;
	validTo?: string;
	decidedBy?: string;
	decidedAt?: string;
	decisionNotes?: string;
	createdDate?: string;
	createdBy?: string;
};

type AccessTask = {
	id: string;
	requestId: string;
	stepOrder?: number;
	approverRole?: string;
	deptCode?: string;
	status?: string;
	decidedBy?: string;
	decidedAt?: string;
	decisionNotes?: string;
	createdDate?: string;
};

type PendingTaskRow = { task: AccessTask; request?: AccessRequest | null };

const formatDateTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const renderStatus = (status?: string) => {
	const normalized = String(status || "").toUpperCase();
	if (normalized === "APPROVED") return <Tag color="green">已通过</Tag>;
	if (normalized === "REJECTED") return <Tag color="red">已驳回</Tag>;
	return <Tag color="gold">待审批</Tag>;
};

const renderActions = (req?: AccessRequest | null) => {
	const parts: string[] = [];
	if (req?.canQuery) parts.push("查询");
	if (req?.canPreview) parts.push("预览");
	return parts.length ? parts.join(" / ") : "-";
};

export default function WorkflowCenterPage() {
	const router = useRouter();
	const [activeTab, setActiveTab] = useState<"mine" | "pending" | "done">("mine");
	const [loadingMine, setLoadingMine] = useState(false);
	const [loadingPending, setLoadingPending] = useState(false);
	const [loadingDone, setLoadingDone] = useState(false);
	const [myRequests, setMyRequests] = useState<AccessRequest[]>([]);
	const [pendingTasks, setPendingTasks] = useState<PendingTaskRow[]>([]);
	const [doneTasks, setDoneTasks] = useState<PendingTaskRow[]>([]);

	const [decisionOpen, setDecisionOpen] = useState(false);
	const [decisionMode, setDecisionMode] = useState<"approve" | "reject">("approve");
	const [decisionNotes, setDecisionNotes] = useState("");
	const [decisionRow, setDecisionRow] = useState<PendingTaskRow | null>(null);
	const [decisionSubmitting, setDecisionSubmitting] = useState(false);

	const loadMine = useCallback(async () => {
		setLoadingMine(true);
		try {
			const resp: any = await listMyDatasetAccessRequests();
			setMyRequests(Array.isArray(resp) ? resp : []);
		} catch (error) {
			console.error(error);
			setMyRequests([]);
		} finally {
			setLoadingMine(false);
		}
	}, []);

	const loadPending = useCallback(async () => {
		setLoadingPending(true);
		try {
			const resp: any = await listPendingDatasetAccessTasks();
			const list = Array.isArray(resp) ? resp : [];
			setPendingTasks(
				list.map((item: any) => ({
					task: item?.task as AccessTask,
					request: (item?.request as AccessRequest) ?? null,
				})),
			);
		} catch (error) {
			console.error(error);
			setPendingTasks([]);
		} finally {
			setLoadingPending(false);
		}
	}, []);

	const loadDone = useCallback(async () => {
		setLoadingDone(true);
		try {
			const resp: any = await listDoneDatasetAccessTasks();
			const list = Array.isArray(resp) ? resp : [];
			setDoneTasks(
				list.map((item: any) => ({
					task: item?.task as AccessTask,
					request: (item?.request as AccessRequest) ?? null,
				})),
			);
		} catch (error) {
			console.error(error);
			setDoneTasks([]);
		} finally {
			setLoadingDone(false);
		}
	}, []);

	useEffect(() => {
		void loadMine();
	}, [loadMine]);

	useEffect(() => {
		if (activeTab === "pending") {
			void loadPending();
		}
		if (activeTab === "done") {
			void loadDone();
		}
	}, [activeTab, loadPending, loadDone]);

	const myColumns: ColumnsType<AccessRequest> = useMemo(
		() => [
			{
				title: "数据资产",
				dataIndex: "datasetName",
				key: "datasetName",
				width: 180,
				ellipsis: true,
				render: (_: unknown, row) => (
					<Button
						type="link"
						style={{ padding: 0 }}
						onClick={() => {
							const datasetId = row.datasetId || "";
							if (datasetId) router.push(`/catalog/datasets/${datasetId}`);
						}}
					>
						{row.datasetName || row.datasetId || "-"}
					</Button>
					),
			},
			{ title: "分层", dataIndex: "warehouseLayer", key: "warehouseLayer", width: 90, render: (v) => v || "-" },
			{ title: "密级", dataIndex: "classification", key: "classification", width: 110, render: (v) => v || "-" },
			{
				title: "申请对象",
				key: "target",
				width: 160,
				render: (_: unknown, row) => row.targetName || row.targetUsername || "-",
			},
			{
				title: "提交人",
				key: "requester",
				width: 160,
				render: (_: unknown, row) => row.requesterName || row.requesterUsername || "-",
			},
			{ title: "申请权限", key: "actions", width: 120, render: (_, row) => renderActions(row) },
			{ title: "有效期至", dataIndex: "validTo", key: "validTo", width: 180, render: (v) => formatDateTime(v) },
			{ title: "状态", dataIndex: "status", key: "status", width: 110, render: (v) => renderStatus(v) },
			{ title: "申请时间", dataIndex: "createdDate", key: "createdDate", width: 180, render: (v) => formatDateTime(v) },
		],
		[router],
	);

	const pendingColumns: ColumnsType<PendingTaskRow> = useMemo(
		() => [
			{
				title: "数据资产",
				key: "dataset",
				width: 180,
				ellipsis: true,
				render: (_: unknown, row) => (
					<Button
						type="link"
						style={{ padding: 0 }}
						onClick={() => {
							const datasetId = row.request?.datasetId || "";
							if (datasetId) router.push(`/catalog/datasets/${datasetId}`);
						}}
					>
						{row.request?.datasetName || row.request?.datasetId || "-"}
					</Button>
					),
			},
			{ title: "申请对象", key: "target", width: 160, render: (_, row) => row.request?.targetName || row.request?.targetUsername || "-" },
			{ title: "提交人", key: "requester", width: 160, render: (_, row) => row.request?.requesterName || row.request?.requesterUsername || "-" },
			{ title: "分层", key: "layer", width: 90, render: (_, row) => row.request?.warehouseLayer || "-" },
			{ title: "密级", key: "class", width: 110, render: (_, row) => row.request?.classification || "-" },
			{ title: "申请权限", key: "actions", width: 120, render: (_, row) => renderActions(row.request) },
			{ title: "有效期至", key: "validTo", width: 180, render: (_, row) => formatDateTime(row.request?.validTo) },
			{ title: "审批环节", key: "step", width: 150, render: (_, row) => `${row.task?.approverRole || "-"} / 第${row.task?.stepOrder || 0}步` },
			{
				title: "操作",
				key: "op",
				width: 180,
				render: (_: unknown, row) => (
					<Space>
						<Button
							size="small"
							type="primary"
							onClick={() => {
								setDecisionMode("approve");
								setDecisionRow(row);
								setDecisionNotes("");
								setDecisionOpen(true);
							}}
						>
							通过
						</Button>
						<Button
							size="small"
							danger
							onClick={() => {
								setDecisionMode("reject");
								setDecisionRow(row);
								setDecisionNotes("");
								setDecisionOpen(true);
							}}
						>
							驳回
						</Button>
					</Space>
					),
			},
		],
		[router],
	);

	const doneColumns: ColumnsType<PendingTaskRow> = useMemo(
		() => [
			{
				title: "数据资产",
				key: "dataset",
				width: 180,
				ellipsis: true,
				render: (_: unknown, row) => (
					<Button
						type="link"
						style={{ padding: 0 }}
						onClick={() => {
							const datasetId = row.request?.datasetId || "";
							if (datasetId) router.push(`/catalog/datasets/${datasetId}`);
						}}
					>
						{row.request?.datasetName || row.request?.datasetId || "-"}
					</Button>
					),
			},
			{ title: "申请对象", key: "target", width: 160, render: (_, row) => row.request?.targetName || row.request?.targetUsername || "-" },
			{ title: "提交人", key: "requester", width: 160, render: (_, row) => row.request?.requesterName || row.request?.requesterUsername || "-" },
			{ title: "申请权限", key: "actions", width: 120, render: (_, row) => renderActions(row.request) },
			{ title: "审批结果", key: "status", width: 120, render: (_, row) => renderStatus(row.task?.status) },
			{ title: "审批时间", key: "decidedAt", width: 180, render: (_, row) => formatDateTime(row.task?.decidedAt) },
			{ title: "审批意见", key: "notes", width: 220, render: (_, row) => row.task?.decisionNotes || "-" },
			{ title: "审批环节", key: "step", width: 150, render: (_, row) => `${row.task?.approverRole || "-"} / 第${row.task?.stepOrder || 0}步` },
		],
		[router],
	);

	const handleDecisionSubmit = async () => {
		if (!decisionRow?.task?.id) {
			setDecisionOpen(false);
			return;
		}
		setDecisionSubmitting(true);
		try {
			if (decisionMode === "approve") {
				await approveDatasetAccessTask(decisionRow.task.id, decisionNotes.trim() || undefined);
				toast.success("已通过审批");
			} else {
				await rejectDatasetAccessTask(decisionRow.task.id, decisionNotes.trim() || undefined);
				toast.success("已驳回审批");
			}
			setDecisionOpen(false);
			setDecisionRow(null);
			void loadPending();
			void loadDone();
		} catch (error) {
			console.error(error);
			toast.error("审批提交失败");
		} finally {
			setDecisionSubmitting(false);
		}
	};

	return (
		<Card>
			<Typography.Title level={4} style={{ marginBottom: 8 }}>
				流程中心
			</Typography.Title>
			<Paragraph style={{ marginBottom: 16 }}>
				<Text type="secondary">
					当前仅展示“数据资产内容访问审批（查询/预览）”。元数据浏览不受影响，查询/预览数据内容需审批授权。
				</Text>
			</Paragraph>
			<Tabs
				activeKey={activeTab}
				onChange={(key) => setActiveTab(key as "mine" | "pending" | "done")}
				items={[
					{
						key: "mine",
						label: "我发起的",
						children: (
							<Table
								rowKey={(row) => row.id}
								loading={loadingMine}
								columns={myColumns}
								dataSource={myRequests}
								pagination={{ pageSize: 10 }}
								locale={{ emptyText: "暂无申请记录" }}
							/>
						),
					},
					{
						key: "pending",
						label: "我的待办",
						children: (
							<Table
								rowKey={(row) => row.task?.id || row.request?.id || Math.random().toString()}
								loading={loadingPending}
								columns={pendingColumns}
								dataSource={pendingTasks}
								pagination={{ pageSize: 10 }}
								locale={{ emptyText: "暂无待审批流程" }}
							/>
						),
					},
					{
						key: "done",
						label: "我的已办",
						children: (
							<Table
								rowKey={(row) => row.task?.id || row.request?.id || Math.random().toString()}
								loading={loadingDone}
								columns={doneColumns}
								dataSource={doneTasks}
								pagination={{ pageSize: 10 }}
								locale={{ emptyText: "暂无已办流程" }}
							/>
						),
					},
				]}
			/>

			<Modal
				open={decisionOpen}
				title={decisionMode === "approve" ? "通过审批" : "驳回申请"}
				onCancel={() => setDecisionOpen(false)}
				onOk={handleDecisionSubmit}
				confirmLoading={decisionSubmitting}
				destroyOnClose
			>
				<Input.TextArea
					rows={4}
					placeholder="审批意见（可选）"
					value={decisionNotes}
					onChange={(e) => setDecisionNotes(e.target.value)}
				/>
			</Modal>
		</Card>
	);
}
