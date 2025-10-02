import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Select, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type { ChangeRequest } from "@/admin/types";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader } from "@/ui/card";
import { Text } from "@/ui/typography";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";

const statusConfig: Record<
	string,
	{
		label: string;
		tagColor: string;
		badge: "default" | "secondary" | "success" | "warning" | "destructive" | "outline";
	}
> = {
	PENDING: { label: "待审批", tagColor: "gold", badge: "warning" },
	PROCESSING: { label: "处理中", tagColor: "blue", badge: "warning" },
	APPROVED: { label: "已批准", tagColor: "green", badge: "success" },
	REJECTED: { label: "已拒绝", tagColor: "red", badge: "destructive" },
	APPLIED: { label: "已应用", tagColor: "blue", badge: "success" },
	FAILED: { label: "执行失败", tagColor: "volcano", badge: "destructive" },
	DRAFT: { label: "草稿", tagColor: "default", badge: "secondary" },
};

const resourceMap: Record<string, string> = {
	USER: "用户",
	ROLE: "角色",
	MENU: "菜单",
	CONFIG: "配置",
};

const categoryLabels: Record<string, string> = {
	USER_MANAGEMENT: "用户管理",
	ROLE_MANAGEMENT: "角色管理",
	PORTAL_MENU: "门户菜单",
	SYSTEM_CONFIG: "系统配置",
	ORGANIZATION: "组织机构",
	CUSTOM_ROLE: "自定义角色",
	ROLE_ASSIGNMENT: "角色授权",
};

const actionMap: Record<string, string> = {
	CREATE: "创建",
	UPDATE: "更新",
	DELETE: "删除",
	SUBMIT: "提交",
	APPROVE: "批准",
	ENABLE: "启用",
	DISABLE: "停用",
	GRANT_ROLE: "分配角色",
	REVOKE_ROLE: "移除角色",
	SET_PERSON_LEVEL: "调整密级",
	RESET_PASSWORD: "重置密码",
};

const PERSON_SECURITY_LEVEL_LABEL_MAP = PERSON_SECURITY_LEVELS.reduce<Record<string, string>>((acc, item) => {
	acc[item.value] = item.label;
	return acc;
}, {});

const FIELD_LABELS: Record<string, string> = {
	username: "用户名",
	email: "邮箱",
	displayName: "显示名称",
	name: "名称",
	code: "编码",
	roles: "角色",
	userSecurityLevel: "人员密级",
	personSecurityLevel: "人员密级",
	securityLevel: "密级",
	parentId: "父级编号",
	key: "配置键",
	value: "配置值",
	metadata: "元数据",
	sortOrder: "排序",
	component: "页面组件",
	path: "路径",
	id: "标识",
	icon: "图标",
	operations: "操作列表",
	datasetIds: "数据集",
	scopeOrgId: "作用范围",
	description: "描述",
	resourceId: "资源标识",
	role: "角色",
	userSecurityLevelBefore: "原人员密级",
	userSecurityLevelAfter: "新人员密级",
};

const CATEGORY_FILTER_OPTIONS = [
	{ label: "用户管理", value: "USER_MANAGEMENT" },
	{ label: "角色管理", value: "ROLE_MANAGEMENT" },
];

