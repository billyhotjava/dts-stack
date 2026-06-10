import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Descriptions, Drawer, Input, Modal, Select, Space, Tabs, Tag, Typography } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { useCatalogManageAccess } from "@/hooks/useModuleManageAccess";
import { PageHeader } from "@/components/page-header";
import {
	cancelDatasetAccessRequest,
	decideDatasetAccessTask,
	decideDatasetAccessTaskBatch,
	getDatasetAccessRequestDetail,
	listDoneDatasetAccessTasks,
	listMyDatasetAccessRequests,
	listPendingDatasetAccessTasks,
} from "@/api/platformApi";

const { Text } = Typography;

type AccessRequest = any;
type TaskView = { task: any; request?: any };
type PageResult<T> = { content: T[]; total: number; page: number; size: number };
type RequestDetail = {
	request?: any;
	steps?: any[];
	currentTask?: any;
	effectiveStatus?: string;
	grant?: Record<string, any>;
};

const APPROVAL_NOTE_TEMPLATES = [
	{ label: "通过：业务需求明确，按期授权", value: "业务需求明确，按申请有效期授权。" },
	{ label: "通过：仅预览，不开放查询", value: "同意预览权限，不开放查询权限。" },
	{ label: "驳回：申请理由不足", value: "申请理由不足，请补充业务场景与使用范围后重提。" },
	{ label: "驳回：超出最小权限原则", value: "超出最小权限原则，请缩小数据范围或权限类型后重提。" },
];

const statusColor = (status?: string) => {
	const key = String(status || "").toUpperCase();
	if (key === "APPROVED" || key === "EFFECTIVE") return "green";
	if (key === "PENDING" || key === "WAITING_EFFECTIVE") return "blue";
	if (key === "REJECTED" || key === "CANCELLED" || key === "EXPIRED" || key === "GRANT_MISSING") return "red";
	if (key === "SKIPPED") return "default";
	return "default";
};

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

const parsePage = <T,>(raw: any, fallbackPage: number, fallbackSize: number): PageResult<T> => {
	if (Array.isArray(raw)) {
		return { content: raw as T[], total: raw.length, page: fallbackPage, size: fallbackSize };
	}
	return {
		content: Array.isArray(raw?.content) ? (raw.content as T[]) : [],
		total: Number(raw?.total || 0),
		page: Number(raw?.page || fallbackPage),
		size: Number(raw?.size || fallbackSize),
	};
};

