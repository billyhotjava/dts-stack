import { useEffect, useState } from "react";
import { Card, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import opsService, { type OpsAlert } from "@/api/services/opsService";

const { Text } = Typography;

export default function OpsAlertLogPage() {
	const [alerts, setAlerts] = useState<OpsAlert[]>([]);
	const [loading, setLoading] = useState(false);

	const loadAlerts = async () => {
		setLoading(true);
		try {
			const list = await opsService.alerts({ limit: 200 });
			setAlerts(Array.isArray(list) ? (list as OpsAlert[]) : []);
		} catch {
			// handled by global interceptor
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadAlerts();
	}, []);

	const columns: ColumnsType<OpsAlert> = [
		{ title: "类型", dataIndex: "type", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "规则", dataIndex: "ruleName", render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "严重性", dataIndex: "severity", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "描述", dataIndex: "message", render: (v) => <Text type="secondary">{v || "-"}</Text> },
	];

	return (
		<div className="space-y-6 px-6 py-6">
			<PageHeader title="告警日志" />
			<Card>
				<Table rowKey={(record, idx) => `${record.type}-${idx}`} columns={columns} dataSource={alerts} loading={loading} />
			</Card>
		</div>
	);
}
