import { useEffect, useMemo, useState } from "react";
import { Form, Input, Modal, Select, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Database, RefreshCw, Star, Workflow } from "lucide-react";
import { toast } from "sonner";
import {
	PlatformFilterBar,
	PlatformMetaPill,
	PlatformPageHero,
	PlatformSectionCard,
	PlatformSummaryCards,
} from "@/components/console-page";
import { EmptyState } from "@/components/empty-state";
import workbenchService, {
	type WorkbenchFavorite,
	type WorkbenchOverview,
	type WorkbenchTodoItem,
} from "@/api/services/workbenchService";
import { useRouter } from "@/routes/hooks";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";

const FAVORITE_TYPES = [
	{ label: "数据资产", value: "DATASET" },
	{ label: "模型", value: "MODEL" },
	{ label: "任务", value: "TASK" },
	{ label: "报表", value: "REPORT" },
	{ label: "链接", value: "LINK" },
];

type FavoriteForm = {
	title: string;
	targetType?: string;
	targetId?: string;
	link?: string;
	sortOrder?: number;
	enabled?: boolean;
};

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

const TODO_VARIANTS: Record<string, "info" | "warning" | "success" | "outline"> = {
	ACCESS_APPROVAL: "warning",
	QUALITY: "info",
	SCHEMA_DRIFT: "success",
};

