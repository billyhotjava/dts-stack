import { PlusOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Popconfirm, Space, Tag } from "antd";
import { useCallback, useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { connectorService } from "@/mock/services/connectorService";
import { dataSourceService } from "@/mock/services/dataSourceService";
import { useDepartmentStore } from "@/store/departmentStore";
import type { Connector, DataSource, DataSourceStatus, DataSourceUpsertPayload } from "@/types/datasource";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn, DotTone } from "@/ui/components";
import { DataSourceDetailDrawer } from "./DataSourceDetailDrawer";
import { DataSourceFormModal } from "./DataSourceFormModal";

const STATUS_TONE: Record<DataSourceStatus, DotTone> = { connected: "success", error: "error", untested: "muted" };
const STATUS_LABEL: Record<DataSourceStatus, string> = { connected: "已连通", error: "异常", untested: "未测试" };

export function DataSourcesTab() {
	const deptId = useDepartmentStore((s) => s.currentDepartmentId);
	const { message } = AntApp.useApp();
	const [sources, setSources] = useState<DataSource[]>([]);
	const [connectors, setConnectors] = useState<Connector[]>([]);
	const [loading, setLoading] = useState(false);
	const [testingId, setTestingId] = useState<string | null>(null);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<DataSource | null>(null);
	const [detail, setDetail] = useState<DataSource | null>(null);

	const refresh = useCallback(async () => {
		if (!deptId) return;
		setLoading(true);
		setSources(unwrap(await dataSourceService.listForDepartment(deptId)));
		setLoading(false);
	}, [deptId]);

	useEffect(() => {
		void refresh();
	}, [refresh]);
	useEffect(() => {
		void connectorService.list().then((r) => setConnectors(unwrap(r)));
	}, []);

	const onTest = async (ds: DataSource) => {
		setTestingId(ds.id);
		const res = unwrap(await dataSourceService.testConnection(ds.id));
		setTestingId(null);
		if (res.success) message.success(`${ds.name}：${res.message}（${res.elapsedMillis}ms）`);
		else message.error(`${ds.name}：${res.message}`);
		await refresh();
	};

	const onSubmit = async (payload: DataSourceUpsertPayload) => {
		if (!deptId) return;
		if (editing) unwrap(await dataSourceService.update(editing.id, payload));
		else unwrap(await dataSourceService.create(deptId, payload));
		message.success(editing ? "已更新" : "已创建");
		setModalOpen(false);
		setEditing(null);
		await refresh();
	};

	const onDelete = async (ds: DataSource) => {
		unwrap(await dataSourceService.remove(ds.id));
		message.success("已删除");
		await refresh();
	};

	const columns: CompactColumn<DataSource>[] = [
		{
			key: "name",
			title: "名称",
			width: 168,
			render: (_v, r) => (
				<button
					type="button"
					onClick={() => setDetail(r)}
					style={{ all: "unset", cursor: "pointer", color: "var(--accent)", fontWeight: 600, whiteSpace: "nowrap" }}
				>
					{r.name}
				</button>
			),
		},
		{ key: "type", title: "类型", dataIndex: "type", width: 110 },
		{ key: "connector", title: "连接器", dataIndex: "connector", width: 110 },
		{
			key: "scope",
			title: "归属",
			width: 110,
			render: (_v, r) => (
				<Tag color={r.scope === "platform" ? "blue" : "default"}>{r.scope === "platform" ? "平台共享" : "本部门"}</Tag>
			),
		},
		{
			key: "status",
			title: "状态",
			width: 100,
			render: (_v, r) => (
				<span style={{ whiteSpace: "nowrap" }}>
					<StatusDot tone={STATUS_TONE[r.status]} label={STATUS_LABEL[r.status]} />
				</span>
			),
		},
		{ key: "lastTestedAt", title: "最近连通", width: 140, render: (_v, r) => r.lastTestedAt ?? "—" },
		{
			key: "actions",
			title: "操作",
			width: 220,
			render: (_v, r) => {
				const isPlatform = r.scope === "platform";
				return (
					<Space size={4}>
						<Button size="small" type="link" loading={testingId === r.id} onClick={() => onTest(r)}>
							测试
						</Button>
						<Button size="small" type="link" onClick={() => setDetail(r)}>
							详情
						</Button>
						<Button size="small" type="link" disabled={isPlatform} onClick={() => { setEditing(r); setModalOpen(true); }}>
							编辑
						</Button>
						<Popconfirm title="删除该数据源？" okText="删除" cancelText="取消" disabled={isPlatform} onConfirm={() => onDelete(r)}>
							<Button size="small" type="link" danger disabled={isPlatform}>
								删除
							</Button>
						</Popconfirm>
					</Space>
				);
			},
		},
	];

	return (
		<div>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
				<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>
					平台共享源对部门只读；本部门本地源可增删改。物理接入与密钥在平台/部门层统一管控。
				</span>
				<Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing(null); setModalOpen(true); }}>
					新建数据源
				</Button>
			</div>

			<CompactTable<DataSource> columns={columns} data={sources} rowKey="id" loading={loading} />

			<DataSourceFormModal
				open={modalOpen}
				editing={editing}
				connectors={connectors}
				onSubmit={onSubmit}
				onClose={() => { setModalOpen(false); setEditing(null); }}
			/>
			<DataSourceDetailDrawer source={detail} onClose={() => setDetail(null)} />
		</div>
	);
}
