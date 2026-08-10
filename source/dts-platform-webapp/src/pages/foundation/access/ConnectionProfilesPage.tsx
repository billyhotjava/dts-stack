import { CheckCircleOutlined, CloseCircleOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Input, Modal, message, Select, Space, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import dataSourcesService, { type ConnectionTestResult, type InfraDataSource } from "@/api/services/dataSourcesService";
import { PageHeader } from "@/components/page-header";
import { CompactTable } from "@/components/table";
import { formatTime } from "@/utils/textUtils";
import { isAdminManagedSource, isApiSourceType, isFileSource } from "../dataSources/helpers";
import ConnectionProfileFormModal from "./ConnectionProfileFormModal";

const { Text } = Typography;

type ProfileKind = "all" | "database" | "api" | "file";

const profileKind = (source: InfraDataSource): Exclude<ProfileKind, "all"> => {
	if (isApiSourceType(source.type)) return "api";
	if (isFileSource(source.type)) return "file";
	return "database";
};

const kindMeta: Record<Exclude<ProfileKind, "all">, { color: string; label: string }> = {
	database: { color: "blue", label: "数据库" },
	api: { color: "purple", label: "API" },
	file: { color: "cyan", label: "文件" },
};

const showConnectionTestResult = (name: string, result: ConnectionTestResult) => {
	const success = Boolean(result?.success);
	Modal[success ? "success" : "error"]({
		title: success ? `${name} 连接成功` : `${name} 连接失败`,
		icon: success ? <CheckCircleOutlined /> : <CloseCircleOutlined />,
		content: (
			<Space direction="vertical" size={4}>
				{result?.message ? <Text>{result.message}</Text> : null}
				{result?.elapsedMillis != null ? <Text type="secondary">耗时：{result.elapsedMillis} ms</Text> : null}
				{result?.engineVersion ? <Text type="secondary">数据库版本：{result.engineVersion}</Text> : null}
				{result?.driverVersion ? <Text type="secondary">驱动版本：{result.driverVersion}</Text> : null}
				{result?.suggestion ? <Text type="warning">处理建议：{result.suggestion}</Text> : null}
			</Space>
		),
	});
};

export default function ConnectionProfilesPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const [rows, setRows] = useState<InfraDataSource[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>();
	const [keyword, setKeyword] = useState("");
	const [kind, setKind] = useState<ProfileKind>("all");
	const [formOpen, setFormOpen] = useState(false);
	const [editing, setEditing] = useState<InfraDataSource | null>(null);
	const [initialConnectorKey, setInitialConnectorKey] = useState<string>();
	const [testingId, setTestingId] = useState<string>();

	const load = useCallback(async () => {
		setLoading(true);
		setError(undefined);
		try {
			const data = await dataSourcesService.list();
			setRows(Array.isArray(data) ? data : []);
		} catch (loadError) {
			setRows([]);
			setError(loadError instanceof Error ? loadError.message : "连接配置加载失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => {
		if (searchParams.get("create") !== "1") return;
		setEditing(null);
		setInitialConnectorKey(searchParams.get("connectorKey") || undefined);
		setFormOpen(true);
		const next = new URLSearchParams(searchParams);
		next.delete("create");
		next.delete("connectorKey");
		setSearchParams(next, { replace: true });
	}, [searchParams, setSearchParams]);

	const filteredRows = useMemo(() => {
		const query = keyword.trim().toLowerCase();
		return rows.filter((row) => {
			if (kind !== "all" && profileKind(row) !== kind) return false;
			if (!query) return true;
			return [row.name, row.connectorName, row.connectorKey, row.type, row.jdbcUrl, row.ownerDept]
				.filter(Boolean)
				.some((value) => String(value).toLowerCase().includes(query));
		});
	}, [keyword, kind, rows]);

	const openCreate = () => {
		setEditing(null);
		setInitialConnectorKey(undefined);
		setFormOpen(true);
	};

	const handleTest = async (record: InfraDataSource) => {
		setTestingId(record.id);
		try {
			const result = await dataSourcesService.test(record.id);
			showConnectionTestResult(record.name, result || { success: false, message: "未获取到测试结果" });
			void load();
		} catch (testError) {
			Modal.error({
				title: `${record.name} 连接测试异常`,
				content: testError instanceof Error ? testError.message : "连接测试请求失败",
			});
		} finally {
			setTestingId(undefined);
		}
	};

	const handleDelete = (record: InfraDataSource) => {
		if (isAdminManagedSource(record)) {
			message.info("系统管理的默认数据湖不能在这里删除");
			return;
		}
		Modal.confirm({
			title: "删除连接配置",
			content: `确认删除“${record.name}”？已绑定任务将无法继续运行。`,
			okType: "danger",
			onOk: async () => {
				await dataSourcesService.remove(record.id);
				message.success("连接配置已删除");
				await load();
			},
		});
	};

	const columns: ColumnsType<InfraDataSource> = [
		{
			title: "名称",
			dataIndex: "name",
			key: "name",
			width: 220,
			render: (value, record) => (
				<Button type="link" className="h-auto p-0" onClick={() => navigate(`/foundation/connections/${record.id}`)}>
					{value}
				</Button>
			),
		},
		{
			title: "类型",
			key: "kind",
			width: 100,
			render: (_, record) => {
				const meta = kindMeta[profileKind(record)];
				return <Tag color={meta.color}>{meta.label}</Tag>;
			},
		},
		{
			title: "连接器",
			key: "connector",
			width: 180,
			render: (_, record) => record.connectorName || record.connectorKey || record.type || "—",
		},
		{
			title: "地址 / JDBC",
			key: "endpoint",
			ellipsis: true,
			render: (_, record) => record.jdbcUrl || String(record.props?.baseUrl || record.props?.url || "—"),
		},
		{ title: "归属部门", dataIndex: "ownerDept", key: "ownerDept", width: 140, render: (value) => value || "—" },
		{
			title: "状态",
			key: "status",
			width: 110,
			render: (_, record) => {
				const healthy = ["ACTIVE", "HEALTHY", "OK", "ONLINE"].includes(
					String(record.heartbeatStatus || record.status || "").toUpperCase(),
				);
				return <Tag color={healthy ? "success" : undefined}>{healthy ? "可用" : "未验证"}</Tag>;
			},
		},
		{
			title: "最近验证",
			dataIndex: "lastVerifiedAt",
			key: "lastVerifiedAt",
			width: 170,
			render: (value) => formatTime(value) || "—",
		},
		{
			title: "操作",
			key: "actions",
			fixed: "right",
			width: 235,
			render: (_, record) => {
				const managed = isAdminManagedSource(record);
				return (
					<Space size={4}>
						<Button type="link" onClick={() => void handleTest(record)} loading={testingId === record.id}>
							测试
						</Button>
						<Button type="link" onClick={() => navigate(`/foundation/connections/${record.id}`)}>
							详情
						</Button>
						<Button
							type="link"
							disabled={managed}
							onClick={() => {
								setEditing(record);
								setFormOpen(true);
							}}
						>
							编辑
						</Button>
						<Button type="link" danger disabled={managed} onClick={() => handleDelete(record)}>
							删除
						</Button>
					</Space>
				);
			},
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="连接管理"
				actions={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={() => void load()} loading={loading}>
							刷新
						</Button>
						<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
							新建连接
						</Button>
					</Space>
				}
			/>
			<div className="rounded-lg bg-white p-4">
				<Space className="mb-4" wrap>
					<Input.Search
						allowClear
						placeholder="搜索名称、连接器或地址"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						style={{ width: 320 }}
					/>
					<Select<ProfileKind>
						value={kind}
						onChange={setKind}
						style={{ width: 150 }}
						options={[
							{ value: "all", label: "全部类型" },
							{ value: "database", label: "数据库" },
							{ value: "api", label: "API" },
							{ value: "file", label: "文件" },
						]}
					/>
				</Space>
				{error ? <Alert className="mb-4" type="error" showIcon message="加载失败" description={error} /> : null}
				<CompactTable<InfraDataSource>
					rowKey="id"
					loading={loading}
					dataSource={filteredRows}
					columns={columns}
					pagination={{ defaultPageSize: 10 }}
				/>
			</div>
			<ConnectionProfileFormModal
				open={formOpen}
				editing={editing}
				initialConnectorKey={initialConnectorKey}
				onClose={() => {
					setFormOpen(false);
					setEditing(null);
					setInitialConnectorKey(undefined);
				}}
				onSaved={() => {
					setFormOpen(false);
					setEditing(null);
					setInitialConnectorKey(undefined);
					void load();
				}}
			/>
		</div>
	);
}