export default function Page() {
	const { push } = useRouter();
	const [overview, setOverview] = useState<WorkbenchOverview | null>(null);
	const [todos, setTodos] = useState<WorkbenchTodoItem[]>([]);
	const [favorites, setFavorites] = useState<WorkbenchFavorite[]>([]);
	const [loading, setLoading] = useState(false);
	const [favoriteModalOpen, setFavoriteModalOpen] = useState(false);
	const [editingFavorite, setEditingFavorite] = useState<WorkbenchFavorite | null>(null);
	const [form] = Form.useForm<FavoriteForm>();

	const loadAll = async () => {
		setLoading(true);
		try {
			const [summary, todoList, favoriteList] = await Promise.all([
				workbenchService.overview(),
				workbenchService.todos(),
				workbenchService.favorites(),
			]);
			setOverview(summary as WorkbenchOverview);
			setTodos(Array.isArray(todoList) ? (todoList as WorkbenchTodoItem[]) : []);
			setFavorites(Array.isArray(favoriteList) ? (favoriteList as WorkbenchFavorite[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "工作台数据加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadAll();
	}, []);

	const todoPreview = useMemo(() => todos.slice(0, 8), [todos]);
	const favoriteTypeCount = useMemo(() => new Set(favorites.map((item) => item.targetType || "LINK")).size, [favorites]);

	const summaryCards = [
		{
			label: "我创建的资产",
			value: overview?.myAssets ?? "--",
			note: "个人资产沉淀规模",
			icon: <Database className="h-5 w-5" />,
		},
		{
			label: "今日新增资产",
			value: overview?.todayNewAssets ?? "--",
			note: "当天新增沉淀",
			icon: <RefreshCw className="h-5 w-5" />,
			tone: "info" as const,
		},
		{
			label: "待办事项",
			value: todos.length,
			note: "审批、质量与漂移提醒",
			icon: <Workflow className="h-5 w-5" />,
			tone: "warning" as const,
		},
		{
			label: "我的收藏",
			value: favorites.length,
			note: `${favoriteTypeCount} 类常用入口`,
			icon: <Star className="h-5 w-5" />,
			tone: "success" as const,
		},
	];

	const openCreateFavorite = () => {
		setEditingFavorite(null);
		form.resetFields();
		form.setFieldsValue({ targetType: "DATASET", enabled: true });
		setFavoriteModalOpen(true);
	};

	const openEditFavorite = (favorite: WorkbenchFavorite) => {
		setEditingFavorite(favorite);
		form.setFieldsValue({
			title: favorite.title,
			targetType: favorite.targetType || undefined,
			targetId: favorite.targetId || undefined,
			link: favorite.link || undefined,
			sortOrder: favorite.sortOrder || undefined,
			enabled: favorite.enabled ?? true,
		});
		setFavoriteModalOpen(true);
	};

	const handleSaveFavorite = async () => {
		try {
			const values = await form.validateFields();
			if (editingFavorite) {
				await workbenchService.updateFavorite(editingFavorite.id, values);
				toast.success("已更新收藏");
			} else {
				await workbenchService.createFavorite(values);
				toast.success("已新增收藏");
			}
			setFavoriteModalOpen(false);
			await loadAll();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const openFavorite = (favorite: WorkbenchFavorite) => {
		if (!favorite.link) return;
		if (/^https?:\/\//i.test(favorite.link)) {
			window.open(favorite.link, "_blank", "noopener,noreferrer");
			return;
		}
		push(favorite.link);
	};

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
			width: 180,
			render: (value) => formatDate(value),
		},
	];

	return (
		<div className="space-y-6" data-testid="platform-workbench-page">
			<PlatformPageHero
				title="工作台总览"
				description="把个人待办、常用入口和资产沉淀汇总到同一个工作面，减少在治理、开发和审批模块之间来回跳转。"
				eyebrow="Workbench Overview"
				actions={
					<>
						<Button data-testid="platform-workbench-refresh" variant="outline" onClick={() => void loadAll()}>
							<RefreshCw className="h-4 w-4" />
							刷新数据
						</Button>
						<Button
							data-testid="platform-workbench-todo-center"
							variant="outline"
							onClick={() => push("/dashboard/workbench/workflow-center")}
						>
							<Workflow className="h-4 w-4" />
							待办中心
						</Button>
						<Button data-testid="platform-workbench-new-favorite" onClick={openCreateFavorite}>
							<Star className="h-4 w-4" />
							新增收藏
						</Button>
					</>
				}
				meta={
					<>
						<PlatformMetaPill>个人待办与常用入口统一收口</PlatformMetaPill>
						<PlatformMetaPill>预览区默认展示最近 8 条待办</PlatformMetaPill>
						<PlatformMetaPill>收藏支持资产、模型、任务、报表与链接</PlatformMetaPill>
					</>
				}
			/>

			<PlatformSummaryCards items={summaryCards} />

			<PlatformFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">从工作台进入高频流程</div>
					<div className="mt-1 text-sm text-muted-foreground">
						待办预览聚焦“现在要处理什么”，收藏区聚焦“经常要去哪”。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
						<Button data-testid="platform-workbench-link-datasets" variant="outline" onClick={() => push("/dashboard/catalog/datasets")}>
							数据资产
						</Button>
						<Button data-testid="platform-workbench-link-jobs" variant="outline" onClick={() => push("/dashboard/explore/etl/transform")}>
							任务中心
						</Button>
						<Button data-testid="platform-workbench-link-dbt" variant="outline" onClick={() => push("/dashboard/modeling/dbt-files")}>
							DBT 文件
						</Button>
					</div>
				</PlatformFilterBar>

			<div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
				<PlatformSectionCard
					title="待办预览"
					description="聚焦最近要处理的审批、质量和结构漂移事项。"
					action={
						<Button
							data-testid="platform-workbench-view-all-todos"
							variant="outline"
							size="sm"
							onClick={() => push("/dashboard/workbench/workflow-center")}
						>
							查看全部
						</Button>
					}
				>
					{todoPreview.length ? (
						<div data-testid="platform-workbench-todos">
							<Table
								rowKey={(record) => `${record.type}-${record.taskId || record.requestId || record.datasetId || record.title}`}
								columns={columns}
								dataSource={todoPreview}
								pagination={false}
								loading={loading}
							/>
						</div>
					) : (
						<EmptyState title="暂无待办" description="没有需要处理的事项。" />
					)}
				</PlatformSectionCard>

				<PlatformSectionCard
					title="我的收藏"
					description="把常用资产、模型或外部入口固定在个人工作面。"
					action={<Badge variant="outline" className="rounded-full px-2.5 py-1">{favorites.length} 项</Badge>}
				>
					{favorites.length ? (
						<div className="grid gap-3">
							{favorites.map((favorite) => (
								<div
									key={favorite.id}
									data-testid={`platform-workbench-favorite-card-${favorite.id}`}
									className="rounded-[22px] border border-border/70 bg-muted/35 p-4"
								>
									<div className="flex items-start justify-between gap-3">
										<div className="space-y-1">
											<div className="flex flex-wrap items-center gap-2">
												<div className="text-sm font-semibold text-foreground">{favorite.title}</div>
												<Badge variant="info" className="rounded-full px-2.5 py-1">
													{favorite.targetType || "LINK"}
												</Badge>
											</div>
											<div className="text-sm text-muted-foreground">
												{favorite.targetId ? `目标: ${favorite.targetId}` : "未绑定具体目标"}
											</div>
											{favorite.link ? (
												<div className="truncate text-xs text-muted-foreground">{favorite.link}</div>
											) : null}
										</div>
										<div className="flex items-center gap-2">
											{favorite.link ? (
												<Button
													data-testid={`platform-workbench-favorite-open-${favorite.id}`}
													size="sm"
													variant="outline"
													onClick={() => openFavorite(favorite)}
												>
													打开
												</Button>
											) : null}
											<Button
												data-testid={`platform-workbench-favorite-edit-${favorite.id}`}
												size="sm"
												variant="ghost"
												onClick={() => openEditFavorite(favorite)}
											>
												编辑
											</Button>
										</div>
									</div>
								</div>
							))}
						</div>
					) : (
						<EmptyState title="暂无收藏" description="添加常用资产或链接到工作台。" />
					)}
				</PlatformSectionCard>
			</div>

			<PlatformSectionCard
				title="处理建议"
				description="先把待办清掉，再回到资产、任务和模型入口继续推进。"
			>
				<div className="grid gap-3 xl:grid-cols-3">
					{[
						"权限审批类待办优先进入数据集访问审批页，避免申请积压。",
						"质量类待办建议结合质量规则页处理根因，而不是只看当前告警。",
						"结构漂移类待办处理后，再把常用任务或模型收藏固定到工作台。",
					].map((item) => (
						<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-sm leading-6 text-muted-foreground">
							{item}
						</div>
					))}
				</div>
			</PlatformSectionCard>

			<Modal
				open={favoriteModalOpen}
				onCancel={() => setFavoriteModalOpen(false)}
				onOk={handleSaveFavorite}
				title={editingFavorite ? "编辑收藏" : "新增收藏"}
				okText="保存"
			>
				<Form layout="vertical" form={form}>
					<Form.Item label="收藏名称" name="title" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：销售主题数据集" />
					</Form.Item>
					<Form.Item label="类型" name="targetType" initialValue="DATASET">
						<Select options={FAVORITE_TYPES} />
					</Form.Item>
					<Form.Item label="目标ID" name="targetId">
						<Input placeholder="可选，用于绑定资产/模型/任务" />
					</Form.Item>
					<Form.Item label="跳转链接" name="link">
						<Input placeholder="例如：/dashboard/catalog/datasets" />
					</Form.Item>
					<Form.Item label="排序" name="sortOrder">
						<Input type="number" placeholder="数字越小越靠前" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
