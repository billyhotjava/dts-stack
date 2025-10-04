import { Button, Input, Modal, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { toast } from "sonner";
import type { ApprovalRequest, ApprovalRequestDetail, KeycloakUser } from "#/服务端";
import { KeycloakApprovalService } from "@/api/services/approvalService";
import useUserStore from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";
import { DetailItem, DetailSection } from "@/components/detail/DetailSection";

// 创建一个新的类型，用于处理后端只返回基本审批信息的情况
interface BasicApprovalRequest extends Omit<ApprovalRequest, "items"> {
	// 不包含items字段，因为后端列表接口不返回这个字段
}

// 用户信息变更详情
interface UserInfoChange {
	type: "CREATE_USER" | "UPDATE_USER" | "DELETE_USER";
	before?: KeycloakUser;
	after?: KeycloakUser;
}

export default function ApprovalPage() {
	const [approvals, setApprovals] = useState<BasicApprovalRequest[]>([]);
	const [loading, setLoading] = useState(false);
	const [selectedRequest, setSelectedRequest] = useState<ApprovalRequestDetail | null>(null);
	const [actionLoading, setActionLoading] = useState(false);
	const [rejectReason, setRejectReason] = useState(""); // 用于存储拒绝理由
	const [showRejectModal, setShowRejectModal] = useState(false); // 用于显示拒绝理由模态框
	const [userInfoChange, setUserInfoChange] = useState<UserInfoChange | null>(null); // 用于存储用户信息变更详情

	const { userInfo } = useUserStore();

	// 加载审批请求列表
	const loadApprovals = useCallback(async () => {
		setLoading(true);
		try {
			// 后端列表接口只返回基本审批信息，不包含items字段
			const data = await KeycloakApprovalService.getApprovalRequests();
			setApprovals(data as BasicApprovalRequest[]);
		} catch (error: any) {
			console.error("Error loading approvals:", error);
			toast.error(`加载审批列表失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, []);

	// 解析用户信息变更详情
	const parseUserInfoChange = (request: ApprovalRequestDetail): UserInfoChange | null => {
		try {
			// 根据审批类型确定变更类型
			let changeType: "CREATE_USER" | "UPDATE_USER" | "DELETE_USER";
			switch (request.type) {
				case "CREATE_USER":
					changeType = "CREATE_USER";
					break;
				case "UPDATE_USER":
					changeType = "UPDATE_USER";
					break;
				case "DELETE_USER":
					changeType = "DELETE_USER";
					break;
				default:
					// 对于角色变更等其他类型，我们暂时不处理
					return null;
			}

			// 解析变更前后的用户信息
			let beforeUser: KeycloakUser | undefined;
			let afterUser: KeycloakUser | undefined;

			if (request.items && request.items.length > 0) {
				const payload = request.items[0].payload;
				if (payload) {
					// 如果payload是JSON字符串，则解析它
					if (typeof payload === "string" && payload.trim().startsWith("{")) {
						afterUser = JSON.parse(payload);
					} else if (typeof payload === "object") {
						afterUser = payload as KeycloakUser;
					}
				}
			}

			// 创建用户信息变更对象
			const change: UserInfoChange = {
				type: changeType,
				before: beforeUser,
				after: afterUser,
			};

			return change;
		} catch (error) {
			console.error("Error parsing user info change:", error);
			return null;
		}
	};

	// 查看审批详情
	const handleViewDetail = async (request: BasicApprovalRequest) => {
		setLoading(true);
		try {
			// 详情接口会返回完整的审批信息，包括items字段
			const detail = await KeycloakApprovalService.getApprovalRequestById(request.id);
			setSelectedRequest(detail);

			// 解析用户信息变更详情
			const change = parseUserInfoChange(detail);
			setUserInfoChange(change);
		} catch (error: any) {
			console.error("Error loading approval detail:", error);
			toast.error(`加载审批详情失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	};

	// 审批通过
	const handleApprove = async (requestId: number) => {
		setActionLoading(true);
		try {
			const data = {
				approver: userInfo.username || "unknown",
				note: "Approved via UI",
			};
			await KeycloakApprovalService.approveRequest(requestId, data);
			toast.success("审批通过成功");
			loadApprovals();
			setSelectedRequest(null);
			setUserInfoChange(null);
		} catch (error: any) {
			console.error("Error approving request:", error);
			toast.error(`审批失败: ${error.message || "未知错误"}`);
		} finally {
			setActionLoading(false);
		}
	};

	// 显示拒绝理由输入框
	const showRejectModalHandler = () => {
		setShowRejectModal(true);
	};

	// 执行审批拒绝
	const handleReject = async () => {
		if (!selectedRequest) return;

		setActionLoading(true);
		try {
			const data = {
				approver: userInfo.username || "unknown",
				note: rejectReason || "Rejected via UI",
			};
			await KeycloakApprovalService.rejectRequest(selectedRequest.id, data);
			toast.success("审批拒绝成功");
			setShowRejectModal(false);
			setRejectReason("");
			loadApprovals();
			setSelectedRequest(null);
			setUserInfoChange(null);
		} catch (error: any) {
			console.error("Error rejecting request:", error);
			toast.error(`审批失败: ${error.message || "未知错误"}`);
		} finally {
			setActionLoading(false);
		}
	};

	// 表格列定义
	const columns: ColumnsType<BasicApprovalRequest> = [
		{
			title: "请求编号",
			dataIndex: "id",
			width: 80,
			render: (id: number) => (
				<Text variant="body2" className="font-mono">
					#{id}
				</Text>
			),
		},
		{
			title: "请求类型",
			dataIndex: "type",
			width: 120,
			render: (type: string) => {
				const typeMap: Record<string, { text: string; color: string }> = {
					CREATE_USER: { text: "创建用户", color: "blue" },
					UPDATE_USER: { text: "更新用户", color: "orange" },
					DELETE_USER: { text: "删除用户", color: "red" },
					GRANT_ROLE: { text: "分配角色", color: "green" },
					REVOKE_ROLE: { text: "移除角色", color: "purple" },
				};
				const config = typeMap[type] || { text: type, color: "default" };
				return <Tag color={config.color}>{config.text}</Tag>;
			},
		},
		{
			title: "申请人",
			dataIndex: "requester",
			width: 120,
		},
		{
			title: "说明",
			dataIndex: "reason",
			ellipsis: true,
			render: (reason: string) => (
				<Tooltip title={reason}>
					<span>{reason}</span>
				</Tooltip>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (status: string) => {
				const statusMap: Record<
					string,
					{ text: string; variant: "default" | "secondary" | "success" | "warning" | "destructive" }
				> = {
					PENDING: { text: "待审批", variant: "warning" },
					APPROVED: { text: "已批准", variant: "success" },
					REJECTED: { text: "已拒绝", variant: "destructive" },
					APPLIED: { text: "已应用", variant: "success" },
					FAILED: { text: "失败", variant: "destructive" },
				};
				const config = statusMap[status] || { text: status, variant: "default" };
				return <Badge variant={config.variant}>{config.text}</Badge>;
			},
		},
		{
			title: "创建时间",
			dataIndex: "createdAt",
			width: 160,
			render: (createdAt: string) => new Date(createdAt).toLocaleString("zh-CN"),
		},
		{
			title: "审批人",
			dataIndex: "approver",
			width: 120,
			render: (approver: string) => approver || "-",
		},
		{
			title: "操作",
			key: "action",
			width: 120,
			fixed: "right",
			render: (_, record) => (
				<div className="flex gap-1">
					<Button size="small" onClick={() => handleViewDetail(record)}>
						查看
					</Button>
					{/* 根据需求，移除处理按钮 */}
				</div>
			),
		},
	];

	// 详情模态框列定义

	// 初始化加载
	useEffect(() => {
		loadApprovals();
	}, [loadApprovals]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between">
						<div>
							<h2 className="text-2xl font-bold">审批管理</h2>
							<p className="text-muted-foreground">管理用户操作审批请求</p>
						</div>
					</div>
				</CardHeader>
				<CardContent>
					<Table
						rowKey="id"
						columns={columns}
						dataSource={approvals as any[]}
						loading={loading}
						scroll={{ x: 1000 }}
						pagination={{
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
					/>
				</CardContent>
			</Card>

			{/* 审批详情模态框 */}
			<Modal
				title="审批请求详情"
				open={!!selectedRequest}
				onCancel={() => {
					setSelectedRequest(null);
					setUserInfoChange(null);
				}}
				footer={[
					selectedRequest?.status === "PENDING" && (
						<div key="actions" className="flex justify-end gap-2">
							<Button onClick={showRejectModalHandler} loading={actionLoading}>
								拒绝
							</Button>
							<Button
								type="primary"
								onClick={() => selectedRequest && handleApprove(selectedRequest.id)}
								loading={actionLoading}
							>
								批准
							</Button>
						</div>
					),
					<Button
						key="close"
						onClick={() => {
							setSelectedRequest(null);
							setUserInfoChange(null);
						}}
					>
						关闭
					</Button>,
				]}
				width={800}
			>
				{selectedRequest && (() => {
					const statusMeta = getApprovalStatusMeta(selectedRequest.status);
					const changeRows = userInfoChange ? buildUserChangeRows(userInfoChange) : [];
					return (
						<div className="space-y-4">
							<DetailSection title="基础信息">
								<DetailItem label="请求编号" value={`#${selectedRequest.id}`} monospace />
								<DetailItem label="类型" value={getApprovalTypeLabel(selectedRequest.type)} />
								<DetailItem label="申请人" value={selectedRequest.requester || "-"} />
								<DetailItem label="状态" value={<Badge variant={statusMeta.variant}>{statusMeta.label}</Badge>} />
								{selectedRequest.category && <DetailItem label="分类" value={selectedRequest.category} />}
							</DetailSection>

							<DetailSection title="时间与审批信息">
								<DetailItem label="创建时间" value={formatDateTime(selectedRequest.createdAt)} />
								<DetailItem label="审批时间" value={formatDateTime(selectedRequest.decidedAt)} />
								<DetailItem label="审批人" value={selectedRequest.approver || "-"} />
								<DetailItem label="重试次数" value={selectedRequest.retryCount != null ? selectedRequest.retryCount : 0} />
							</DetailSection>

							{selectedRequest.reason && (
								<DetailSection title="申请说明" columns={1}>
									<DetailItem
										label="申请说明"
										value={<div className="rounded-md bg-muted px-3 py-2 leading-relaxed">{selectedRequest.reason}</div>}
									/>
								</DetailSection>
							)}

							{selectedRequest.decisionNote && (
								<DetailSection title="审批意见" columns={1}>
									<DetailItem
										label="审批意见"
										value={<div className="rounded-md bg-muted px-3 py-2 leading-relaxed">{selectedRequest.decisionNote}</div>}
									/>
								</DetailSection>
							)}

							{selectedRequest.errorMessage && (
								<DetailSection title="错误信息" columns={1}>
									<DetailItem
										label="错误描述"
										value={
											<div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
												{selectedRequest.errorMessage}
											</div>
										}
									/>
								</DetailSection>
							)}

							{changeRows.length > 0 && userInfoChange && (
								<DetailSection
									title={getUserInfoChangeTitle(userInfoChange.type)}
									description="对比关键字段的变更前后差异"
									columns={1}
								>
									<div className="space-y-3">
										{changeRows.map(({ key, label, before, after }) => (
											<UserChangeField key={key} label={label} before={before} after={after} />
										))}
									</div>
								</DetailSection>
							)}

							{!userInfoChange && (
								<DetailSection title="审批项" columns={1} description="审批执行时提交的具体事项">
									{selectedRequest.items && selectedRequest.items.length > 0 ? (
										<div className="space-y-3">
											{selectedRequest.items.map((item, index) => (
												<ApprovalItemCard
													key={item.id ?? `${item.targetKind}-${item.targetId}-${index}`}
													item={item}
													index={index}
												/>
											))}
										</div>
									) : (
										<div className="rounded-md bg-muted/60 px-3 py-6 text-center text-sm text-muted-foreground">
											暂无审批项
										</div>
									)}
								</DetailSection>
							)}
						</div>
					);
				})()}
			</Modal>

			{/* 拒绝理由输入模态框 */}
			<Modal
				title="拒绝审批"
				open={showRejectModal}
				onCancel={() => {
					setShowRejectModal(false);
					setRejectReason("");
				}}
				onOk={handleReject}
				okText="确认拒绝"
				cancelText="取消"
				confirmLoading={actionLoading}
			>
				<div className="space-y-4">
					<p>请填写拒绝理由：</p>
					<Input.TextArea
						rows={4}
						value={rejectReason}
						onChange={(e) => setRejectReason(e.target.value)}
						placeholder="请输入拒绝理由..."
					/>
				</div>
			</Modal>
		</div>
	);
}

type BadgeAppearance = "default" | "secondary" | "destructive" | "info" | "warning" | "success" | "error" | "outline";

function getApprovalTypeLabel(type: string): string {
	switch (type) {
		case "CREATE_USER":
			return "创建用户";
		case "UPDATE_USER":
			return "更新用户";
		case "DELETE_USER":
			return "删除用户";
		case "GRANT_ROLE":
			return "分配角色";
		case "REVOKE_ROLE":
			return "移除角色";
		default:
			return type;
	}
}

function getUserInfoChangeTitle(type: UserInfoChange["type"]): string {
	switch (type) {
		case "CREATE_USER":
			return "创建用户信息";
		case "UPDATE_USER":
			return "用户信息变更";
		case "DELETE_USER":
			return "删除用户信息";
		default:
			return "用户信息";
	}
}

function getApprovalStatusMeta(status: string): { label: string; variant: BadgeAppearance } {
	switch (status) {
		case "PENDING":
			return { label: "待审批", variant: "warning" };
		case "APPROVED":
			return { label: "已批准", variant: "success" };
		case "REJECTED":
			return { label: "已拒绝", variant: "destructive" };
		case "APPLIED":
			return { label: "已应用", variant: "success" };
		case "FAILED":
			return { label: "失败", variant: "error" };
		default:
			return { label: status || "-", variant: "default" };
	}
}

function formatDateTime(value?: string | null): string {
	if (!value) {
		return "-";
	}
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString("zh-CN");
}

type ChangeRow = {
	key: string;
	label: string;
	before: unknown;
	after: unknown;
};

function buildUserChangeRows(change: UserInfoChange): ChangeRow[] {
	const rows: ChangeRow[] = [
		{ key: "username", label: "用户名", before: change.before?.username, after: change.after?.username },
		{ key: "email", label: "邮箱", before: change.before?.email, after: change.after?.email },
		{ key: "firstName", label: "名", before: change.before?.firstName, after: change.after?.firstName },
		{ key: "lastName", label: "姓", before: change.before?.lastName, after: change.after?.lastName },
		{ key: "enabled", label: "启用状态", before: change.before?.enabled, after: change.after?.enabled },
		{ key: "emailVerified", label: "邮箱验证", before: change.before?.emailVerified, after: change.after?.emailVerified },
		{ key: "attributes", label: "附加属性", before: change.before?.attributes, after: change.after?.attributes },
	];

	return rows.filter((row) => row.before !== undefined || row.after !== undefined);
}

type ChangeFieldProps = {
	label: string;
	before: unknown;
	after: unknown;
};

function UserChangeField({ label, before, after }: ChangeFieldProps) {
	return (
		<div className="space-y-3 rounded-lg border border-border/70 bg-background/80 p-3">
			<Text variant="body2" className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
				{label}
			</Text>
			<div className="grid gap-3 md:grid-cols-2">
				<div>
					<p className="text-[11px] uppercase tracking-wide text-muted-foreground">变更前</p>
					<div className="mt-1 text-sm">{renderFieldValue(before)}</div>
				</div>
				<div>
					<p className="text-[11px] uppercase tracking-wide text-muted-foreground">变更后</p>
					<div className="mt-1 text-sm">{renderFieldValue(after)}</div>
				</div>
			</div>
		</div>
	);
}

function renderFieldValue(value: unknown): ReactNode {
	if (value === null || value === undefined) {
		return <span className="text-muted-foreground">-</span>;
	}
	if (typeof value === "boolean") {
		return value ? "是" : "否";
	}
	if (typeof value === "number") {
		return value.toString();
	}
	if (typeof value === "string") {
		const trimmed = value.trim();
		return trimmed ? trimmed : <span className="text-muted-foreground">-</span>;
	}
	if (Array.isArray(value)) {
		if (value.length === 0) {
			return <span className="text-muted-foreground">-</span>;
		}
		return (
			<div className="flex flex-wrap gap-1">
				{value.map((item, index) => (
					<span key={`${String(item)}-${index}`} className="rounded bg-muted px-2 py-0.5 text-xs text-foreground/80">
						{String(item)}
					</span>
				))}
			</div>
		);
	}
	if (typeof value === "object") {
		try {
			return (
				<pre className="whitespace-pre-wrap break-all rounded-md bg-muted/60 px-2 py-1 font-mono text-xs leading-relaxed">
					{JSON.stringify(value, null, 2)}
				</pre>
			);
		} catch (error) {
			return String(value);
		}
	}
	return String(value);
}

type ApprovalItemDetail = ApprovalRequestDetail["items"][number];

function ApprovalItemCard({ item, index }: { item: ApprovalItemDetail; index: number }) {
	return (
		<div className="space-y-3 rounded-lg border border-dashed border-border/60 bg-background/80 p-4">
			<div className="flex items-center justify-between text-xs text-muted-foreground">
				<span>审批项 #{index + 1}</span>
				<span>序号 {item.seqNumber}</span>
			</div>
			<div className="grid gap-4 md:grid-cols-2">
				<div>
					<p className="text-[11px] uppercase tracking-wide text-muted-foreground">目标类型</p>
					<div className="mt-1 font-mono text-sm">{item.targetKind || "-"}</div>
				</div>
				<div>
					<p className="text-[11px] uppercase tracking-wide text-muted-foreground">目标标识</p>
					<div className="mt-1 break-all font-mono text-sm">{item.targetId || "-"}</div>
				</div>
				<div className="md:col-span-2">
					<p className="text-[11px] uppercase tracking-wide text-muted-foreground">数据</p>
					<div className="mt-1 rounded-md bg-muted/60 px-3 py-2">
						<pre className="whitespace-pre-wrap break-all font-mono text-xs leading-relaxed">
							{formatPayloadDisplay(item.payload)}
						</pre>
					</div>
				</div>
			</div>
		</div>
	);
}

function formatPayloadDisplay(payload: string): string {
	if (!payload) {
		return "-";
	}
	try {
		const parsed = JSON.parse(payload);
		return typeof parsed === "object" ? JSON.stringify(parsed, null, 2) : String(parsed);
	} catch (error) {
		return payload;
	}
}