export default function Page() {
	const canManage = useCatalogManageAccess();
	const [activeTab, setActiveTab] = useState("requests");

	const [requestsPage, setRequestsPage] = useState<PageResult<AccessRequest>>({ content: [], total: 0, page: 1, size: 10 });
	const [pendingPage, setPendingPage] = useState<PageResult<TaskView>>({ content: [], total: 0, page: 1, size: 10 });
	const [donePage, setDonePage] = useState<PageResult<TaskView>>({ content: [], total: 0, page: 1, size: 10 });

	const [requestsQuery, setRequestsQuery] = useState<{ page: number; size: number; status?: string; keyword?: string }>({
		page: 1,
		size: 10,
	});
	const [pendingQuery, setPendingQuery] = useState<{ page: number; size: number; keyword?: string }>({ page: 1, size: 10 });
	const [doneQuery, setDoneQuery] = useState<{ page: number; size: number; status?: string; keyword?: string }>({ page: 1, size: 10 });

	const [loadingRequests, setLoadingRequests] = useState(false);
	const [loadingPending, setLoadingPending] = useState(false);
	const [loadingDone, setLoadingDone] = useState(false);

	const [selectedPendingRowKeys, setSelectedPendingRowKeys] = useState<React.Key[]>([]);
	const [decisionModal, setDecisionModal] = useState<{
		open: boolean;
		action?: "approve" | "reject";
		taskIds?: string[];
	}>({ open: false });
	const [decisionNotes, setDecisionNotes] = useState("");

	const [detailOpen, setDetailOpen] = useState(false);
	const [detailLoading, setDetailLoading] = useState(false);
	const [detail, setDetail] = useState<RequestDetail | null>(null);
	const [cancellingRequestId, setCancellingRequestId] = useState<string | null>(null);

	const loadRequests = async () => {
		setLoadingRequests(true);
		try {
			const resp: any = await listMyDatasetAccessRequests(requestsQuery);
			setRequestsPage(parsePage<AccessRequest>(resp, requestsQuery.page, requestsQuery.size));
		} catch (error: any) {
			toast.error(error?.message || "我的申请加载失败");
		} finally {
			setLoadingRequests(false);
		}
	};

	const loadPending = async () => {
		setLoadingPending(true);
		try {
			const resp: any = await listPendingDatasetAccessTasks(pendingQuery);
			setPendingPage(parsePage<TaskView>(resp, pendingQuery.page, pendingQuery.size));
		} catch (error: any) {
			toast.error(error?.message || "待办加载失败");
		} finally {
			setLoadingPending(false);
		}
	};

	const loadDone = async () => {
		setLoadingDone(true);
		try {
			const resp: any = await listDoneDatasetAccessTasks(doneQuery);
			setDonePage(parsePage<TaskView>(resp, doneQuery.page, doneQuery.size));
		} catch (error: any) {
			toast.error(error?.message || "已办加载失败");
		} finally {
			setLoadingDone(false);
		}
	};

	useEffect(() => {
		void loadRequests();
	}, [requestsQuery]);

	useEffect(() => {
		void loadPending();
	}, [pendingQuery]);

	useEffect(() => {
		void loadDone();
	}, [doneQuery]);

	const openDecision = (taskIds: string[], action: "approve" | "reject") => {
		if (!canManage) {
			toast.error("当前账号无资产审批权限");
			return;
		}
		if (!taskIds.length) return;
		setDecisionNotes("");
		setDecisionModal({ open: true, action, taskIds });
	};

	const openRequestDetail = async (requestId?: string) => {
		if (!requestId) return;
		setDetailOpen(true);
		setDetailLoading(true);
		try {
			const resp: any = await getDatasetAccessRequestDetail(requestId);
			setDetail(resp || null);
		} catch (error: any) {
			toast.error(error?.message || "审批详情加载失败");
			setDetail(null);
		} finally {
			setDetailLoading(false);
		}
	};

	const handleCancelRequest = async (requestId?: string) => {
		if (!requestId) return;
		setCancellingRequestId(requestId);
		try {
			await cancelDatasetAccessRequest(requestId);
			toast.success("申请已撤回");
			await Promise.all([loadRequests(), loadPending(), loadDone()]);
			if (detailOpen) {
				await openRequestDetail(requestId);
			}
		} catch (error: any) {
			toast.error(error?.message || "撤回失败");
		} finally {
			setCancellingRequestId(null);
		}
	};

	const handleDecision = async () => {
		if (!canManage) {
			toast.error("当前账号无资产审批权限");
			return;
		}
		const approved = decisionModal.action === "approve";
		const taskIds = decisionModal.taskIds || [];
		if (!taskIds.length || decisionModal.action == null) return;
		try {
			if (taskIds.length === 1) {
				await decideDatasetAccessTask(taskIds[0], approved, decisionNotes || undefined);
			} else {
				await decideDatasetAccessTaskBatch(taskIds, approved, decisionNotes || undefined);
			}
			toast.success(approved ? "审批通过" : "审批驳回");
			setDecisionModal({ open: false });
			setSelectedPendingRowKeys([]);
			await Promise.all([loadRequests(), loadPending(), loadDone()]);
		} catch (error: any) {
			toast.error(error?.message || "审批失败");
		}
	};

	const requestColumns: ColumnsType<AccessRequest> = [
		{ title: "数据集", dataIndex: "datasetName", render: (v) => v || "-" , sorter: (a, b) => (a.datasetName || "").localeCompare(b.datasetName || "") },
		{ title: "申请人", dataIndex: "requesterName", render: (v) => v || "-" },
		{ title: "目标用户", dataIndex: "targetName", render: (v) => v || "-" },
		{
			title: "权限",
			dataIndex: "canQuery",
			render: (_, record) => {
				const tags: string[] = [];
				if (record?.canQuery) tags.push("查询");
				if (record?.canPreview) tags.push("预览");
				return tags.length ? tags.map((item) => <Tag key={item}>{item}</Tag>) : "-";
			},
		},
		{ title: "状态", dataIndex: "status", render: (v) => <Tag color={statusColor(v)}>{v || "-"}</Tag> },
		{ title: "有效期", render: (_, record) => `${formatDate(record?.validFrom)} ~ ${formatDate(record?.validTo)}` },
		{
			title: "操作",
			width: 210,
			render: (_, record) => (
				<Space>
					<Button size="small" onClick={() => void openRequestDetail(record?.id)}>
						详情
					</Button>
					{String(record?.status || "").toUpperCase() === "PENDING" ? (
						<Button
							size="small"
							danger
							onClick={() => void handleCancelRequest(record?.id)}
							loading={cancellingRequestId === record?.id}
						>
							撤回
						</Button>
					) : null}
				</Space>
			),
		},
	];

	const taskColumns: ColumnsType<TaskView> = [
		{ title: "数据集", dataIndex: ["request", "datasetName"], render: (v) => v || "-" },
		{ title: "申请人", dataIndex: ["request", "requesterName"], render: (v) => v || "-" },
		{ title: "步骤", dataIndex: ["task", "stepOrder"], width: 80, render: (v) => v ?? "-" },
		{ title: "状态", dataIndex: ["task", "status"], width: 120, render: (v) => <Tag color={statusColor(v)}>{v || "-"}</Tag> },
		{ title: "创建时间", dataIndex: ["task", "createdDate"], render: (v) => formatDate(v) },
		{
			title: "操作",
			render: (_, record) => (
				<Space>
					<Button size="small" onClick={() => void openRequestDetail(record?.request?.id)}>
						详情
					</Button>
					<Button size="small" type="primary" onClick={() => openDecision([record.task?.id], "approve")} disabled={!canManage}>
						同意
					</Button>
					<Button size="small" danger onClick={() => openDecision([record.task?.id], "reject")} disabled={!canManage}>
						驳回
					</Button>
				</Space>
			),
		},
	];

	const doneColumns: ColumnsType<TaskView> = [
		{ title: "数据集", dataIndex: ["request", "datasetName"], render: (v) => v || "-" },
		{ title: "申请人", dataIndex: ["request", "requesterName"], render: (v) => v || "-" },
		{ title: "审批人", dataIndex: ["task", "decidedBy"], render: (v) => v || "-" },
		{ title: "结果", dataIndex: ["task", "status"], render: (v) => <Tag color={statusColor(v)}>{v || "-"}</Tag> },
		{ title: "审批时间", dataIndex: ["task", "decidedAt"], render: (v) => formatDate(v) },
		{ title: "备注", dataIndex: ["task", "decisionNotes"], render: (v) => v || "-" },
		{
			title: "操作",
			render: (_, record) => (
				<Button size="small" onClick={() => void openRequestDetail(record?.request?.id)}>
					详情
				</Button>
			),
		},
	];

	const pendingSelectedTaskIds = useMemo(
		() => selectedPendingRowKeys.map((key) => String(key)),
		[selectedPendingRowKeys],
	);

	return (
		<div className="space-y-6">
			<PageHeader title="数据资产门户 / 权限申请" />
			<Card>
				<Tabs
					activeKey={activeTab}
					onChange={setActiveTab}
					items={[
						{
							key: "requests",
							label: `我的申请 (${requestsPage.total})`,
							children: (
								<Space direction="vertical" size={12} className="w-full">
									<Space wrap>
										<Input
											allowClear
											placeholder="按数据集/申请人/目标用户搜索"
											style={{ width: 280 }}
											value={requestsQuery.keyword || ""}
											onChange={(e) =>
												setRequestsQuery((prev) => ({ ...prev, page: 1, keyword: e.target.value || undefined }))
											}
										/>
										<Select
											allowClear
											placeholder="状态"
											style={{ width: 160 }}
											value={requestsQuery.status}
											options={[
												{ label: "待审批", value: "PENDING" },
												{ label: "已通过", value: "APPROVED" },
												{ label: "已驳回", value: "REJECTED" },
												{ label: "已撤回", value: "CANCELLED" },
											]}
											onChange={(value) => setRequestsQuery((prev) => ({ ...prev, page: 1, status: value || undefined }))}
										/>
									</Space>
									<CompactTable
										rowKey={(record) => record.id}
										dataSource={requestsPage.content}
										columns={requestColumns}
										loading={loadingRequests}
										pagination={{
											current: requestsPage.page,
											pageSize: requestsPage.size,
											total: requestsPage.total,
											showSizeChanger: true,
											onChange: (page, size) =>
												setRequestsQuery((prev) => ({
													...prev,
													page: size && size !== prev.size ? 1 : page,
													size: size || prev.size,
												})),
										}}
									/>
								</Space>
							),
						},
						{
							key: "pending",
							label: `待我审批 (${pendingPage.total})`,
							children: (
								<Space direction="vertical" size={12} className="w-full">
									<Space wrap>
										<Input
											allowClear
											placeholder="按数据集/申请人/目标用户搜索"
											style={{ width: 280 }}
											value={pendingQuery.keyword || ""}
											onChange={(e) => setPendingQuery((prev) => ({ ...prev, page: 1, keyword: e.target.value || undefined }))}
										/>
										<Button
											type="primary"
											disabled={!canManage || pendingSelectedTaskIds.length === 0}
											onClick={() => openDecision(pendingSelectedTaskIds, "approve")}
										>
											批量同意
										</Button>
										<Button
											danger
											disabled={!canManage || pendingSelectedTaskIds.length === 0}
											onClick={() => openDecision(pendingSelectedTaskIds, "reject")}
										>
											批量驳回
										</Button>
									</Space>
									<CompactTable
										rowKey={(record) => record.task?.id}
										rowSelection={{
											selectedRowKeys: selectedPendingRowKeys,
											onChange: setSelectedPendingRowKeys,
										}}
										dataSource={pendingPage.content}
										columns={taskColumns}
										loading={loadingPending}
										pagination={{
											current: pendingPage.page,
											pageSize: pendingPage.size,
											total: pendingPage.total,
											showSizeChanger: true,
											onChange: (page, size) =>
												setPendingQuery((prev) => ({
													...prev,
													page: size && size !== prev.size ? 1 : page,
													size: size || prev.size,
												})),
										}}
									/>
								</Space>
							),
						},
						{
							key: "done",
							label: `已处理 (${donePage.total})`,
							children: (
								<Space direction="vertical" size={12} className="w-full">
									<Space wrap>
										<Input
											allowClear
											placeholder="按数据集/申请人/目标用户搜索"
											style={{ width: 280 }}
											value={doneQuery.keyword || ""}
											onChange={(e) => setDoneQuery((prev) => ({ ...prev, page: 1, keyword: e.target.value || undefined }))}
										/>
										<Select
											allowClear
											placeholder="结果"
											style={{ width: 160 }}
											value={doneQuery.status}
											options={[
												{ label: "已通过", value: "APPROVED" },
												{ label: "已驳回", value: "REJECTED" },
												{ label: "已跳过", value: "SKIPPED" },
											]}
											onChange={(value) => setDoneQuery((prev) => ({ ...prev, page: 1, status: value || undefined }))}
										/>
									</Space>
									<CompactTable
										rowKey={(record) => record.task?.id}
										dataSource={donePage.content}
										columns={doneColumns}
										loading={loadingDone}
										pagination={{
											current: donePage.page,
											pageSize: donePage.size,
											total: donePage.total,
											showSizeChanger: true,
											onChange: (page, size) =>
												setDoneQuery((prev) => ({
													...prev,
													page: size && size !== prev.size ? 1 : page,
													size: size || prev.size,
												})),
										}}
									/>
								</Space>
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
				okButtonProps={{ disabled: !canManage }}
				destroyOnClose
			>
				<Text>审批意见（可选）</Text>
				<Select
					className="mt-2 w-full"
					placeholder="选择常用意见模板（可选）"
					options={APPROVAL_NOTE_TEMPLATES}
					onChange={(value: string) => setDecisionNotes(value)}
					allowClear
				/>
				<Input.TextArea
					rows={4}
					className="mt-2"
					value={decisionNotes}
					onChange={(event) => setDecisionNotes(event.target.value)}
					placeholder="请输入备注"
				/>
			</Modal>

			<Drawer title="审批详情" open={detailOpen} width={760} onClose={() => setDetailOpen(false)} destroyOnClose>
				<Space direction="vertical" size={12} className="w-full">
					<Descriptions bordered size="small" column={1}>
						<Descriptions.Item label="数据集">{detail?.request?.datasetName || "-"}</Descriptions.Item>
						<Descriptions.Item label="申请人">{detail?.request?.requesterName || detail?.request?.requesterUsername || "-"}</Descriptions.Item>
						<Descriptions.Item label="目标用户">{detail?.request?.targetName || detail?.request?.targetUsername || "-"}</Descriptions.Item>
						<Descriptions.Item label="申请状态">
							<Tag color={statusColor(detail?.request?.status)}>{detail?.request?.status || "-"}</Tag>
						</Descriptions.Item>
						<Descriptions.Item label="生效状态">
							<Tag color={statusColor(detail?.effectiveStatus)}>{detail?.effectiveStatus || "-"}</Tag>
						</Descriptions.Item>
						<Descriptions.Item label="有效期">{`${formatDate(detail?.request?.validFrom)} ~ ${formatDate(detail?.request?.validTo)}`}</Descriptions.Item>
						<Descriptions.Item label="审批说明">{detail?.request?.decisionNotes || "-"}</Descriptions.Item>
					</Descriptions>

					<Card title="审批链路" size="small" loading={detailLoading}>
						<CompactTable
							rowKey={(row) => row?.id || `${row?.stepOrder || 0}-${row?.approverRole || "NA"}`}
							dataSource={Array.isArray(detail?.steps) ? detail?.steps : []}
							pagination={false}
							columns={[
								{ title: "步骤", dataIndex: "stepOrder", width: 80 },
								{ title: "角色", dataIndex: "approverRole", width: 160 },
								{ title: "部门", dataIndex: "deptCode", width: 140, render: (v) => v || "-" },
								{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag color={statusColor(v)}>{v || "-"}</Tag> },
								{ title: "审批人", dataIndex: "decidedBy", width: 140, render: (v) => v || "-" },
								{ title: "时间", dataIndex: "decidedAt", width: 180, render: (v) => formatDate(v) , sorter: (a, b) => { const ta = a.decidedAt ? new Date(a.decidedAt as any).getTime() : 0; const tb = b.decidedAt ? new Date(b.decidedAt as any).getTime() : 0; return ta - tb; } },
								{ title: "意见", dataIndex: "decisionNotes", render: (v) => v || "-" },
							]}
						/>
					</Card>

					<Card title="授权结果" size="small" loading={detailLoading}>
						<Descriptions bordered size="small" column={1}>
							<Descriptions.Item label="授权类型">{detail?.grant?.grantType || "-"}</Descriptions.Item>
							<Descriptions.Item label="授权用户">{detail?.grant?.granteeName || detail?.grant?.granteeUsername || "-"}</Descriptions.Item>
							<Descriptions.Item label="权限">
								<Space>
									<Tag color={detail?.grant?.canQuery ? "green" : "default"}>查询</Tag>
									<Tag color={detail?.grant?.canPreview ? "green" : "default"}>预览</Tag>
								</Space>
							</Descriptions.Item>
							<Descriptions.Item label="授权有效期">{`${formatDate(detail?.grant?.validFrom)} ~ ${formatDate(detail?.grant?.validTo)}`}</Descriptions.Item>
						</Descriptions>
					</Card>
				</Space>
			</Drawer>
		</div>
	);
}
