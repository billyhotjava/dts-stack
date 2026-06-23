import { UploadOutlined } from "@ant-design/icons";
import { App as AntApp, Button } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { jdbcDriverService } from "@/mock/services/jdbcDriverService";
import type { JdbcDriver } from "@/types/datasource";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

/** JDBC 驱动管理（平台层）。 */
export function JdbcDriversTab() {
	const { message } = AntApp.useApp();
	const [drivers, setDrivers] = useState<JdbcDriver[]>([]);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		setLoading(true);
		void jdbcDriverService.list().then((r) => {
			setDrivers(unwrap(r));
			setLoading(false);
		});
	}, []);

	const columns: CompactColumn<JdbcDriver>[] = [
		{ key: "name", title: "驱动", dataIndex: "name", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "version", title: "版本", dataIndex: "version", width: 110 },
		{ key: "connectorKey", title: "连接器", dataIndex: "connectorKey", width: 120 },
		{ key: "fileName", title: "文件", dataIndex: "fileName" },
		{ key: "uploadedAt", title: "上传于", dataIndex: "uploadedAt", width: 120 },
		{
			key: "status",
			title: "状态",
			width: 100,
			render: (_v, r) => (
				<StatusDot tone={r.status === "active" ? "success" : "muted"} label={r.status === "active" ? "生效" : "未启用"} />
			),
		},
	];

	return (
		<div>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
				<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>驱动由平台统一上传管理，供连接器使用。</span>
				<Button icon={<UploadOutlined />} onClick={() => message.info("上传驱动 —— 原型从略")}>
					上传驱动
				</Button>
			</div>
			<CompactTable<JdbcDriver> columns={columns} data={drivers} rowKey="id" loading={loading} />
		</div>
	);
}
