import { Button, Input, Modal, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { ApprovalRequest, ApprovalRequestDetail, KeycloakUser } from "#/keycloak";
import { KeycloakApprovalService } from "@/api/services/approvalService";
import useUserStore from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";

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
			title: "ID",
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
	const detailColumns: ColumnsType<any> = [
		{
			title: "属性",
			dataIndex: "key",
			width: 120,
		},
		{
			title: "값",
			dataIndex: "value",
			render: (value: any) => {
				if (typeof value === "object") {
					return <pre className="text-xs">{JSON.stringify(value, null, 2)}</pre>;
				}
				return value;
			},
		},
	];

	// 用户信息变更详情列定义
	const userChangeColumns: ColumnsType<any> = [
		{
			title: "属性",
			dataIndex: "property",
			width: 150,
		},
		{
			title: "变更前",
			dataIndex: "before",
			width: 200,
			render: (value: any) => {
				if (value === undefined || value === null) return "-";
				if (typeof value === "object") {
					return <pre className="text-xs">{JSON.stringify(value, null, 2)}</pre>;
				}
				return value.toString();
			},
		},
		{
			title: "变更后",
			dataIndex: "after",
			width: 200,
			render: (value: any) => {
				if (value === undefined || value === null) return "-";
				if (typeof value === "object") {
					return <pre className="text-xs">{JSON.stringify(value, null, 2)}</pre>;
				}
				return value.toString();
			},
		},
	];

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
				{selectedRequest && (
					<div className="space-y-4">
						<div className="grid grid-cols-2 gap-4">
							<div>
								<Text variant="body2" className="text-muted-foreground">
									ID
								</Text>
								<Text variant="body1" className="font-mono">
									#{selectedRequest.id}
								</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									类型
								</Text>
								<Text variant="body1">
									{selectedRequest.type === "CREATE_USER"
										? "创建用户"
										: selectedRequest.type === "UPDATE_USER"
											? "更新用户"
											: selectedRequest.type === "DELETE_USER"
												? "删除用户"
												: selectedRequest.type === "GRANT_ROLE"
													? "分配角色"
													: selectedRequest.type === "REVOKE_ROLE"
														? "移除角色"
														: selectedRequest.type}
								</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									申请人
								</Text>
								<Text variant="body1">{selectedRequest.requester}</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									状态
								</Text>
								<Badge
									variant={
										selectedRequest.status === "PENDING"
											? "warning"
											: selectedRequest.status === "APPROVED"
												? "success"
												: selectedRequest.status === "REJECTED"
													? "destructive"
													: "default"
									}
								>
									{selectedRequest.status === "PENDING"
										? "待审批"
										: selectedRequest.status === "APPROVED"
											? "已批准"
											: selectedRequest.status === "REJECTED"
												? "已拒绝"
												: selectedRequest.status === "APPLIED"
													? "已应用"
													: selectedRequest.status === "FAILED"
														? "失败"
														: selectedRequest.status}
								</Badge>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									创建时间
								</Text>
								<Text variant="body1">{new Date(selectedRequest.createdAt).toLocaleString("zh-CN")}</Text>
							</div>
							{selectedRequest.decidedAt && (
								<div>
									<Text variant="body2" className="text-muted-foreground">
										审批时间
									</Text>
									<Text variant="body1">{new Date(selectedRequest.decidedAt).toLocaleString("zh-CN")}</Text>
								</div>
							)}
							{selectedRequest.approver && (
								<div>
									<Text variant="body2" className="text-muted-foreground">
										审批人
									</Text>
									<Text variant="body1">{selectedRequest.approver}</Text>
								</div>
							)}
						</div>

						{selectedRequest.reason && (
							<div>
								<Text variant="body2" className="text-muted-foreground">
									说明
								</Text>
								<div className="p-2 bg-muted rounded">
									<Text variant="body1">{selectedRequest.reason}</Text>
								</div>
							</div>
						)}

						{selectedRequest.decisionNote && (
							<div>
								<Text variant="body2" className="text-muted-foreground">
									审批意见
								</Text>
								<div className="p-2 bg-muted rounded">
									<Text variant="body1">{selectedRequest.decisionNote}</Text>
								</div>
							</div>
						)}

						{selectedRequest.errorMessage && (
							<div>
								<Text variant="body2" className="text-muted-foreground text-destructive">
									错误信息
								</Text>
								<div className="p-2 bg-destructive/10 rounded">
									<Text variant="body1" className="text-destructive">
										{selectedRequest.errorMessage}
									</Text>
								</div>
							</div>
						)}

						{/* 用户信息变更详情 */}
						{userInfoChange && (
							<div>
								<Text variant="body2" className="text-muted-foreground">
									{userInfoChange.type === "CREATE_USER"
										? "创建用户信息"
										: userInfoChange.type === "UPDATE_USER"
											? "用户信息变更"
											: userInfoChange.type === "DELETE_USER"
												? "删除用户信息"
												: "用户信息"}
								</Text>
								<Table
									size="small"
									columns={userChangeColumns}
									dataSource={[
										{
											key: "username",
											property: "用户名",
											before: userInfoChange.before?.username,
											after: userInfoChange.after?.username,
										},
										{
											key: "email",
											property: "邮箱",
											before: userInfoChange.before?.email,
											after: userInfoChange.after?.email,
										},
										{
											key: "firstName",
											property: "名",
											before: userInfoChange.before?.firstName,
											after: userInfoChange.after?.firstName,
										},
										{
											key: "lastName",
											property: "姓",
											before: userInfoChange.before?.lastName,
											after: userInfoChange.after?.lastName,
										},
										{
											key: "enabled",
											property: "启用状态",
											before: userInfoChange.before?.enabled?.toString(),
											after: userInfoChange.after?.enabled?.toString(),
										},
										{
											key: "emailVerified",
											property: "邮箱验证",
											before: userInfoChange.before?.emailVerified?.toString(),
											after: userInfoChange.after?.emailVerified?.toString(),
										},
										{
											key: "attributes",
											property: "附加属性",
											before: userInfoChange.before?.attributes,
											after: userInfoChange.after?.attributes,
										},
									]}
									pagination={false}
								/>
							</div>
						)}

						{/* 原始审批项（对于非用户信息变更的审批） */}
						{!userInfoChange && selectedRequest.items && selectedRequest.items.length > 0 && (
							<div>
								<Text variant="body2" className="text-muted-foreground">
									审批项
								</Text>
								{selectedRequest.items && selectedRequest.items.length > 0 ? (
									<Table
										size="small"
										columns={detailColumns}
										dataSource={selectedRequest.items
											.map((item) => ({
												key: `target-${item.id}`,
												属性: "目标类型/ID",
												값: `${item.targetKind}/${item.targetId}`,
											}))
											.concat(
												selectedRequest.items.flatMap((item) => [
													{
														key: `seq-${item.id}`,
														属性: "序号",
														값: item.seqNumber.toString(),
													},
													{
														key: `payload-${item.id}`,
														属性: "数据",
														값: typeof item.payload === "string" ? item.payload : JSON.stringify(item.payload, null, 2),
													},
												]),
											)}
										pagination={false}
									/>
								) : (
									<div className="p-2 bg-muted rounded text-center text-muted-foreground">暂无审批项</div>
								)}
							</div>
						)}
					</div>
				)}
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
