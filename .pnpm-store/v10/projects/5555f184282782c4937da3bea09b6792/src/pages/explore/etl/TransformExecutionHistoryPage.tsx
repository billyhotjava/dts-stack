import { useParams } from "@/routes/hooks";
import { Button, Space } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";
import { useRouter } from "@/routes/hooks";
import ExecutionHistoryTable from "./components/ExecutionHistoryTable";

export default function TransformExecutionHistoryPage() {
    const { id } = useParams();
    const router = useRouter();

    return (
        <div className="space-y-6">
            <div style={{ marginBottom: 16 }}>
                <Space>
                    <Button icon={<ArrowLeftOutlined />} onClick={() => router.push(`/explore/etl/transform/${id}`)}>
                        返回
                    </Button>
                </Space>
            </div>
            <ExecutionHistoryTable taskId={Number(id)} />
        </div>
    );
}
