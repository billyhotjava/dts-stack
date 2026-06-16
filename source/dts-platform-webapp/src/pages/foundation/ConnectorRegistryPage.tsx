import { useEffect, useMemo, useState } from "react";
import { AppstoreOutlined, } from "@ant-design/icons";
import { Button, Card, Descriptions, Drawer, Select, Space, Switch, Tag, Typography, message } from "antd";
import { CompactTable } from "@/components/table";
import type { TableProps } from "antd";
import { PageHeader } from "@/components/page-header";
import connectorsService, { type InfraConnector } from "@/api/services/connectorsService";
import { useRouter } from "@/routes/hooks";
import { formatTime } from "@/utils/textUtils";

const { Text } = Typography;

const CAPABILITY_COLUMN_WIDTH = 240;
const CONNECTOR_TABLE_SCROLL_X = 1360;

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

function CapabilityTags({ connector, compact = false }: { connector: InfraConnector; compact?: boolean }) {
	const keys = Object.keys(CAPABILITY_LABELS).filter((key) => capabilityEnabled(connector, key));
	const visibleKeys = compact ? keys.slice(0, 5) : keys;
	const overflow = keys.length - visibleKeys.length;
	if (!keys.length) {
		return <Text type="secondary">-</Text>;
	}
	return (
		<Space size={[4, 4]} wrap className="max-w-full min-w-0">
			{visibleKeys.map((key) => (
				<Tag key={key} color={key === "cdc" ? "orange" : "processing"} className="whitespace-nowrap">
					{CAPABILITY_LABELS[key]}
				</Tag>
			))}
			{overflow > 0 ? <Tag className="whitespace-nowrap">+{overflow}</Tag> : null}
		</Space>
	);
}

export default function ConnectorRegistryPage() {
	const router = useRouter();
	const [list, setList] = useState<InfraConnector[]>([]);
	const [loading, setLoading] = useState(false);
	const [category, setCategory] = useState("");
	const [includeDisabled, setIncludeDisabled] = useState(false);
	const [selected, setSelected] = useState<InfraConnector | null>(null);
	const [seeding, setSeeding] = useState(false);

	const loadList = async () => {
		setLoading(true);
		try {
			const data = await connectorsService.list({
				category: category || undefined,
				includeDisabled,
			});
			setList(Array.isArray(data) ? data : []);
		} catch {
			setList([]);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadList();
	}, [category, includeDisabled]);

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
				width: 220,
				render: (_: string, record) => (
					<Space direction="vertical" size={0}>
						<Button type="link" className="h-auto p-0" onClick={() => setSelected(record)}>
							{record.name || record.connectorKey}
						</Button>
						<Text type="secondary" className="text-xs">
							{record.connectorKey}
						</Text>
					</Space>
				),
			},
			{
				title: "分类",
				dataIndex: "category",
				key: "category",
				width: 110,
				render: (value: string) => <Tag color={CATEGORY_COLORS[value] || "default"}>{value || "-"}</Tag>,
			},
			{ title: "源类型", dataIndex: "sourceType", key: "sourceType", width: 120 },
			{
				title: "默认引擎",
				dataIndex: "defaultEngine",
				key: "defaultEngine",
				width: 130,
				render: (value: string) => <Tag color={ENGINE_COLORS[value] || "default"}>{value || "-"}</Tag>,
			},
			{
				title: "能力",
				key: "capabilities",
				width: CAPABILITY_COLUMN_WIDTH,
				render: (_: unknown, record) => <CapabilityTags connector={record} compact />,
			},
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 100,
				render: (value: string) => <Tag color={value === "ACTIVE" ? "success" : "default"}>{value || "-"}</Tag>,
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
				width: 180,
				render: (value: string) => formatTime(value),
			},
			{
				title: "操作",
				key: "actions",
				width: 260,
				fixed: "right",
				render: (_: unknown, record) => (
					<Space size="small" wrap>
						<Button size="small" onClick={() => router.push("/foundation/data-sources")}>
							创建数据源
						</Button>
						<Button size="small" onClick={() => setSelected(record)}>
							配置
						</Button>
						<Button size="small" onClick={() => setSelected(record)}>
							查看模板
						</Button>
						<Button size="small" disabled title="当前连接器目录接口未开放启用动作，请同步内置目录后在数据源页使用">
							启用
						</Button>
						<Button size="small" disabled title="当前连接器目录接口未开放停用动作，请通过停用筛选核对状态">
							停用
						</Button>
					</Space>
				),
			},
		],
		[router]
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据接入基础 / 连接器目录"
				actions={
					<Space wrap>
						<Button onClick={() => router.push("/foundation/data-sources")}>创建数据源</Button>
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
						<Space size={6}>
							<Text type="secondary">停用</Text>
							<Switch size="small" checked={includeDisabled} onChange={setIncludeDisabled} />
						</Space>
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
				scroll={{ x: CONNECTOR_TABLE_SCROLL_X }}
				pagination={{ defaultPageSize: 10 }}
			/>

				<Drawer
				title={selected?.name || "连接器详情"}
				open={Boolean(selected)}
				onClose={() => setSelected(null)}
				width={720}
				destroyOnClose
			>
				{selected ? (
					<Space direction="vertical" size="large" className="w-full">
						<Descriptions bordered size="small" column={2}>
							<Descriptions.Item label="连接器 Key">{selected.connectorKey}</Descriptions.Item>
							<Descriptions.Item label="状态">{selected.status || "-"}</Descriptions.Item>
							<Descriptions.Item label="分类">{selected.category || "-"}</Descriptions.Item>
							<Descriptions.Item label="源类型">{selected.sourceType || "-"}</Descriptions.Item>
							<Descriptions.Item label="默认引擎">{selected.defaultEngine || "-"}</Descriptions.Item>
							<Descriptions.Item label="排序">{selected.displayOrder ?? "-"}</Descriptions.Item>
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
							<Text strong>配置 Schema</Text>
							<div className="mt-2">
								<Descriptions bordered size="small" column={1}>
									<Descriptions.Item label="必填字段">
										{toArray(selected.configSchema?.required).join(", ") || "-"}
									</Descriptions.Item>
									<Descriptions.Item label="可选字段">
										{toArray(selected.configSchema?.optional).join(", ") || "-"}
									</Descriptions.Item>
									<Descriptions.Item label="默认值">{renderJson(selected.configSchema?.defaults)}</Descriptions.Item>
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
