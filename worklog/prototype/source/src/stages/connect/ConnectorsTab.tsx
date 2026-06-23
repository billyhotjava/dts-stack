import { Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { connectorService } from "@/mock/services/connectorService";
import type { Connector } from "@/types/datasource";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

/** 连接器注册（平台层，跨部门只读）。 */
export function ConnectorsTab() {
	const [connectors, setConnectors] = useState<Connector[]>([]);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		setLoading(true);
		void connectorService.list().then((r) => {
			setConnectors(unwrap(r));
			setLoading(false);
		});
	}, []);

	const columns: CompactColumn<Connector>[] = [
		{ key: "name", title: "连接器", dataIndex: "name", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "category", title: "类别", dataIndex: "category", width: 140 },
		{ key: "engines", title: "支持引擎", render: (_v, r) => r.engines.map((e) => <Tag key={e}>{e}</Tag>) },
		{
			key: "status",
			title: "状态",
			width: 110,
			render: (_v, r) => (
				<StatusDot tone={r.status === "enabled" ? "success" : "muted"} label={r.status === "enabled" ? "已启用" : "未启用"} />
			),
		},
	];

	return (
		<div>
			<div style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)", marginBottom: 12 }}>
				连接器由网信中心在平台层统一登记，跨部门共享。
			</div>
			<CompactTable<Connector> columns={columns} data={connectors} rowKey="key" loading={loading} />
		</div>
	);
}