export default function MyChangeRequestsPage() {
	const { data, isLoading, refetch } = useQuery({
		queryKey: ["admin", "change-requests", "mine", "dashboard"],
		queryFn: adminApi.getMyChangeRequests,
	});
	const [categoryFilter, setCategoryFilter] = useState<string | null>(null);

	const summary = useMemo(() => {
		const counts = new Map<string, number>();
		for (const item of data ?? []) {
			const status = item.status ?? "UNKNOWN";
			counts.set(status, (counts.get(status) ?? 0) + 1);
		}

		return Array.from(counts.entries())
			.map(([status, count]) => ({
				status,
				count,
				config: statusConfig[status] ?? {
					label: status,
					tagColor: "default",
					badge: "default",
				},
			}))
			.sort((a, b) => b.count - a.count);
	}, [data]);

	const filteredChangeRequests = useMemo(() => {
		const source = (data ?? []) as ChangeRequest[];
		const filtered = categoryFilter ? source.filter((item) => item.category === categoryFilter) : source;
		return [...filtered].sort((a, b) => {
			const timeA = a.requestedAt ? new Date(a.requestedAt).getTime() : 0;
			const timeB = b.requestedAt ? new Date(b.requestedAt).getTime() : 0;
			return timeB - timeA;
		});
	}, [categoryFilter, data]);

	const formatPrimitiveValue = (val: unknown) => {
		if (val === null || val === undefined || val === "") {
			return "-";
		}
		if (typeof val === "boolean") {
			return val ? "是" : "否";
		}
		return String(val);
	};

	const renderChangeValue = (value: ChangeRequest["originalValue"]) => {
		if (value === null || value === undefined || value === "") {
			return "-";
		}
		if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
			return <span className="whitespace-pre-wrap break-all">{formatPrimitiveValue(value)}</span>;
		}
		if (Array.isArray(value)) {
			const flattened = value
				.map((item) => (typeof item === "object" ? JSON.stringify(item) : formatPrimitiveValue(item)))
				.filter((item) => item !== "-");
			if (flattened.length === 0) {
				return "-";
			}
			return <span className="whitespace-pre-wrap break-all">{flattened.join("、")}</span>;
		}
		if (typeof value === "object") {
			const entries = Object.entries(value as Record<string, unknown>).filter(([, fieldValue]) => fieldValue !== undefined && fieldValue !== null && fieldValue !== "");
			if (entries.length === 0) {
				return "-";
			}
			return (
				<div className="flex max-h-32 flex-col gap-1 overflow-auto pr-1">
					{entries.map(([field, fieldValue]) => {
						const label = FIELD_LABELS[field] ?? field;
						let display: string;
						if (Array.isArray(fieldValue)) {
							display = fieldValue
								.map((item) => (typeof item === "object" ? JSON.stringify(item) : formatPrimitiveValue(item)))
								.filter((item) => item !== "-")
								.join("、") || "-";
						} else if (typeof fieldValue === "object" && fieldValue !== null) {
							display = JSON.stringify(fieldValue);
						} else {
							display = formatPrimitiveValue(fieldValue);
						}
						return (
							<div key={field} className="rounded-md bg-muted/40 px-2 py-1 text-xs">
								<span className="font-medium text-muted-foreground">{`<${label}, `}</span>
								<span className="break-all text-foreground">{display}</span>
								<span className="font-medium text-muted-foreground">{">"}</span>
							</div>
						);
					})}
				</div>
			);
		}
		return <span className="whitespace-pre-wrap break-all">{formatPrimitiveValue(value)}</span>;
	};

	const columns: ColumnsType<ChangeRequest> = [
		{
			title: "编号",
			dataIndex: "id",
			width: 90,
			render: (id: number) => (
				<Text variant="body2" className="font-mono">
					#{id}
				</Text>
			),
		},
		{
			title: "分类",
			dataIndex: "category",
			width: 140,
			render: (value?: string) => categoryLabels[value ?? ""] ?? value ?? "GENERAL",
		},
		{
			title: "资源类型",
			dataIndex: "resourceType",
			width: 140,
			render: (value: string) => resourceMap[value] ?? value,
		},
		{
			title: "操作",
			dataIndex: "action",
			width: 120,
			render: (value: string) => actionMap[value] ?? value,
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (status: string) => {
				const config = statusConfig[status] ?? {
					label: status,
					tagColor: "default",
					badge: "secondary" as const,
				};
				return <Tag color={config.tagColor}>{config.label}</Tag>;
			},
		},
		{
			title: "提交时间",
			dataIndex: "requestedAt",
			width: 180,
			render: (value?: string) => (value ? new Date(value).toLocaleString("zh-CN") : "-"),
		},
		{
			title: "审批人",
			dataIndex: "decidedBy",
			width: 120,
			render: (value?: string) => value || "-",
		},
		{
			title: "审批时间",
			dataIndex: "decidedAt",
			width: 180,
			render: (value?: string) => (value ? new Date(value).toLocaleString("zh-CN") : "-"),
		},
		{
			title: "说明",
			dataIndex: "reason",
			ellipsis: true,
			render: (value?: string) => value || "-",
		},
		{
			title: "原值",
			dataIndex: "originalValue",
			width: 220,
			render: renderChangeValue,
		},
		{
			title: "修改值",
			dataIndex: "updatedValue",
			width: 220,
			render: renderChangeValue,
		},
	];

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
					<div>
						<h2 className="text-2xl font-bold">我发起的变更</h2>
						<p className="text-muted-foreground">追踪我提交的配置、权限等变更进度</p>
					</div>
					<div className="flex flex-wrap items-center gap-2">
						<Select
						allowClear
						placeholder="操作分类"
						options={CATEGORY_FILTER_OPTIONS}
						value={categoryFilter ?? undefined}
						onChange={(value) => setCategoryFilter(value ? String(value) : null)}
						style={{ width: 160 }}
					/>
						<Button variant="outline" size="sm" onClick={() => refetch()} disabled={isLoading}>
						刷新
					</Button>
				</div>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="flex flex-wrap gap-3">
						{summary.length === 0 ? (
							<Badge variant="secondary">暂无提交的变更</Badge>
						) : (
							summary.map(({ status, count, config }) => (
								<div key={status} className="flex items-center gap-2 rounded-md border px-3 py-2">
									<Badge variant={config.badge}>{count}</Badge>
									<Text variant="body3" className="text-muted-foreground">
										{config.label}
									</Text>
								</div>
							))
						)}
					</div>
					<Table
						rowKey="id"
						loading={isLoading}
						columns={columns}
						dataSource={filteredChangeRequests}
						size="small"
						className="text-sm"
						rowClassName={() => "text-sm"}
						pagination={{
							showSizeChanger: true,
							showQuickJumper: true,
							showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
						}}
						scroll={{ x: "max-content" }}
					/>
				</CardContent>
			</Card>
		</div>
	);
}
