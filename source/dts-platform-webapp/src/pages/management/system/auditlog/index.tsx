import { Button, Modal, Select, Table, Tag, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Eye } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { AuditLog } from "#/entity";
import { AuditLogService } from "@/api/services/auditLogService";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";
import { useUserRoles } from "@/store/userStore";

export default function AuditLogPage() {
	const [loading, setLoading] = useState(false);
	const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
	const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);
	const [detailModalVisible, setDetailModalVisible] = useState(false);
	const [roleFilter, setRoleFilter] = useState<string>(""); // sysadmin | authadmin | auditadmin | business
	const roles = useUserRoles();
	const isAuthAdmin = roles.some(
		(r: any) => (typeof r === "string" ? r : r?.name || r?.code)?.toUpperCase?.() === "AUTHADMIN",
	);
	const isAuditAdmin = roles.some(
		(r: any) => (typeof r === "string" ? r : r?.name || r?.code)?.toUpperCase?.() === "AUDITADMIN",
	);
	const actorOptions = (() => {
		const opts: { label: string; value: string }[] = [];
		if (isAuthAdmin)
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		if (isAuditAdmin)
			opts.push(
				{ label: "系统管理员", value: "sysadmin" },
				{ label: "授权管理员", value: "authadmin" },
				{ label: "业务系统", value: "business" },
			);
		if (!isAuthAdmin && !isAuditAdmin)
			opts.push({ label: "系统管理员", value: "sysadmin" }, { label: "安全审计员", value: "auditadmin" });
		const map = new Map<string, string>();
		for (const o of opts) map.set(o.value, o.label);
		return Array.from(map.entries()).map(([value, label]) => ({ value, label }));
	})();

	// 加载审计日志列表
	const loadAuditLogs = useCallback(async (page = 1, pageSize = 10) => {
		setLoading(true);
		try {
			const response = await AuditLogService.getAuditLogs(page - 1, pageSize, "id,desc");

			setAuditLogs(response as AuditLog[]);
		} catch (error: any) {
			console.error("获取审计日志失败:", error);
			toast.error(`加载审计日志失败: ${error.message || "未知错误"}`);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		loadAuditLogs();
	}, [loadAuditLogs]);

	const handleTableChange = (pagination: any) => {
		loadAuditLogs(pagination.current, pagination.pageSize);
	};

	const handleViewDetail = (record: AuditLog) => {
		setSelectedLog(record);
		setDetailModalVisible(true);
	};

	const getActionTagColor = (action: string) => {
		switch (action.toLowerCase()) {
			case "create":
				return "green";
			case "update":
			case "approve":
				return "blue";
			case "delete":
			case "reject":
				return "red";
			default:
				return "default";
		}
	};

	// 表格列定义
	const columns: ColumnsType<AuditLog> = [
		{
			title: "记录编号",
			dataIndex: "id",
			width: 80,
			render: (id: number) => (
				<Text variant="body2" className="font-mono">
					#{id}
				</Text>
			),
		},
		{
			title: "操作类型",
			dataIndex: "action",
			width: 120,
			render: (action: string) => <Tag color={getActionTagColor(action)}>{action}</Tag>,
		},
		{
			title: "目标",
			dataIndex: "target",
			width: 150,
		},
		{
			title: "操作人",
			dataIndex: "actor",
			width: 120,
		},
		{
			title: "内容",
			dataIndex: "details",
			ellipsis: true,
			render: (details: string) => (
				<Tooltip title={details}>
					<span>{details}</span>
				</Tooltip>
			),
		},
		{
			title: "创建时间",
			dataIndex: "at",
			width: 180,
			render: (date: string) => new Date(date).toLocaleString(),
			sorter: true,
		},
		{
			title: "操作",
			key: "action",
			width: 100,
			render: (_, record) => (
				<Button type="link" icon={<Eye className="h-4 w-4" />} onClick={() => handleViewDetail(record)}>
					查看详情
				</Button>
			),
		},
	];

	return (
		<div className="p-4">
			<Card>
				<CardHeader>
					<div className="flex items-center justify-between gap-3">
						<div>
							<h2 className="text-2xl font-bold">日志审计</h2>
							<p className="text-muted-foreground">查看系统操作日志</p>
						</div>
						<div className="flex items-center gap-2">
							<label className="text-sm text-muted-foreground">角色筛选</label>
							<Select
								allowClear
								placeholder="请选择角色"
								options={actorOptions}
								className="min-w-[160px]"
								value={roleFilter || undefined}
								onChange={(val) => setRoleFilter(val || "")}
							/>
						</div>
					</div>
				</CardHeader>
				<CardContent>
					<Table
						columns={columns}
						dataSource={
							roleFilter
								? roleFilter === "business"
									? auditLogs.filter(
											(x) => !["sysadmin", "authadmin", "auditadmin"].includes((x.actor || "").toLowerCase()),
										)
									: auditLogs.filter((x) => (x.actor || "").toLowerCase() === roleFilter.toLowerCase())
								: auditLogs
						}
						loading={loading}
						pagination={{
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
						onChange={handleTableChange}
						rowKey="id"
					/>
				</CardContent>
			</Card>

			<Modal
				title="审计日志详情"
				open={detailModalVisible}
				onCancel={() => {
					setDetailModalVisible(false);
					setSelectedLog(null);
				}}
				footer={null}
				width={800}
			>
				{selectedLog && (
					<div className="space-y-4">
						<div className="grid grid-cols-2 gap-4">
							<div>
								<Text variant="body2" className="text-muted-foreground">
									记录编号
								</Text>
								<Text variant="body1">#{selectedLog.id}</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									操作类型
								</Text>
								{(() => {
									const typeMap: Record<string, { text: string; color: string }> = {
										CREATE: { text: "创建", color: "blue" },
										UPDATE: { text: "更新", color: "orange" },
										DELETE: { text: "删除", color: "red" },
										APPROVE: { text: "批准", color: "green" },
										REJECT: { text: "拒绝", color: "purple" },
									};
									const config = typeMap[selectedLog.action.toUpperCase()] || {
										text: selectedLog.action,
										color: "default",
									};
									return <Tag color={config.color}>{config.text}</Tag>;
								})()}
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									目标
								</Text>
								<Text variant="body1">{selectedLog.target || "-"}</Text>
							</div>
							<div>
								<Text variant="body2" className="text-muted-foreground">
									操作人
								</Text>
								<Text variant="body1">{selectedLog.actor}</Text>
							</div>
							<div className="col-span-2">
								<Text variant="body2" className="text-muted-foreground">
									创建时间
								</Text>
								<Text variant="body1">{new Date(selectedLog.at).toLocaleString()}</Text>
							</div>
							{selectedLog.result && (
								<div className="col-span-2">
									<Text variant="body2" className="text-muted-foreground">
										结果
									</Text>
									<Text variant="body1">{selectedLog.result}</Text>
								</div>
							)}
						</div>
						<div>
							<Text variant="body2" className="text-muted-foreground">
								内容详情
							</Text>
							<div className="mt-2 p-3 bg-muted rounded-md">
								<Text variant="body1">{selectedLog.details}</Text>
							</div>
						</div>
					</div>
				)}
			</Modal>
		</div>
	);
}
