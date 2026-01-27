import { useEffect, useState } from "react";
import { useParams } from "@/routes/hooks";
import { Button, Card, Descriptions, Space, Tag, message, Spin, Modal } from "antd";
import { PlayCircleOutlined, EditOutlined, HistoryOutlined, ArrowLeftOutlined, SyncOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import { ingestionTaskAPI, type IngestionTaskDTO } from "@/api/ingestion";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";

export default function TransformDetailPage() {
    const { id } = useParams();
    const router = useRouter();
    const [task, setTask] = useState<IngestionTaskDTO | null>(null);
    const [sourceDetail, setSourceDetail] = useState<InfraDataSource | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (id) {
            loadTask();
        }
    }, [id]);

    useEffect(() => {
        if (!task?.sourceDataSourceId) {
            setSourceDetail(null);
            return;
        }
        const loadSource = async () => {
            try {
                const detail = await dataSourcesService.detail(String(task.sourceDataSourceId));
                setSourceDetail(detail);
            } catch {
                setSourceDetail(null);
            }
        };
        loadSource();
    }, [task?.sourceDataSourceId]);

    const loadTask = async () => {
        setLoading(true);
        try {
            const result = await ingestionTaskAPI.getTask(Number(id));
            setTask(result);
        } catch (error: any) {
            message.error("加载任务详情失败: " + (error.message || "未知错误"));
        } finally {
            setLoading(false);
        }
    };

    const handleExecute = async () => {
        try {
            await ingestionTaskAPI.executeTask(Number(id));
            message.success("任务已触发执行");
            loadTask();
        } catch (error: any) {
            message.error("执行失败: " + (error.message || "未知错误"));
        }
    };

    const handleRebuildDag = async () => {
        Modal.confirm({
            title: "强制重建 DAG",
            content: `确定要重建任务 "${task?.name}" 的 DAG 文件吗？`,
            onOk: async () => {
                try {
                    await ingestionTaskAPI.rebuildDag(Number(id));
                    message.success("DAG 已重建");
                    loadTask();
                } catch (error: any) {
                    message.error("重建失败: " + (error.message || "未知错误"));
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

    if (loading || !task) {
        return (
            <div className="flex justify-center items-center h-96">
                <Spin size="large" />
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title={task.name}
                description={task.description || "入湖任务详情"}
                actions={
                    <Space>
                        <Button icon={<ArrowLeftOutlined />} onClick={() => router.push("/explore/etl/transform")}>
                            返回
                        </Button>
                        <Button icon={<HistoryOutlined />} onClick={() => router.push(`/explore/etl/transform/${id}/executions`)}>
                            执行历史
                        </Button>
                        <Button
                            icon={<EditOutlined />}
                            onClick={() => router.push(`/explore/etl/transform/${id}/edit`)}
                            disabled={task.status === "deleted"}
                        >
                            编辑
                        </Button>
                        <Button
                            icon={<SyncOutlined />}
                            onClick={handleRebuildDag}
                            disabled={task.status === "deleted" || task.airflowEnabled === false}
                        >
                            重建 DAG
                        </Button>
                        <Button type="primary" icon={<PlayCircleOutlined />} onClick={handleExecute} disabled={task.status === "deleted"}>
                            执行任务
                        </Button>
                    </Space>
                }
            />

            <Card title="基本信息">
                <Descriptions column={2} bordered>
                    <Descriptions.Item label="任务名称">{task.name}</Descriptions.Item>
                    <Descriptions.Item label="状态">{renderStatus(task.status)}</Descriptions.Item>
                    <Descriptions.Item label="数据源连接">
                        {sourceDetail ? `${sourceDetail.name} (${sourceDetail.type || "unknown"})` : task.sourceDataSourceId || "-"}
                    </Descriptions.Item>
                    <Descriptions.Item label="Reader 类型">{task.sourceType}</Descriptions.Item>
                    <Descriptions.Item label="目标类型">{task.destinationType || "postgresqlwriter"}</Descriptions.Item>
                    <Descriptions.Item label="同步模式">{task.syncMode}</Descriptions.Item>
                    <Descriptions.Item label="调度配置">{task.syncSchedule || "手动触发"}</Descriptions.Item>
                    <Descriptions.Item label="创建人">{task.createdBy}</Descriptions.Item>
                    <Descriptions.Item label="创建时间">{task.createdDate ? new Date(task.createdDate).toLocaleString("zh-CN") : "-"}</Descriptions.Item>
                    <Descriptions.Item label="最后修改人">{task.lastModifiedBy || "-"}</Descriptions.Item>
                    <Descriptions.Item label="最后修改时间">
                        {task.lastModifiedDate ? new Date(task.lastModifiedDate).toLocaleString("zh-CN") : "-"}
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card title="源端覆盖参数">
                <pre className="bg-gray-50 p-4 rounded overflow-auto">{JSON.stringify(task.sourceConfig || {}, null, 2)}</pre>
            </Card>

            {task.destinationConfig && (
                <Card title="目标配置">
                    <pre className="bg-gray-50 p-4 rounded overflow-auto">{JSON.stringify(task.destinationConfig, null, 2)}</pre>
                </Card>
            )}

            {task.tableMapping && task.tableMapping.length > 0 && (
                <Card title="表映射配置">
                    <pre className="bg-gray-50 p-4 rounded overflow-auto">{JSON.stringify(task.tableMapping, null, 2)}</pre>
                </Card>
            )}

            <Card title="Airflow集成">
                <Descriptions column={2} bordered>
                    <Descriptions.Item label="启用状态">{task.airflowEnabled ? <Tag color="success">已启用</Tag> : <Tag>未启用</Tag>}</Descriptions.Item>
                    <Descriptions.Item label="编排模板">{task.airflowDagId ? "系统自动生成" : "系统默认"}</Descriptions.Item>
                </Descriptions>
            </Card>

            <Card title="执行信息">
                <Descriptions column={2} bordered>
                    <Descriptions.Item label="最后执行时间">
                        {task.lastExecutedAt ? new Date(task.lastExecutedAt).toLocaleString("zh-CN") : "从未执行"}
                    </Descriptions.Item>
                    <Descriptions.Item label="最后执行状态">
                        {task.lastExecutionStatus ? (
                            <Tag color={task.lastExecutionStatus === "success" ? "success" : task.lastExecutionStatus === "failed" ? "error" : "processing"}>
                                {task.lastExecutionStatus}
                            </Tag>
                        ) : (
                            "-"
                        )}
                    </Descriptions.Item>
                    <Descriptions.Item label="Addax Job路径" span={2}>
                        {task.addaxJobPath || "-"}
                    </Descriptions.Item>
                </Descriptions>
            </Card>
        </div>
    );
}
