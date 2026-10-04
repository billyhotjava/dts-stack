import { AppstoreOutlined } from "@ant-design/icons";
import type { TableProps } from "antd";
import { Button, Card, Descriptions, Drawer, message, Select, Space, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import connectorsService, { type InfraConnector } from "@/api/services/connectorsService";
import { PageHeader } from "@/components/page-header";
import { actionColumn, CompactTable } from "@/components/table";
import { useRouter } from "@/routes/hooks";
import { formatTime } from "@/utils/textUtils";

const { Text } = Typography;

const CAPABILITY_COLUMN_WIDTH = 160;
const CONNECTOR_ACTION_COLUMN_WIDTH = 220;
const CONNECTOR_TABLE_SCROLL_X = 1280;

type ConnectorDrawerMode = "detail" | "config";

const CATEGORY_OPTIONS = [
	{ label: "全部", value: "" },
	{ label: "数据库", value: "DATABASE" },
	{ label: "文件", value: "FILE" },
	{ label: "API", value: "API" },
	{ label: "流式", value: "STREAM" },
];

const CAPABILITY_LABELS: Record<string, string> = {
	connectionTest: "连接测试",
	schemaDiscover: "Schema",
	samplePreview: "采样",
	fullRefresh: "全量",
	append: "追加",
	timestampIncremental: "时间戳增量",
	primaryKeyIncremental: "主键增量",
	cdc: "CDC",
	odsGeneration: "ODS",
	dbtSourceGeneration: "dbt source",
};

const CATEGORY_COLORS: Record<string, string> = {
	DATABASE: "blue",
	FILE: "cyan",
	API: "purple",
	STREAM: "orange",
};

const ENGINE_COLORS: Record<string, string> = {
	ADDAX: "green",
	API_RUNTIME: "purple",
	FILE: "cyan",
	FUTURE: "default",
};

const CONNECTOR_STATUS_LABELS: Record<string, string> = {
	ACTIVE: "可选用",
	INACTIVE: "已停用",
};

const DRIVER_STATUS_LABELS: Record<string, string> = {
	READY: "驱动就绪",
	MISSING: "缺少驱动",
	CUSTOM_REQUIRED: "自定义驱动",
	NOT_REQUIRED: "无需驱动",
};

const DRIVER_POLICY_LABELS: Record<string, string> = {
	BUNDLED: "随连接器内置",
	ADMIN_PROVIDED: "管理员补充",
	CUSTOM: "自定义",
	NOT_REQUIRED: "无需驱动",
};

const toArray = (value: unknown): string[] => {
	if (Array.isArray(value)) {
		return value.map((item) => String(item));
	}
	if (typeof value === "string" && value.trim()) {
		return [value.trim()];
	}
	return [];
};

const renderJson = (value?: Record<string, any>) => {
	if (!value || Object.keys(value).length === 0) {
		return "-";
	}
	return (
		<pre className="m-0 max-h-56 overflow-auto rounded bg-slate-50 p-3 text-xs leading-5 text-slate-700">
			{JSON.stringify(value, null, 2)}
		</pre>
	);
};

const capabilityEnabled = (connector: InfraConnector, key: string) => connector.capabilities?.[key] === true;

const getDrawerTitle = (connector: InfraConnector | null, mode: ConnectorDrawerMode) => {
	if (!connector) {
		return "连接器详情";
	}
	const base = connector.name || connector.connectorKey;
	if (mode === "config") {
		return `${base} / 配置要求`;
	}
	return `${base} / 连接器定义`;
};

function CapabilityTags({ connector, compact = false }: { connector: InfraConnector; compact?: boolean }) {
	const keys = Object.keys(CAPABILITY_LABELS).filter((key) => capabilityEnabled(connector, key));
	const visibleKeys = compact ? keys.slice(0, 2) : keys;
	const overflow = keys.length - visibleKeys.length;
	if (!keys.length) {
		return <Text type="secondary">-</Text>;
	}
	return (
		<Space
			size={[4, 4]}
			wrap={!compact}
			className={compact ? "connector-registry-capability-tags max-w-full min-w-0" : "max-w-full min-w-0"}
		>
			{visibleKeys.map((key) => (
				<Tag key={key} color={key === "cdc" ? "orange" : "processing"} className="whitespace-nowrap">
					{CAPABILITY_LABELS[key]}
				</Tag>
			))}
			{overflow > 0 ? <Tag className="whitespace-nowrap">+{overflow}</Tag> : null}
		</Space>
	);
}

function DriverStatusTag({ connector }: { connector: InfraConnector }) {
	const status = connector.driver?.status;
	const color =
		status === "READY"
			? "success"
			: status === "MISSING"
				? "error"
				: status === "CUSTOM_REQUIRED"
					? "processing"
					: "default";
	return <Tag color={color}>{DRIVER_STATUS_LABELS[status || ""] || "未检测"}</Tag>;
}

export default function ConnectorRegistryPage() {
	const router = useRouter();
	const [list, setList] = useState<InfraConnector[]>([]);
	const [loading, setLoading] = useState(false);
	const [category, setCategory] = useState("");
	const [selected, setSelected] = useState<InfraConnector | null>(null);
	const [drawerMode, setDrawerMode] = useState<ConnectorDrawerMode>("detail");
	const [seeding, setSeeding] = useState(false);

	const openConnectorDrawer = useCallback((connector: InfraConnector, mode: ConnectorDrawerMode) => {
		setSelected(connector);
		setDrawerMode(mode);
	}, []);

	const openDataSourceCreate = useCallback(
		(connectorKey?: string) => {
			const target = connectorKey
				? `/foundation/connections?create=1&connectorKey=${encodeURIComponent(connectorKey)}`
				: "/foundation/connections?create=1";
			router.push(target);
		},
		[router],
	);

	const loadList = useCallback(async () => {
		setLoading(true);
		try {
			const data = await connectorsService.list({
				category: category || undefined,
			});
			setList(Array.isArray(data) ? data : []);
		} catch {
			setList([]);
		} finally {
			setLoading(false);
		}
	}, [category]);

	useEffect(() => {
		void loadList();
	}, [loadList]);

	const handleSeed = async () => {
		setSeeding(true);
		try {
			const data = await connectorsService.seed();
			setList(Array.isArray(data) ? data : []);
			message.success("连接器目录已刷新");
		} catch {
			// handled by global interceptor
		} finally {
			setSeeding(false);
		}
	};

	const summary = useMemo(() => {
		const byCategory = new Map<string, number>();
		for (const item of list) {
			const key = item.category || "UNKNOWN";
			byCategory.set(key, (byCategory.get(key) || 0) + 1);
		}
		return Array.from(byCategory.entries());
	}, [list]);

	const columns = useMemo<TableProps<InfraConnector>["columns"]>(
		() => [
			{
				title: "连接器",
				dataIndex: "name",
				sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
				key: "name",
				width: 190,
				render: (_: string, record) => (
					<Space size={6}>
						<Button type="link" className="h-auto p-0" onClick={() => openConnectorDrawer(record, "detail")}>
							{record.name || record.connectorKey}
						</Button>
						<Text type="secondary">{record.connectorKey}</Text>
					</Space>
				),
			},
			{
				title: "分类",
				dataIndex: "category",
				key: "category",
				width: 90,
				render: (value: string) => <Tag color={CATEGORY_COLORS[value] || "default"}>{value || "-"}</Tag>,
			},
			{ title: "源类型", dataIndex: "sourceType", key: "sourceType", width: 100 },
			{
				title: "默认引擎",
				dataIndex: "defaultEngine",
				key: "defaultEngine",
				width: 100,
				render: (value: string) => <Tag color={ENGINE_COLORS[value] || "default"}>{value || "-"}</Tag>,
			},
			{
				title: "能力",
				key: "capabilities",
				width: CAPABILITY_COLUMN_WIDTH,
				render: (_: unknown, record) => <CapabilityTags connector={record} compact />,
			},
			{
				title: "驱动",
				key: "driver",
				width: 120,
				render: (_: unknown, record) => <DriverStatusTag connector={record} />,
			},
			{
				title: "目录状态",
				dataIndex: "status",
				key: "status",
				width: 100,
				render: (value: string) => (
					<Text type={value === "ACTIVE" ? "success" : "secondary"}>
						{CONNECTOR_STATUS_LABELS[value] || value || "-"}
					</Text>
				),
			},
			{
				title: "更新时间",
				dataIndex: "lastUpdatedAt",
				sorter: (a, b) => {
					const ta = a.lastUpdatedAt ? new Date(a.lastUpdatedAt as any).getTime() : 0;
					const tb = b.lastUpdatedAt ? new Date(b.lastUpdatedAt as any).getTime() : 0;
					return ta - tb;
				},
				key: "lastUpdatedAt",
				width: 150,
				render: (value: string) => formatTime(value),
			},
			actionColumn<InfraConnector>(
				(record) => [
					{
						key: "create-source",
						label: "创建数据源",
						disabled: record.driver?.status === "MISSING",
						onClick: () => openDataSourceCreate(record.connectorKey),
					},
					{ key: "config", label: "配置要求", onClick: () => openConnectorDrawer(record, "config") },
				],
				{ width: CONNECTOR_ACTION_COLUMN_WIDTH },
			),
		],
		[openConnectorDrawer, openDataSourceCreate],
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据接入基础 / 连接器目录"
				actions={
					<Space wrap>
						<Button onClick={() => openDataSourceCreate()}>创建数据源</Button>
						<Button onClick={loadList} disabled={loading}>
							刷新
						</Button>
						<Button type="primary" onClick={handleSeed} loading={seeding}>
							同步内置
						</Button>
					</Space>
				}
			/>
			<Card
				title="连接器目录"
				extra={
					<Space wrap>
						<Select style={{ width: 120 }} value={category} options={CATEGORY_OPTIONS} onChange={setCategory} />
					</Space>
				}
			>
				<Space className="mb-3" size={[8, 8]} wrap>
					<Tag icon={<AppstoreOutlined />}>共 {list.length} 个</Tag>
					{summary.map(([key, count]) => (
						<Tag key={key} color={CATEGORY_COLORS[key] || "default"}>
							{key} {count}
						</Tag>
					))}
				</Space>

				<CompactTable
					rowKey="connectorKey"
					columns={columns}
					dataSource={list}
					loading={loading}
					className="connector-registry-table"
					scroll={{ x: CONNECTOR_TABLE_SCROLL_X }}
					tableLayout="fixed"
					pagination={{ defaultPageSize: 10 }}
				/>

				<Drawer
					title={getDrawerTitle(selected, drawerMode)}
					open={Boolean(selected)}
					onClose={() => setSelected(null)}
					width={720}
					destroyOnClose
					footer={
						selected ? (
							<Space className="w-full justify-end">
								<Button onClick={() => setSelected(null)}>关闭</Button>
								<Button
									type="primary"
									disabled={selected.driver?.status === "MISSING"}
									onClick={() => openDataSourceCreate(selected.connectorKey)}
								>
									创建数据源
								</Button>
							</Space>
						) : null
					}
				>
					{selected ? (
						<Space direction="vertical" size="large" className="w-full">
							{drawerMode === "config" ? (
								<Text type="secondary">按连接器配置要求创建数据源，实际连接参数在数据源页录入。</Text>
							) : null}
							{drawerMode === "detail" ? (
								<Text type="secondary">连接器定义统一声明源类型、执行引擎、能力边界、配置要求和部署兼容性。</Text>
							) : null}

							<Descriptions size="small" column={2}>
								<Descriptions.Item label="连接器 Key">{selected.connectorKey}</Descriptions.Item>
								<Descriptions.Item label="目录状态">
									{selected.status ? CONNECTOR_STATUS_LABELS[selected.status] || selected.status : "-"}
								</Descriptions.Item>
								<Descriptions.Item label="分类">{selected.category || "-"}</Descriptions.Item>
								<Descriptions.Item label="源类型">{selected.sourceType || "-"}</Descriptions.Item>
								<Descriptions.Item label="默认引擎">{selected.defaultEngine || "-"}</Descriptions.Item>
								<Descriptions.Item label="排序">{selected.displayOrder ?? "-"}</Descriptions.Item>
								<Descriptions.Item label="驱动状态">
									<DriverStatusTag connector={selected} />
								</Descriptions.Item>
								<Descriptions.Item label="驱动策略">
									{DRIVER_POLICY_LABELS[selected.driver?.policy || ""] || "-"}
								</Descriptions.Item>
								<Descriptions.Item label="驱动主类" span={2}>
									{selected.driver?.driverClass || "-"}
								</Descriptions.Item>
								<Descriptions.Item label="驱动文件" span={2}>
									{selected.driver?.fileName || selected.driver?.message || "-"}
								</Descriptions.Item>
								<Descriptions.Item label="更新时间" span={2}>
									{formatTime(selected.lastUpdatedAt)}
								</Descriptions.Item>
								<Descriptions.Item label="说明" span={2}>
									{selected.description || "-"}
								</Descriptions.Item>
							</Descriptions>

							<div>
								<Text strong>能力矩阵</Text>
								<div className="mt-2">
									<CapabilityTags connector={selected} />
								</div>
							</div>

							<div>
								<Text strong>配置要求</Text>
								<div className="mt-2">
									<Descriptions size="small" column={1}>
										<Descriptions.Item label="必填字段">
											{toArray(selected.configSchema?.required).join(", ") || "-"}
										</Descriptions.Item>
										<Descriptions.Item label="可选字段">
											{toArray(selected.configSchema?.optional).join(", ") || "-"}
										</Descriptions.Item>
										<Descriptions.Item label="新建默认值">
											{renderJson(selected.configSchema?.defaults)}
										</Descriptions.Item>
										<Descriptions.Item label="敏感字段">
											{selected.sensitiveFields?.length ? selected.sensitiveFields.join(", ") : "-"}
										</Descriptions.Item>
									</Descriptions>
								</div>
							</div>

							<div>
								<Text strong>部署兼容性</Text>
								<div className="mt-2">{renderJson(selected.compatibility)}</div>
							</div>
						</Space>
					) : null}
				</Drawer>
			</Card>
		</div>
	);
}
