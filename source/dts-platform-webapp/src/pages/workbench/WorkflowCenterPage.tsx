import { useEffect, useMemo, useState } from "react";
import { Button, Card, Select, Space, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { CheckCircle2, RefreshCw, Shield, Workflow } from "lucide-react";
import { PlatformSummaryCards } from "@/components/console-page";
import { EmptyState } from "@/components/empty-state";
import workbenchService, { type WorkbenchTodoItem } from "@/api/services/workbenchService";
import { useRouter } from "@/routes/hooks";

const TYPE_OPTIONS = [
	{ label: "全部", value: "ALL" },
	{ label: "权限审批", value: "ACCESS_APPROVAL" },
	{ label: "质量工单", value: "QUALITY" },
	{ label: "结构漂移", value: "SCHEMA_DRIFT" },
];

const TODO_COLORS: Record<string, string> = {
	ACCESS_APPROVAL: "orange",
	QUALITY: "blue",
	SCHEMA_DRIFT: "green",
};

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function Page() {
	const { push } = useRouter();
	const [todos, setTodos] = useState<WorkbenchTodoItem[]>([]);
	const [loading, setLoading] = useState(false);
	const [typeFilter, setTypeFilter] = useState<string>("ALL");

	const loadTodos = async () => {
		setLoading(true);
		try {
			const list = await workbenchService.todos();
			setTodos(Array.isArray(list) ? (list as WorkbenchTodoItem[]) : []);
		} catch {
			setTodos([]);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadTodos();
	}, []);

	const filteredTodos = useMemo(() => {
		if (typeFilter === "ALL") return todos;
		return todos.filter((item) => item.type === typeFilter);
	}, [todos, typeFilter]);

	const stats = useMemo(
		() => [
			{
				label: "全部待办",
				value: todos.length,
				note: "工作台统一待办池",
				icon: <Workflow className="h-5 w-5" />,
			},
			{
				label: "权限审批",
				value: todos.filter((item) => item.type === "ACCESS_APPROVAL").length,
				note: "数据访问与授权",
				icon: <Shield className="h-5 w-5" />,
				tone: "warning" as const,
			},
			{
				label: "质量工单",
				value: todos.filter((item) => item.type === "QUALITY").length,
				note: "规则异常与质量核查",
				icon: <CheckCircle2 className="h-5 w-5" />,
				tone: "info" as const,
			},
			{
				label: "结构漂移",
				value: todos.filter((item) => item.type === "SCHEMA_DRIFT").length,
				note: "元数据与结构变化提醒",
				icon: <RefreshCw className="h-5 w-5" />,
				tone: "success" as const,
			},
		],
		[todos],
	);

	const columns: ColumnsType<WorkbenchTodoItem> = [
		{
			title: "类型",
			dataIndex: "type",
			width: 140,
			render: (value) => (
				<Tag color={TODO_COLORS[value || ""] || "default"}>
					{value || "TODO"}
				</Tag>
			),
		},
		{
			title: "事项",
			dataIndex: "title",
			sorter: (a, b) => (a.title || "").localeCompare(b.title || ""),
			render: (value, record) => (
				<div className="space-y-1">
					<div className="font-medium text-foreground">{value || "-"}</div>
					{record.message ? <div className="text-sm text-muted-foreground">{record.message}</div> : null}
				</div>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 140,
			render: (value) => value || "-",
		},
		{
			title: "时间",
			dataIndex: "createdAt",
			sorter: (a, b) => {
				const ta = a.createdAt ? new Date(a.createdAt as any).getTime() : 0;
				const tb = b.createdAt ? new Date(b.createdAt as any).getTime() : 0;
				return ta - tb;
			},
			width: 200,
			render: (value) => formatDate(value),
		},
		{
			title: "操作",
			dataIndex: "action",
			width: 160,
			render: (_, record) => {
				if (record.type === "ACCESS_APPROVAL") {
					return <Button onClick={() => push("/security/dataset-access-approval")}>前往审批</Button>;
				}
				if (record.type === "QUALITY") {
					return <Button onClick={() => push("/governance/quality-rules")}>查看质量</Button>;
				}
				if (record.type === "SCHEMA_DRIFT") {
					return <Button onClick={() => push("/catalog/assets")}>查看详情</Button>;
				}
				return "-";
			},
		},
	];

	return (
		<div className="space-y-6">
			<Card
				title="待办中心"
				extra={
					<Space>
						<Button onClick={() => push("/workbench")}>
							返回工作台
						</Button>
						<Button type="primary" onClick={() => void loadTodos()}>
							<RefreshCw className="h-4 w-4" />
							刷新待办
						</Button>
					</Space>
				}
			>
				<PlatformSummaryCards items={stats} />
			</Card>

			<Card
				title="待办清单"
				extra={<Tag>{filteredTodos.length} 条</Tag>}
			>
				<div className="mb-4 flex flex-wrap items-center gap-2">
					<Select
						value={typeFilter}
						onChange={setTypeFilter}
						options={TYPE_OPTIONS}
						style={{ width: 180 }}
					/>
					<Button onClick={() => push("/security/dataset-access-approval")}>
						审批入口
					</Button>
					<Button onClick={() => push("/governance/quality-rules")}>
						质量规则
					</Button>
				</div>
				{filteredTodos.length ? (
					<CompactTable
						rowKey={(record) => `${record.type}-${record.taskId || record.requestId || record.datasetId || record.title}`}
						columns={columns}
						dataSource={filteredTodos}
						loading={loading}
						scroll={{ x: 1000 }}
					/>
				) : (
					<EmptyState title="暂无待办" description="当前没有待处理的事项。" />
				)}
			</Card>
		</div>
	);
}
