import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router";
import {
	Alert,
	Breadcrumb,
	Button,
	Card,
	Descriptions,
	Dropdown,
	Empty,
	Modal,
	Space,
	Spin,
	Tabs,
	Tag,
	Typography,
	message,
} from "antd";
import { RollbackOutlined, } from "@ant-design/icons";
import { CompactTable } from "@/components/table";
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import dataSourcesService, {
	type ConnectionTestResult,
	type InfraDataSource,
	type SchemaDiscoverResponse,
	type SchemaDiscoverTable,
} from "@/api/services/dataSourcesService";
import { formatTime } from "@/utils/textUtils";
import DataSourceFormModal from "./DataSourceFormModal";
import { isAdminManagedSource, isApiSourceType } from "./dataSources/helpers";

const { Text } = Typography;

const LIST_PATH = "/foundation/data-sources";

/**
 * 数据源详情页
 *
 * - 只读 Descriptions + Schema 探测 Tab
 * - 编辑直接在本页弹 <DataSourceFormModal/>，保存后 reload 详情
 * - 测试 / 删除 / 全链路回退 与列表行能力对齐
 */
export default function DataSourceDetailPage() {
	const { id } = useParams<{ id: string }>();
	const navigate = useNavigate();
	const [source, setSource] = useState<InfraDataSource | null>(null);
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState<string | null>(null);
	const [activeTab, setActiveTab] = useState<string>("basic");
	const [testing, setTesting] = useState(false);
	const [schemaLoading, setSchemaLoading] = useState(false);
	const [schemaResult, setSchemaResult] = useState<SchemaDiscoverResponse | null>(null);
	const [schemaError, setSchemaError] = useState<string | null>(null);
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);
	const [editOpen, setEditOpen] = useState(false);

	const loadDetail = useCallback(async () => {
		if (!id) {
			setLoadError("缺少数据源 ID");
			return;
		}
		setLoading(true);
		setLoadError(null);
		try {
			const data = await dataSourcesService.detail(id);
			setSource(data || null);
			if (!data) setLoadError("未找到该数据源");
		} catch (error: any) {
			setSource(null);
			setLoadError(error?.message || "加载数据源详情失败");
		} finally {
			setLoading(false);
		}
	}, [id]);

	useEffect(() => {
		void loadDetail();
	}, [loadDetail]);

	const adminManaged = isAdminManagedSource(source);
	const apiSource = isApiSourceType(source?.type);

	const handleEdit = () => {
		if (!source?.id || adminManaged) return;
		setEditOpen(true);
	};

	const handleTest = async () => {
		if (!source?.id) return;
		setTesting(true);
		try {
			const result = await dataSourcesService.test(source.id);
			showTestResult(source.name, result || { success: false, message: "未获取到测试结果" });
		} catch (error: any) {
			Modal.error({
				title: `${source.name} 连接测试异常`,
				content: error?.message || "连接测试请求失败，请检查网络或服务状态。",
			});
		} finally {
			setTesting(false);
		}
	};

	const handleDelete = () => {
		if (!source?.id || adminManaged) return;
		Modal.confirm({
			title: "确认删除",
			content: `确定删除数据源 "${source.name}" 吗？删除后该数据源相关入湖任务将无法继续使用。`,
			okType: "danger",
			onOk: async () => {
				try {
					await dataSourcesService.remove(source.id);
					message.success("已删除数据源");
					navigate(LIST_PATH);
				} catch (error: any) {
					message.error(error?.message || "删除失败");
				}
			},
		});
	};

	const handleSchemaDiscover = async () => {
		if (!source?.id) return;
		setSchemaLoading(true);
		setSchemaError(null);
		try {
			const result = await dataSourcesService.schemaDiscover(source.id, {});
			setSchemaResult(result || null);
		} catch (error: any) {
			setSchemaResult(null);
			setSchemaError(error?.message || "Schema 探测失败");
		} finally {
			setSchemaLoading(false);
		}
	};

	// 切到 Schema Tab 时按需触发一次（懒加载，避免页面打开即占用带宽）
	useEffect(() => {
		if (activeTab === "schema" && source?.id && !schemaResult && !schemaLoading && !schemaError && !apiSource) {
			void handleSchemaDiscover();
		}
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [activeTab, source?.id]);

	const moreMenuItems = useMemo(
		() =>
			source?.id
				? [
						{
							key: "rollback",
							icon: <RollbackOutlined />,
							label: "全链路回退",
							danger: true,
							onClick: () => {
								setRollbackRequest({ level: 3, scope: "datasource", dataSourceId: source.id });
								setRollbackOpen(true);
							},
						},
					]
				: [],
		[source?.id],
	);

	return (
		<Card
			title={
				<Space size="small">
					<Button type="text" onClick={() => navigate(LIST_PATH)}>
						返回
					</Button>
					<Breadcrumb
						items={[
							{ title: <a onClick={() => navigate(LIST_PATH)}>数据源连接</a> },
							{ title: source?.name || (id ? `#${id}` : "详情") },
						]}
					/>
				</Space>
			}
			extra={
				<Space size={4}>
					<Button onClick={() => void loadDetail()} loading={loading}>
						刷新
					</Button>
					<Button onClick={() => void handleTest()} loading={testing}>
						测试连接
					</Button>
					<Button type="primary" disabled={adminManaged || !source?.id} onClick={handleEdit}>
						编辑
					</Button>
					<Button danger disabled={adminManaged || !source?.id} onClick={handleDelete}>
						删除
					</Button>
					{moreMenuItems.length > 0 && (
						<Dropdown menu={{ items: moreMenuItems }} trigger={["click"]} placement="bottomRight">
							<Button aria-label="更多操作" >更多操作</Button>
						</Dropdown>
					)}
				</Space>
			}
		>
			{loading && !source ? (
				<div className="flex justify-center py-12">
					<Spin />
				</div>
			) : loadError ? (
				<Alert
					type="error"
					showIcon
					message="加载失败"
					description={loadError}
					action={
						<Button size="small" onClick={() => void loadDetail()}>
							重试
						</Button>
					}
				/>
			) : source ? (
				<>
					{adminManaged ? (
						<Alert
							type="info"
							showIcon
							className="mb-4"
							message="该数据源由管理端统一维护，平台侧不支持编辑或删除。"
						/>
					) : null}
					<Tabs
						activeKey={activeTab}
						onChange={setActiveTab}
						items={[
							{
								key: "basic",
								label: "基础信息",
								children: <BasicInfoPanel source={source} />,
							},
							{
								key: "schema",
								label: "Schema 探测",
								disabled: apiSource,
								children: (
									<SchemaPanel
										apiSource={apiSource}
										loading={schemaLoading}
										result={schemaResult}
										error={schemaError}
										onRefresh={() => void handleSchemaDiscover()}
									/>
								),
							},
						]}
					/>
				</>
			) : (
				<Empty description="未找到数据源" />
			)}

			<RollbackImpactModal
				open={rollbackOpen}
				request={rollbackRequest}
				onClose={() => {
					setRollbackOpen(false);
					setRollbackRequest(null);
				}}
			/>

			<DataSourceFormModal
				open={editOpen}
				editing={source}
				onClose={() => setEditOpen(false)}
				onSaved={() => void loadDetail()}
			/>
		</Card>
	);
}

