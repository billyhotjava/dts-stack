import { useEffect, useMemo, useState } from "react";
import { Select, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ArrowRight, CheckCircle2, RefreshCw, Shield, Workflow } from "lucide-react";
import { toast } from "sonner";
import {
	PlatformFilterBar,
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { EmptyState } from "@/components/empty-state";
import workbenchService, { type WorkbenchTodoItem } from "@/api/services/workbenchService";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

const TYPE_OPTIONS = [
	{ label: "全部", value: "ALL" },
	{ label: "权限审批", value: "ACCESS_APPROVAL" },
	{ label: "质量工单", value: "QUALITY" },
	{ label: "结构漂移", value: "SCHEMA_DRIFT" },
];

const TODO_VARIANTS: Record<string, "info" | "warning" | "success" | "outline"> = {
	ACCESS_APPROVAL: "warning",
	QUALITY: "info",
	SCHEMA_DRIFT: "success",
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
				<Badge variant={TODO_VARIANTS[value || ""] || "outline"} className="rounded-full px-2.5 py-1">
					{value || "TODO"}
				</Badge>
			),
		},
		{
			title: "事项",
			dataIndex: "title",
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
			width: 200,
			render: (value) => formatDate(value),
		},
		{
			title: "操作",
			dataIndex: "action",
			width: 160,
			render: (_, record) => {
				if (record.type === "ACCESS_APPROVAL") {
					return <Button size="sm" variant="outline" onClick={() => push("/dashboard/security/dataset-access-approval")}>前往审批</Button>;
				}
				if (record.type === "QUALITY") {
					return <Button size="sm" variant="outline" onClick={() => push("/dashboard/governance/quality-rules")}>查看质量</Button>;
				}
				if (record.type === "SCHEMA_DRIFT") {
					return <Button size="sm" variant="outline" onClick={() => push("/dashboard/catalog/datasets")}>查看详情</Button>;
				}
				return "-";
			},
		},
	];

	return (
		<div className="space-y-6">
			<PlatformPageHero
				title="待办中心"
				description="集中处理权限审批、质量核查和结构漂移提醒，避免待办分散在不同业务模块里。"
				eyebrow="Workbench Inbox"
				actions={
					<>
						<Button variant="outline" onClick={() => push("/dashboard/workbench")}>
							<ArrowRight className="h-4 w-4" />
							返回工作台
						</Button>
						<Button onClick={() => void loadTodos()}>
							<RefreshCw className="h-4 w-4" />
							刷新待办
						</Button>
					</>
				}
				meta={
					<>
						<PlatformMetaPill>统一待办池</PlatformMetaPill>
						<PlatformMetaPill>按事项类型快速分拣</PlatformMetaPill>
						<PlatformMetaPill>就近跳转到处理入口</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={stats} />

			<PlatformFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">先筛类型，再处理事项</div>
					<div className="mt-1 text-sm text-muted-foreground">
						类型过滤只改变当前列表，不改变待办池本身的优先级与分派规则。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
					<Select
						value={typeFilter}
						onChange={setTypeFilter}
						options={TYPE_OPTIONS}
						style={{ width: 180 }}
					/>
					<Button variant="outline" onClick={() => push("/dashboard/security/dataset-access-approval")}>
						审批入口
					</Button>
					<Button variant="outline" onClick={() => push("/dashboard/governance/quality-rules")}>
						质量规则
					</Button>
				</div>
			</PlatformFilterBar>

			<PlatformSectionCard
				title="待办清单"
				description="按类型、状态和时间查看待办，直接跳到对应处理页。"
				action={<Badge variant="outline" className="rounded-full px-2.5 py-1">{filteredTodos.length} 条</Badge>}
			>
				{filteredTodos.length ? (
					<Table
						rowKey={(record) => `${record.type}-${record.taskId || record.requestId || record.datasetId || record.title}`}
						columns={columns}
						dataSource={filteredTodos}
						loading={loading}
					/>
				) : (
					<EmptyState title="暂无待办" description="当前没有待处理的事项。" />
				)}
			</PlatformSectionCard>

			<PlatformSectionCard
				title="分拣建议"
				description="不同类型的待办应该去不同页面处理，不建议在这里补业务逻辑。"
			>
				<div className="grid gap-3 xl:grid-cols-3">
					{[
						"权限审批类待办应直接进入数据集审批页，避免待办中心变成审批工作台的替身。",
						"质量工单类待办优先查看规则与历史执行，再决定是修规则还是修数据。",
						"结构漂移类待办优先核对数据集详情与采集链路，再决定是否需要重新同步元数据。",
					].map((item) => (
						<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-sm leading-6 text-muted-foreground">
							{item}
						</div>
					))}
				</div>
			</PlatformSectionCard>
		</div>
	);
}
