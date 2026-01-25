import { useEffect, useState } from "react";
import { Button, Card, Space, Table, Tag, message, Modal, Descriptions } from "antd";
import { PlayCircleOutlined, EditOutlined, DeleteOutlined, HistoryOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import { ingestionTaskAPI, type IngestionTaskDTO, type PageResult } from "@/api/ingestion";
import { formatTimestamp } from "@/utils/format";

export default function TransformPage() {
	const router = useRouter();
	const [tasks, setTasks] = useState<IngestionTaskDTO[]>([]);
	const [loading, setLoading] = useState(false);
	const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
	const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);

	useEffect(() => {
		loadTasks();
	}, [pagination.current, statusFilter]);

	const loadTasks = async () => {
		setLoading(true);
		try {
			const result = await ingestionTaskAPI.getTasks({
				status: statusFilter,
				page: pagination.current - 1,
				size: pagination.pageSize,
			});
			setTasks(result.content);
			setPagination((prev) => ({ ...prev, total: result.totalElements }));
		} catch (error: any) {
			message.error("加载任务列表失败: " + (error.message || "未知错误"));
		} finally {
			setLoading(false);
		}
	};

	const handleExecute = async (id: number, name: string) => {
		Modal.confirm({
			title: "确认执行",
			content: `确定要执行任务 "${name}" 吗？`,
			onOk: async () => {
				try {
					await ingestionTaskAPI.executeTask(id);
					message.success("任务已触发执行");
					loadTasks();
				} catch (error: any) {
					message.error("执行失败: " + (error.message || "未知错误"));
				}
			},
		});
	};

	const handleDelete = async (id: number, name: string) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除任务 "${name}" 吗？此操作为软删除，可以恢复。`,
			okText: "删除",
			okType: "danger",
			onOk: async () => {
				try {
					await ingestionTaskAPI.deleteTask(id);
					message.success("任务已删除");
					loadTasks();
				} catch (error: any) {
					message.error("删除失败: " + (error.message || "未知错误"));
				}
			},
		});
	};

	const renderStatus = (status?: string) => {
		const statusMap: Record<string, { color: string; text: string }> = {
			draft: { color: "default", text: "草稿" },
			active: { color: "success", text: "活跃" },
			paused: { color: "warning", text: "暂停" },
			deleted: { color: "error", text: "已删除" },
		};
		const config = statusMap[status || "draft"];
		return <Tag color={config.color}>{config.text}</Tag>;
	};

	const renderExecutionStatus = (status?: string) => {
		const statusMap: Record<string, { color: string; text: string }> = {
			running: { color: "processing", text: "运行中" },
			success: { color: "success", text: "成功" },
			failed: { color: "error", text: "失败" },
		};
		const config = statusMap[status || ""];
		return config ? <Tag color={config.color}>{config.text}</Tag> : <span>-</span>;
	};

	const columns = [
		{
			title: "任务名称",
			dataIndex: "name",
			key: "name",
			width: 200,
			render: (text: string, record: IngestionTaskDTO) => (
				<a onClick={() => router.push(`/explore/etl/transform/${record.id}`)}>{text}</a>
			),
		},
		{
			title: "数据源类型",
			dataIndex: "sourceType",
			key: "sourceType",
			width: 150,
		},
		{
			title: "同步模式",
			dataIndex: "syncMode",
			key: "syncMode",
			width: 120,
		},
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 100,
			render: renderStatus,
		},
		{
			title: "最后执行状态",
			dataIndex: "lastExecutionStatus",
			key: "lastExecutionStatus",
			width: 120,
			render: renderExecutionStatus,
		},
		{
			title: "最后执行时间",
			dataIndex: "lastExecutedAt",
			key: "lastExecutedAt",
			width: 180,
			render: (text: string) => (text ? formatTimestamp(text) : "-"),
		},
		{
			title: "创建时间",
			dataIndex: "createdDate",
			key: "createdDate",
			width: 180,
			render: (text: string) => (text ? formatTimestamp(text) : "-"),
		},
		{
			title: "创建人",
			dataIndex: "createdBy",
			key: "createdBy",
			width: 120,
		},
		{
			title: "操作",
			key: "action",
			width: 250,
			fixed: "right" as const,
			render: (_: any, record: IngestionTaskDTO) => (
				<Space size="small">
					<Button
						size="small"
						type="primary"
						icon={<PlayCircleOutlined />}
						onClick={() => handleExecute(record.id!, record.name)}
						disabled={record.status === "deleted"}
					>
						执行
					</Button>
					<Button
						size="small"
						icon={<HistoryOutlined />}
						onClick={() => router.push(`/explore/etl/transform/${record.id}/executions`)}
					>
						历史
					</Button>
					<Button
						size="small"
						icon={<EditOutlined />}
						onClick={() => router.push(`/explore/etl/transform/${record.id}/edit`)}
						disabled={record.status === "deleted"}
					>
						编辑
					</Button>
					<Button
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={() => handleDelete(record.id!, record.name)}
						disabled={record.status === "deleted"}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title="入湖任务管理"
				description="使用 Addax 生成作业配置，Airflow 编排执行"
				actions={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={loadTasks} loading={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={() => router.push("/explore/etl/transform/new")}>
							创建入湖任务
						</Button>
					</Space>
				}
			/>

			<Card>
				<Space style={{ marginBottom: 16 }}>
					<span>状态筛选：</span>
					{[
						{ label: "全部", value: undefined },
						{ label: "草稿", value: "draft" },
						{ label: "活跃", value: "active" },
						{ label: "暂停", value: "paused" },
						{ label: "已删除", value: "deleted" },
					].map((item) => (
						<Button
							key={item.label}
							type={statusFilter === item.value ? "primary" : "default"}
							size="small"
							onClick={() => setStatusFilter(item.value)}
						>
							{item.label}
						</Button>
					))}
				</Space>

				<Table
					columns={columns}
					dataSource={tasks}
					rowKey="id"
					loading={loading}
					scroll={{ x: 1400 }}
					pagination={{
						current: pagination.current,
						pageSize: pagination.pageSize,
						total: pagination.total,
						showSizeChanger: true,
						showQuickJumper: true,
						showTotal: (total) => `共 ${total} 条`,
						onChange: (page, pageSize) => {
							setPagination((prev) => ({ ...prev, current: page, pageSize: pageSize || 20 }));
						},
					}}
				/>
			</Card>
		</div>
	);
}