function BasicInfoPanel({ source }: { source: InfraDataSource }) {
	const heartbeatTag = (() => {
		const status = String(source.heartbeatStatus || source.status || "").toUpperCase();
		if (status === "HEALTHY" || status === "OK" || status === "ONLINE") return <Tag color="green">健康</Tag>;
		if (status === "DEGRADED" || status === "WARNING") return <Tag color="gold">降级</Tag>;
		if (status === "FAILED" || status === "ERROR" || status === "OFFLINE") return <Tag color="red">故障</Tag>;
		return source.heartbeatStatus || source.status ? <Tag>{source.heartbeatStatus || source.status}</Tag> : <Text type="secondary">—</Text>;
	})();

	return (
		<Descriptions column={2} bordered size="middle">
			<Descriptions.Item label="名称">{source.name || "—"}</Descriptions.Item>
			<Descriptions.Item label="ID">
				<Text copyable>{source.id}</Text>
			</Descriptions.Item>
			<Descriptions.Item label="连接器">
				{source.connectorName || source.connectorKey || "—"}
				{source.connectorCategory ? <Tag className="ml-2">{source.connectorCategory}</Tag> : null}
			</Descriptions.Item>
			<Descriptions.Item label="源类型">{source.type || "—"}</Descriptions.Item>
			<Descriptions.Item label="默认引擎">{source.defaultEngine || "—"}</Descriptions.Item>
			<Descriptions.Item label="归属部门">{source.ownerDept || "—"}</Descriptions.Item>
			<Descriptions.Item label="JDBC URL" span={2}>
				{source.jdbcUrl ? <Text copyable code>{source.jdbcUrl}</Text> : "—"}
			</Descriptions.Item>
			<Descriptions.Item label="用户名">{source.username || "—"}</Descriptions.Item>
			<Descriptions.Item label="是否含密钥">{source.hasSecrets ? <Tag color="blue">是</Tag> : <Tag>否</Tag>}</Descriptions.Item>
			<Descriptions.Item label="引擎版本">{source.engineVersion || "—"}</Descriptions.Item>
			<Descriptions.Item label="驱动版本">{source.driverVersion || "—"}</Descriptions.Item>
			<Descriptions.Item label="心跳状态">{heartbeatTag}</Descriptions.Item>
			<Descriptions.Item label="心跳连续失败">{source.heartbeatFailureCount ?? 0}</Descriptions.Item>
			<Descriptions.Item label="最近测试耗时">
				{source.lastTestElapsedMillis != null ? `${source.lastTestElapsedMillis} ms` : "—"}
			</Descriptions.Item>
			<Descriptions.Item label="最近验证时间">{formatTime(source.lastVerifiedAt)}</Descriptions.Item>
			<Descriptions.Item label="最近心跳时间">{formatTime(source.lastHeartbeatAt)}</Descriptions.Item>
			<Descriptions.Item label="创建时间">{formatTime(source.createdAt)}</Descriptions.Item>
			<Descriptions.Item label="最近更新时间">{formatTime(source.lastUpdatedAt)}</Descriptions.Item>
			<Descriptions.Item label="描述" span={2}>
				{source.description || <Text type="secondary">—</Text>}
			</Descriptions.Item>
			{source.lastError ? (
				<Descriptions.Item label="最近错误" span={2}>
					<Text type="danger">{source.lastError}</Text>
				</Descriptions.Item>
			) : null}
			{source.props && Object.keys(source.props).length > 0 ? (
				<Descriptions.Item label="扩展配置 (props)" span={2}>
					<pre className="m-0 max-h-64 overflow-auto rounded bg-slate-50 p-2 text-xs">
						{JSON.stringify(source.props, null, 2)}
					</pre>
				</Descriptions.Item>
			) : null}
		</Descriptions>
	);
}

