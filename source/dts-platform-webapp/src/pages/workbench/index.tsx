import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Col, Form, Input, Modal, Row, Select, Statistic, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import workbenchService, {
	type WorkbenchFavorite,
	type WorkbenchTodoItem,
	type WorkbenchOverview,
} from "@/api/services/workbenchService";
import { RouterLink } from "@/routes/components/router-link";

const { Text } = Typography;

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

export default function Page() {
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

	const openCreateFavorite = () => {
		setEditingFavorite(null);
		form.resetFields();
		setFavoriteModalOpen(true);
	};

	const openEditFavorite = (fav: WorkbenchFavorite) => {
		setEditingFavorite(fav);
		form.setFieldsValue({
			title: fav.title,
			targetType: fav.targetType || undefined,
			targetId: fav.targetId || undefined,
			link: fav.link || undefined,
			sortOrder: fav.sortOrder || undefined,
			enabled: fav.enabled ?? true,
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

	const columns: ColumnsType<WorkbenchTodoItem> = [
		{
			title: "类型",
			dataIndex: "type",
			width: 120,
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
			width: 120,
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
		<div className="space-y-6">
			<PageHeader title="工作台 / 我的概览" description="个人任务中心与效能看板。" />

			<Row gutter={[16, 16]}>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic title="我创建的资产" value={overview?.myAssets ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic title="今日新增资产" value={overview?.todayNewAssets ?? "--"} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic title="待办事项" value={todos.length} />
					</Card>
				</Col>
				<Col xs={24} sm={12} lg={6}>
					<Card>
						<Statistic title="我的收藏" value={favorites.length} />
					</Card>
				</Col>
			</Row>

			<Row gutter={[16, 16]}>
				<Col xs={24} lg={14}>
					<Card
						title="待办事项"
						extra={<RouterLink href="/dashboard/workbench/workflow-center">查看全部</RouterLink>}
					>
						{todoPreview.length ? (
							<Table
								rowKey={(record) => `${record.type}-${record.taskId || record.requestId || record.datasetId || record.title}`}
								columns={columns}
								dataSource={todoPreview}
								pagination={false}
								loading={loading}
							/>
						) : (
							<EmptyState title="暂无待办" description="没有需要处理的事项。" />
						)}
					</Card>
				</Col>
				<Col xs={24} lg={10}>
					<Card
						title="我的收藏"
						extra={
							<Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreateFavorite}>
								新增
							</Button>
						}
					>
						{favorites.length ? (
							<div className="space-y-3">
								{favorites.map((fav) => (
									<div key={fav.id} className="flex items-center justify-between gap-3">
										<div className="min-w-0">
											<Text strong>{fav.title}</Text>
											<div className="text-xs text-muted-foreground">
												{fav.targetType || "收藏"}
												{fav.link ? ` · ${fav.link}` : ""}
											</div>
										</div>
										<div className="flex items-center gap-2">
											{fav.link ? (
												<RouterLink href={fav.link}>打开</RouterLink>
											) : null}
											<Button type="link" size="small" onClick={() => openEditFavorite(fav)}>
												编辑
											</Button>
										</div>
									</div>
								))}
							</div>
						) : (
							<EmptyState title="暂无收藏" description="添加常用资产或链接到工作台。" />
						)}
					</Card>
				</Col>
			</Row>

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
