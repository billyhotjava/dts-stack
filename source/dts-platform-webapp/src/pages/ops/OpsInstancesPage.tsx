import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import opsService, { type OpsInstance } from "@/api/services/opsService";

const { Text } = Typography;

const STATUS_OPTIONS = [
	{ label: "全部", value: "ALL" },
	{ label: "RUNNING", value: "RUNNING" },
	{ label: "SUCCESS", value: "SUCCESS" },
	{ label: "FAILED", value: "FAILED" },
];

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function OpsInstancesPage() {
	const [records, setRecords] = useState<OpsInstance[]>([]);
	const [loading, setLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [status, setStatus] = useState("ALL");

	const loadInstances = async () => {
		setLoading(true);
		try {
			const list = await opsService.instances({
				keyword: keyword.trim() || undefined,
				status: status === "ALL" ? undefined : status,
				limit: 200,
			});
			setRecords(Array.isArray(list) ? (list as OpsInstance[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "实例加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadInstances();
	}, []);

	const columns: ColumnsType<OpsInstance> = [
		{ title: "任务", dataIndex: "artifactName", render: (v) => v || "-" },
		{ title: "类型", dataIndex: "artifactType", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "开始时间", dataIndex: "startedAt", render: (v) => formatDate(v) },
		{ title: "结束时间", dataIndex: "finishedAt", render: (v) => formatDate(v) },
		{ title: "耗时(ms)", dataIndex: "durationMs", render: (v) => v ?? "-" },
		{ title: "备注", dataIndex: "message", render: (v) => <Text type="secondary">{v || "-"}</Text> },
	];

	return (
		<div className="space-y-6 px-6 py-6">
			<PageHeader title="任务实例监控" description="查看任务运行实例与日志入口。" />
			<Card
				extra={
					<Space>
						<Input placeholder="搜索任务名称..." value={keyword} onChange={(e) => setKeyword(e.target.value)} />
						<Select value={status} options={STATUS_OPTIONS} onChange={setStatus} style={{ width: 140 }} />
						<Button onClick={loadInstances}>刷新</Button>
					</Space>
				}
			>
				<Table rowKey={(record) => record.id} columns={columns} dataSource={records} loading={loading} />
			</Card>
		</div>
	);
}