function SchemaPanel({
	apiSource,
	loading,
	result,
	error,
	onRefresh,
}: {
	apiSource: boolean;
	loading: boolean;
	result: SchemaDiscoverResponse | null;
	error: string | null;
	onRefresh: () => void;
}) {
	if (apiSource) {
		return <Alert type="info" showIcon message="API 数据源不适用 JDBC Schema 探测。" />;
	}

	const tables = result?.tables || [];

	return (
		<Space direction="vertical" size="middle" style={{ width: "100%" }}>
			<Space size="small" wrap>
				<Button type="primary" onClick={onRefresh} loading={loading}>
					{result ? "重新探测" : "开始探测"}
				</Button>
				{result?.databaseProduct ? <Tag color="blue">{result.databaseProduct}</Tag> : null}
				{result?.databaseVersion ? <Tag>{result.databaseVersion}</Tag> : null}
				{result?.cached ? <Tag color="gold">缓存</Tag> : result ? <Tag color="green">实时</Tag> : null}
				{result?.elapsedMs != null ? <Text type="secondary">耗时 {result.elapsedMs} ms</Text> : null}
				{result?.discoveredAt ? <Text type="secondary">· 探测于 {formatTime(result.discoveredAt)}</Text> : null}
			</Space>

			{error ? <Alert type="error" showIcon message="探测失败" description={error} /> : null}

			{result?.drift ? (
				<Alert
					type="warning"
					showIcon
					message={`Schema drift：新增 ${result.drift.addedTables || 0} 表，删除 ${result.drift.removedTables || 0} 表，变更 ${result.drift.changedTables || 0} 表`}
					description={
						result.drift.detailsJson ? (
							<pre className="m-0 mt-2 max-h-48 overflow-auto text-xs">{result.drift.detailsJson}</pre>
						) : undefined
					}
				/>
			) : null}

			{loading && !result ? (
				<div className="flex justify-center py-8">
					<Spin tip="正在探测…" />
				</div>
			) : tables.length > 0 ? (
				<CompactTable<SchemaDiscoverTable>
					rowKey={(row) => `${row.schema || ""}.${row.name}`}
					dataSource={tables}
					pagination={{ pageSize: 20, showSizeChanger: true, pageSizeOptions: ["20", "50", "100"] }}
					columns={[
						{ title: "Schema", dataIndex: "schema", key: "schema", width: 140, render: (v: string) => v || "—" },
						{ title: "表名", dataIndex: "name", key: "name", width: 240 },
						{
							title: "类型",
							dataIndex: "type",
							key: "type",
							width: 100,
							render: (_, row) => (row.view ? <Tag color="purple">VIEW</Tag> : <Tag>TABLE</Tag>),
						},
						{
							title: "列数",
							key: "columnCount",
							width: 80,
							render: (_, row) => row.columns?.length ?? 0,
						},
						{
							title: "主键",
							key: "pk",
							render: (_, row) => row.primaryKeys?.join(", ") || <Text type="secondary">—</Text>,
						},
						{
							title: "注释",
							dataIndex: "comment",
							key: "comment",
							render: (v: string) => v || <Text type="secondary">—</Text>,
						},
					]}
				/>
			) : !loading ? (
				<Empty description={result ? "未探测到任何表" : "尚未执行探测"} />
			) : null}
		</Space>
	);
}

function showTestResult(name: string, result: ConnectionTestResult) {
	const isOk = Boolean(result?.success);
	Modal[isOk ? "success" : "error"]({
		title: isOk ? `${name} 连接成功` : `${name} 连接失败`,
		content: (
			<div>
				{result.message ? <div>{result.message}</div> : null}
				{result.elapsedMillis != null ? <div className="mt-2 text-xs text-slate-500">耗时 {result.elapsedMillis} ms</div> : null}
				{result.engineVersion ? <div className="text-xs text-slate-500">引擎版本：{result.engineVersion}</div> : null}
				{result.driverVersion ? <div className="text-xs text-slate-500">驱动版本：{result.driverVersion}</div> : null}
				{result.suggestion ? <div className="mt-2 text-xs text-amber-600">建议：{result.suggestion}</div> : null}
			</div>
		),
	});
}
