import { useEffect, useState } from "react";
import { useParams } from "@/routes/hooks";
import { Button, Card, Space, Table, Tag, message, Spin } from "antd";
import { ArrowLeftOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import { ingestionTaskAPI, type IngestionExecutionDTO, type IngestionTaskDTO } from "@/api/ingestion";
import { formatTimestamp, formatNumber } from "@/utils/format";

export default function TransformExecutionHistoryPage() {
    const { id } = useParams();
    const router = useRouter();
    const [task, setTask] = useState<IngestionTaskDTO | null>(null);
    const [executions, setExecutions] = useState<IngestionExecutionDTO[]>([]);
    const [loading, setLoading] = useState(false);
    const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });

    useEffect(() => {
        if (id) {
            loadTask();
            loadExecutions();
        }
    }, [id, pagination.current]);

    const loadTask = async () => {
        try {
            const result = await ingestionTaskAPI.getTask(Number(id));
            setTask(result);
        } catch (error: any) {
            message.error("加载任务信息失败: " + (error.message || "未知错误"));
        }
    };

    const loadExecutions = async () => {
        setLoading(true);
        try {
            const result = await ingestionTaskAPI.getExecutions(Number(id), {
                page: pagination.current - 1,
                size: pagination.pageSize,
                sort: "createdAt,desc",
            });
            setExecutions(result.content);
            setPagination((prev) => ({ ...prev, total: result.totalElements }));
        } catch (error: any) {
            message.error("加载执行历史失败: " + (error.message || "未知错误"));
        } finally {
            setLoading(false);
        }
    };

    const renderStatus = (status: string) => {
        const statusMap: Record<string, { color: string; text: string }> = {
            running: { color: "processing", text: "运行中" },
            success: { color: "success", text: "成功" },
            failed: { color: "error", text: "失败" },
        };
        const config = statusMap[status] || { color: "default", text: status };
        return <Tag color={config.color}>{config.text}</Tag>;
    };

    const calculateDuration = (start?: string, end?: string) => {
        if (!start) return "-";
        if (!end) return "进行中";

        const duration = new Date(end).getTime() - new Date(start).getTime();
        const seconds = Math.floor(duration / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);

        if (hours > 0) {
            return `${hours}小时${minutes % 60}分${seconds % 60}秒`;
        }
        if (minutes > 0) {
            return `${minutes}分${seconds % 60}秒`;
        }
        return `${seconds}秒`;
    };

    const columns = [
        {
            title: "执行ID",
            dataIndex: "executionId",
            key: "executionId",
            width: 200,
        },
        {
            title: "状态",
            dataIndex: "status",
            key: "status",
            width: 100,
            render: renderStatus,
        },
        {
            title: "开始时间",
            dataIndex: "startTime",
            key: "startTime",
            width: 180,
            render: formatTimestamp,
        },
        {
            title: "结束时间",
            dataIndex: "endTime",
            key: "endTime",
            width: 180,
            render: formatTimestamp,
        },
        {
            title: "执行时长",
            key: "duration",
            width: 150,
            render: (_: any, record: IngestionExecutionDTO) => calculateDuration(record.startTime, record.endTime),
        },
        {
            title: "读取行数",
            dataIndex: "rowsRead",
            key: "rowsRead",
            width: 120,
            render: formatNumber,
        },
        {
            title: "写入行数",
            dataIndex: "rowsWritten",
            key: "rowsWritten",
            width: 120,
            render: formatNumber,
        },
        {
            title: "错误信息",
            dataIndex: "errorMessage",
            key: "errorMessage",
            ellipsis: true,
            render: (text: string) => text || "-",
        },
    ];

    if (!task) {
        return (
            <div className="flex justify-center items-center h-96">
                <Spin size="large" />
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title={`${task.name} - 执行历史`}
                description="查看任务的历史执行记录"
                actions={
                    <Space>
                        <Button icon={<ArrowLeftOutlined />} onClick={() => router.push(`/explore/etl/transform/${id}`)}>
                            返回
                        </Button>
                        <Button icon={<ReloadOutlined />} onClick={loadExecutions} loading={loading}>
                            刷新
                        </Button>
                    </Space>
                }
            />

            <Card>
                <Table
                    columns={columns}
                    dataSource={executions}
                    rowKey="id"
                    loading={loading}
                    pagination={{
                        current: pagination.current,
                        pageSize: pagination.pageSize,
                        total: pagination.total,
                        showSizeChanger: true,
                        showQuickJumper: true,
                        showTotal: (total) => `共 ${total} 条执行记录`,
                        onChange: (page, pageSize) => {
                            setPagination((prev) => ({ ...prev, current: page, pageSize: pageSize || 20 }));
                        },
                    }}
                />
            </Card>
        </div>
    );
}
