import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Modal, Space, Table, Tabs, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	approveDatasetAccessTask,
	listMyDatasetAccessRequests,
	listPendingDatasetAccessTasks,
	rejectDatasetAccessTask,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";

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

export default function DatasetAccessApprovalPage() {
	const router = useRouter();
	const [activeTab, setActiveTab] = useState<"mine" | "pending">("mine");
	const [loadingMine, setLoadingMine] = useState(false);
	const [loadingPending, setLoadingPending] = useState(false);
	const [myRequests, setMyRequests] = useState<AccessRequest[]>([]);
	const [pendingTasks, setPendingTasks] = useState<PendingTaskRow[]>([]);

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

	useEffect(() => {
		void loadMine();
	}, [loadMine]);

	useEffect(() => {
		if (activeTab === "pending") {
			void loadPending();
		}
	}, [activeTab, loadPending]);

	const myColumns: ColumnsType<AccessRequest> = useMemo(
		() => [
			{
				title: "数据资产",
				dataIndex: "datasetName",
				key: "datasetName",
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
			{ title: "申请人", key: "requester", width: 160, render: (_, row) => row.request?.requesterName || row.request?.requesterUsername || "-" },
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

	const handleDecision = useCallback(async () => {
		if (!decisionRow?.task?.id) {
			setDecisionOpen(false);
			return;
		}
		setDecisionSubmitting(true);
		try {
			const notes = decisionNotes.trim() || undefined;
			if (decisionMode === "approve") {
				await approveDatasetAccessTask(decisionRow.task.id, notes);
				toast.success("已通过审批");
			} else {
				await rejectDatasetAccessTask(decisionRow.task.id, notes);
				toast.success("已驳回申请");
			}
			setDecisionOpen(false);
			setDecisionRow(null);
			setDecisionNotes("");
			await loadPending();
			await loadMine();
		} catch (error) {
			console.error(error);
			toast.error("操作失败");
		} finally {
			setDecisionSubmitting(false);
		}
	}, [decisionMode, decisionNotes, decisionRow, loadMine, loadPending]);

	return (
		<div className="p-4">
			<Card style={{ marginBottom: 12 }}>
				<Typography.Title level={4} style={{ margin: 0 }}>
					数据内容访问审批（查询/预览）
				</Typography.Title>
				<Typography.Paragraph style={{ marginBottom: 0, marginTop: 8, color: "#666" }}>
					说明：元数据浏览不受影响；查询/预览数据内容必须走审批授权。
				</Typography.Paragraph>
			</Card>

			<Card>
				<Tabs
					activeKey={activeTab}
					onChange={(key) => setActiveTab(key === "pending" ? "pending" : "mine")}
					items={[
						{
							key: "mine",
							label: "我的申请",
							children: (
								<Table
									rowKey={(row) => row.id}
									loading={loadingMine}
									columns={myColumns}
									dataSource={myRequests}
									size="middle"
									pagination={{ pageSize: 10 }}
								/>
							),
						},
						{
							key: "pending",
							label: "待我审批",
							children: (
								<Table
									rowKey={(row) => row.task.id}
									loading={loadingPending}
									columns={pendingColumns}
									dataSource={pendingTasks}
									size="middle"
									pagination={{ pageSize: 10 }}
								/>
							),
						},
					]}
				/>
			</Card>

			<Modal
				open={decisionOpen}
				title={decisionMode === "approve" ? "通过审批" : "驳回申请"}
				okText={decisionMode === "approve" ? "确认通过" : "确认驳回"}
				okButtonProps={{ loading: decisionSubmitting, danger: decisionMode === "reject" }}
				cancelButtonProps={{ disabled: decisionSubmitting }}
				onOk={handleDecision}
				onCancel={() => {
					setDecisionOpen(false);
					setDecisionRow(null);
					setDecisionNotes("");
				}}
			>
				<div style={{ marginBottom: 12 }}>
					<div style={{ fontWeight: 600, marginBottom: 6 }}>{decisionRow?.request?.datasetName || "-"}</div>
					<div style={{ color: "#666" }}>
						申请人：{decisionRow?.request?.requesterName || decisionRow?.request?.requesterUsername || "-"}，申请权限：
						{renderActions(decisionRow?.request)}
					</div>
				</div>
				<Input.TextArea
					value={decisionNotes}
					onChange={(e) => setDecisionNotes(e.target.value)}
					placeholder="审批意见（可选）"
					autoSize={{ minRows: 3, maxRows: 6 }}
				/>
			</Modal>
		</div>
	);
}

