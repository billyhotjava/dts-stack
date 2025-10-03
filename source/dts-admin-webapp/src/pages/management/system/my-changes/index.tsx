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
	personnelSecurityLevel: "人员密级",
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

	const translateSecurityLevel = (field: string | undefined, value: unknown) => {
		if (typeof value !== "string") {
			return null;
		}
		const upper = value.toUpperCase();
		if (
			field &&
			(field === "personSecurityLevel" || field === "userSecurityLevel" || field === "personnelSecurityLevel")
		) {
			return PERSON_SECURITY_LEVEL_LABEL_MAP[upper] ?? null;
		}
		return PERSON_SECURITY_LEVEL_LABEL_MAP[upper] ?? null;
	};

const isPlainObject = (val: unknown): val is Record<string, unknown> => Boolean(val) && typeof val === "object" && !Array.isArray(val);

const valuesEqual = (a: unknown, b: unknown): boolean => {
	if (a === b) {
		return true;
	}
	if (Array.isArray(a) && Array.isArray(b)) {
		if (a.length !== b.length) {
			return false;
		}
		return a.every((item, index) => valuesEqual(item, b[index]));
	}
	if (isPlainObject(a) && isPlainObject(b)) {
		const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
		for (const key of keys) {
			if (!valuesEqual(a[key], b[key])) {
				return false;
			}
		}
		return true;
	}
	return false;
};

const extractDiffEntries = (beforeValue: unknown, afterValue: unknown) => {
	const beforeEntries: Array<{ field: string; value: unknown }> = [];
	const afterEntries: Array<{ field: string; value: unknown }> = [];

	const appendEntry = (field: string, before: unknown, after: unknown) => {
		if (!valuesEqual(before, after)) {
			beforeEntries.push({ field, value: before });
			afterEntries.push({ field, value: after });
		}
	};

	if (isPlainObject(beforeValue) || isPlainObject(afterValue)) {
		const beforeObj = isPlainObject(beforeValue) ? beforeValue : {};
		const afterObj = isPlainObject(afterValue) ? afterValue : {};
		const keys = new Set([...Object.keys(beforeObj), ...Object.keys(afterObj)]);
		for (const key of keys) {
			appendEntry(key, beforeObj[key], afterObj[key]);
		}
	} else if (Array.isArray(beforeValue) || Array.isArray(afterValue)) {
		if (!valuesEqual(beforeValue, afterValue)) {
			beforeEntries.push({ field: "value", value: beforeValue });
			afterEntries.push({ field: "value", value: afterValue });
		}
	} else if (!valuesEqual(beforeValue, afterValue)) {
		beforeEntries.push({ field: "value", value: beforeValue });
		afterEntries.push({ field: "value", value: afterValue });
	}

	return { beforeEntries, afterEntries };
};

const formatPrimitiveValue = (field: string | undefined, val: unknown) => {
	if (val === null || val === undefined || val === "") {
		return "-";
	}
	if (typeof val === "boolean") {
		return val ? "是" : "否";
	}
	if (typeof val === "string") {
		const translated = translateSecurityLevel(field, val);
		return translated ?? val;
	}
	if (typeof val === "number") {
		return String(val);
	}
	return String(val ?? "-");
};

const renderValueFragments = (field: string | undefined, value: unknown) => {
	if (Array.isArray(value)) {
		const formatted = value
			.map((item) => (typeof item === "object" ? JSON.stringify(item) : formatPrimitiveValue(field, item)))
			.filter((entry) => entry !== "-")
			.join("、");
		return formatted || "-";
	}
	if (isPlainObject(value)) {
		return JSON.stringify(value);
	}
	return formatPrimitiveValue(field, value);
};

const renderChangeValue = (
	value: ChangeRequest["originalValue"],
	otherValue: ChangeRequest["updatedValue"],
	side: "before" | "after",
) => {
	const { beforeEntries, afterEntries } = extractDiffEntries(value, otherValue);
	const entries = side === "before" ? beforeEntries : afterEntries;
	if (entries.length === 0) {
		return "-";
	}
	const chips = entries
		.map(({ field, value: entryValue }) => {
			const labelKey = field && field !== "value" ? field : side === "before" ? "原值" : "修改值";
			const label = FIELD_LABELS[labelKey] ?? labelKey;
			const display = renderValueFragments(field !== "value" ? field : undefined, entryValue);
			if (!display || display === "-") {
				return null;
			}
			return `<${label}=${display}>`;
		})
		.filter(Boolean) as string[];

	if (chips.length === 0) {
		return "-";
	}

	return (
		<div className="flex max-h-32 flex-col gap-1 overflow-auto pr-1">
			{chips.map((chip) => (
				<div key={chip} className="rounded-md bg-muted/40 px-2 py-1 text-xs text-foreground break-all">
					{chip}
				</div>
			))}
		</div>
	);
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
			render: (_value, record) => renderChangeValue(record.originalValue, record.updatedValue, "before"),
		},
		{
			title: "修改值",
			dataIndex: "updatedValue",
			width: 220,
			render: (_value, record) => renderChangeValue(record.originalValue, record.updatedValue, "after"),
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
