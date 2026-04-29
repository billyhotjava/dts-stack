import { useEffect, useMemo, useState } from "react";
import { AppstoreOutlined, ReloadOutlined, SyncOutlined } from "@ant-design/icons";
import { Button, Card, Descriptions, Drawer, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import type { TableProps } from "antd";
import connectorsService, { type InfraConnector } from "@/api/services/connectorsService";
import { formatTime } from "@/utils/textUtils";

const { Text } = Typography;

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
		<Space size={[4, 4]} wrap>
			{visibleKeys.map((key) => (
				<Tag key={key} color={key === "cdc" ? "orange" : "processing"}>
					{CAPABILITY_LABELS[key]}
				</Tag>
			))}
			{overflow > 0 ? <Tag>+{overflow}</Tag> : null}
		</Space>
	);
}

export default function ConnectorRegistryPage() {
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
				key: "lastUpdatedAt",
				width: 180,
				render: (value: string) => formatTime(value),
			},
		],
		[]
	);

	return (
		<Card
			title="连接器目录"
			extra={
				<Space wrap>
					<Select style={{ width: 120 }} value={category} options={CATEGORY_OPTIONS} onChange={setCategory} />
					<Space size={6}>
						<Text type="secondary">停用</Text>
						<Switch size="small" checked={includeDisabled} onChange={setIncludeDisabled} />
					</Space>
					<Button icon={<ReloadOutlined />} onClick={loadList} disabled={loading}>
						刷新
					</Button>
					<Button icon={<SyncOutlined />} onClick={handleSeed} loading={seeding}>
						同步内置
					</Button>
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

			<Table
				rowKey="connectorKey"
				columns={columns}
				dataSource={list}
				loading={loading}
				scroll={{ x: 1040 }}
				pagination={{ pageSize: 12 }}
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
	);
}
