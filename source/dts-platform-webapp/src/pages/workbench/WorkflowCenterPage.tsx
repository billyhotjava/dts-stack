import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Card, Select, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import workbenchService, { type WorkbenchTodoItem } from "@/api/services/workbenchService";
import { RouterLink } from "@/routes/components/router-link";

const { Text } = Typography;

const TYPE_OPTIONS = [
	{ label: "全部", value: "ALL" },
	{ label: "权限审批", value: "ACCESS_APPROVAL" },
	{ label: "质量工单", value: "QUALITY" },
	{ label: "结构漂移", value: "SCHEMA_DRIFT" },
];

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function Page() {
	const [todos, setTodos] = useState<WorkbenchTodoItem[]>([]);
	const [loading, setLoading] = useState(false);
	const [typeFilter, setTypeFilter] = useState<string>("ALL");

	const loadTodos = async () => {
		setLoading(true);
		try {
			const list = await workbenchService.todos();
			setTodos(Array.isArray(list) ? (list as WorkbenchTodoItem[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "待办加载失败");
			setTodos([]);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadTodos();
	}, []);

	const filtered = useMemo(() => {
		if (typeFilter === "ALL") return todos;
		return todos.filter((item) => item.type === typeFilter);
	}, [todos, typeFilter]);

	const columns: ColumnsType<WorkbenchTodoItem> = [
		{
			title: "类型",
			dataIndex: "type",
			width: 140,
			render: (value) => <Tag>{value || "TODO"}</Tag>,
		},
		{
			title: "事项",
			dataIndex: "title",
			render: (value) => value || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 140,
			render: (value) => <Text>{value || "-"}</Text>,
		},
		{
			title: "时间",
			dataIndex: "createdAt",
			width: 200,
			render: (value) => formatDate(value),
		},
		{
			title: "操作",
			dataIndex: "action",
			width: 160,
			render: (_, record) => {
				if (record.type === "ACCESS_APPROVAL") {
					return <RouterLink href="/dashboard/security/dataset-access-approval">前往审批</RouterLink>;
				}
				if (record.type === "QUALITY") {
					return <RouterLink href="/dashboard/governance/quality-rules">查看质量</RouterLink>;
				}
				if (record.type === "SCHEMA_DRIFT") {
					return <RouterLink href="/dashboard/catalog/datasets">查看详情</RouterLink>;
				}
				return "-";
			},
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader title="工作台 / 待办事项" description="资产权限审批、质量核查与任务漂移提醒。" />
			<Card
				extra={
					<Select
						value={typeFilter}
						onChange={setTypeFilter}
						options={TYPE_OPTIONS}
						style={{ width: 160 }}
					/>
				}
			>
				{filtered.length ? (
					<Table
						rowKey={(record) => `${record.type}-${record.taskId || record.requestId || record.datasetId || record.title}`}
						columns={columns}
						dataSource={filtered}
						loading={loading}
					/>
				) : (
					<EmptyState title="暂无待办" description="当前没有待处理的事项。" />
				)}
			</Card>
		</div>
	);
}
