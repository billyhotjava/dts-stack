import { ReloadOutlined } from "@ant-design/icons";
import { Alert, Button, Descriptions, Empty, Space, Spin, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useRef, useState } from "react";
import {
	type ApiAuthProviderDescriptorDTO,
	type ApiConnectorContractDTO,
	type DefaultDestinationStatus,
	type IngestionAccessDefaultPolicyDTO,
	type IngestionConnectorCapabilityDTO,
	type IngestionTaskTemplateDTO,
	ingestionTaskAPI,
} from "@/api/ingestion";
import { PageHeader } from "@/components/page-header";
import styles from "./AccessDefaultsPage.module.css";

const { Text } = Typography;
const HIDDEN_VALUE = "[已隐藏]";
const SENSITIVE_KEY =
	/pass(?:word|wd)?|secret|token|authorization|credential|api[-_]?key|access[-_]?key|private[-_]?key/i;
const SENSITIVE_VALUE_PAIR =
	/([?&;]\s*[a-z0-9_.-]*(?:pass(?:word|wd)?|secret|token|authorization|credential|api[-_]?key|access[-_]?key|private[-_]?key)[a-z0-9_.-]*=)[^&#;\s]*/gi;
const SENSITIVE_INLINE_PAIR =
	/(\b[a-z0-9_.-]*(?:pass(?:word|wd)?|secret|token|authorization|credential|api[-_]?key|access[-_]?key|private[-_]?key)[a-z0-9_.-]*\s*[:=]\s*)(?:(?:Bearer|Basic)\s+)?[^,;\s]+/gi;

const redactSensitiveString = (value: string) => {
	if (/^(?:Bearer|Basic)\s+\S+/i.test(value.trim())) return HIDDEN_VALUE;
	return value
		.replace(/\/\/([^/@:\s]+):([^/@\s]+)@/g, `//${HIDDEN_VALUE}@`)
		.replace(SENSITIVE_VALUE_PAIR, `$1${HIDDEN_VALUE}`)
		.replace(SENSITIVE_INLINE_PAIR, `$1${HIDDEN_VALUE}`);
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
	Boolean(value) && typeof value === "object" && !Array.isArray(value);

const isOptionalStringArray = (value: unknown) =>
	value === undefined || (Array.isArray(value) && value.every((item) => typeof item === "string"));

const isValidApiConnectorContract = (value: unknown): value is ApiConnectorContractDTO => {
	if (!isRecord(value) || !isOptionalStringArray(value.sourceTypes) || !isOptionalStringArray(value.syncModes)) {
		return false;
	}
	if (value.authProviders === undefined) return true;
	if (!Array.isArray(value.authProviders)) return false;
	return value.authProviders.every((provider) => {
		if (!isRecord(provider) || typeof provider.id !== "string" || typeof provider.label !== "string") return false;
		if (provider.fields === undefined) return true;
		return (
			Array.isArray(provider.fields) &&
			provider.fields.every(
				(field) =>
					isRecord(field) &&
					typeof field.name === "string" &&
					typeof field.label === "string" &&
					typeof field.type === "string" &&
					(field.sensitive === undefined || typeof field.sensitive === "boolean"),
			)
		);
	});
};

export type AccessDefaultsDependencies = {
	getAccessDefaultPolicy: () => Promise<IngestionAccessDefaultPolicyDTO>;
	getDefaultDestinationStatus: () => Promise<DefaultDestinationStatus>;
	getTaskTemplates: () => Promise<IngestionTaskTemplateDTO[]>;
	getConnectorCapabilities: () => Promise<IngestionConnectorCapabilityDTO[]>;
	getApiConnectorContract: () => Promise<ApiConnectorContractDTO | null>;
};

export type AccessDefaultsSnapshot = {
	policy: IngestionAccessDefaultPolicyDTO;
	destination: DefaultDestinationStatus;
	templates: IngestionTaskTemplateDTO[];
	capabilities: IngestionConnectorCapabilityDTO[];
	apiContract: ApiConnectorContractDTO | null;
};

const DEFAULT_DEPENDENCIES: AccessDefaultsDependencies = {
	getAccessDefaultPolicy: () => ingestionTaskAPI.getAccessDefaultPolicy(),
	getDefaultDestinationStatus: () => ingestionTaskAPI.getDefaultDestinationStatus(),
	getTaskTemplates: () => ingestionTaskAPI.getTaskTemplates(),
	getConnectorCapabilities: () => ingestionTaskAPI.getConnectorCapabilities(),
	getApiConnectorContract: () => ingestionTaskAPI.getApiConnectorContract(),
};

export const isExplicitlyEnabled = (value: unknown): value is true => value === true;

export const redactSensitiveConfiguration = <T,>(value: T, parentKey = ""): T => {
	if (parentKey && SENSITIVE_KEY.test(parentKey)) return HIDDEN_VALUE as T;
	if (Array.isArray(value)) {
		return value.map((item) => redactSensitiveConfiguration(item)) as T;
	}
	if (value && typeof value === "object") {
		return Object.fromEntries(
			Object.entries(value as Record<string, unknown>).map(([key, nestedValue]) => [
				key,
				redactSensitiveConfiguration(nestedValue, key),
			]),
		) as T;
	}
	if (typeof value === "string") return redactSensitiveString(value) as T;
	return value;
};

export const loadAccessDefaultsSnapshot = async (
	dependencies: AccessDefaultsDependencies = DEFAULT_DEPENDENCIES,
): Promise<AccessDefaultsSnapshot> => {
	const [policy, destination, templates, capabilities, apiContract] = await Promise.all([
		dependencies.getAccessDefaultPolicy(),
		dependencies.getDefaultDestinationStatus(),
		dependencies.getTaskTemplates(),
		dependencies.getConnectorCapabilities(),
		dependencies.getApiConnectorContract(),
	]);

	if (
		!policy ||
		typeof policy.policyKey !== "string" ||
		!Number.isInteger(policy.version) ||
		policy.version < 1 ||
		typeof policy.checksum !== "string" ||
		!policy.defaults ||
		typeof policy.defaults !== "object" ||
		Array.isArray(policy.defaults)
	) {
		throw new Error("默认策略响应无效");
	}
	if (!destination || typeof destination.available !== "boolean") {
		throw new Error("默认数据湖状态响应无效");
	}
	if (!Array.isArray(templates) || !Array.isArray(capabilities)) {
		throw new Error("默认配置响应无效");
	}
	if (
		templates.some((template) => !template || typeof template.id !== "string" || typeof template.name !== "string") ||
		capabilities.some(
			(capability) =>
				!capability || typeof capability.connectorType !== "string" || !Array.isArray(capability.capabilities),
		)
	) {
		throw new Error("默认配置条目无效");
	}
	if (apiContract !== null && !isValidApiConnectorContract(apiContract)) {
		throw new Error("API 接入契约响应无效");
	}

	return {
		policy: {
			...policy,
			defaults: redactSensitiveConfiguration(policy.defaults),
		},
		destination,
		templates: templates.map((template) => ({
			...template,
			defaults: redactSensitiveConfiguration(template.defaults),
		})),
		capabilities: capabilities.map((capability) => ({
			...capability,
			constraints: redactSensitiveConfiguration(capability.constraints),
		})),
		apiContract: redactSensitiveConfiguration(apiContract),
	};
};

const ReadinessTag = ({
	ready,
	readyText = "就绪",
	blockedText = "未就绪",
}: {
	ready: boolean;
	readyText?: string;
	blockedText?: string;
}) => <Tag color={ready ? "success" : "error"}>{ready ? readyText : blockedText}</Tag>;

const ConfigurationPreview = ({ value }: { value?: Record<string, unknown> }) => {
	if (!value || Object.keys(value).length === 0) return <Text type="secondary">未声明</Text>;
	return <pre className={styles.codePreview}>{JSON.stringify(value, null, 2)}</pre>;
};

const templateColumns: ColumnsType<IngestionTaskTemplateDTO> = [
	{
		title: "模板",
		dataIndex: "name",
		key: "name",
		width: 190,
		render: (value: string, row) => (
			<Space direction="vertical" size={0}>
				<Text strong>{value || row.id}</Text>
				<Text type="secondary">{row.id}</Text>
			</Space>
		),
	},
	{
		title: "接入分类",
		dataIndex: "sourceCategory",
		key: "sourceCategory",
		width: 110,
		render: (value) => value || "—",
	},
	{ title: "连接器", dataIndex: "connectorType", key: "connectorType", width: 120, render: (value) => value || "—" },
	{ title: "模板版本", dataIndex: "version", key: "version", width: 100, render: (value) => value || "—" },
	{
		title: "模板默认值（非生效策略）",
		dataIndex: "defaults",
		key: "defaults",
		width: 360,
		render: (value?: Record<string, unknown>) => <ConfigurationPreview value={value} />,
	},
	{
		title: "必填参数",
		dataIndex: "requiredParams",
		key: "requiredParams",
		width: 180,
		render: (value?: string[]) => value?.join("、") || "—",
	},
	{
		title: "提示",
		dataIndex: "warnings",
		key: "warnings",
		width: 220,
		render: (value?: string[]) => value?.join("；") || "—",
	},
];

const capabilityColumns: ColumnsType<IngestionConnectorCapabilityDTO> = [
	{
		title: "连接器类型",
		dataIndex: "connectorType",
		key: "connectorType",
		width: 180,
		render: (value: string) => <Text strong>{value || "—"}</Text>,
	},
	{
		title: "版本",
		dataIndex: "connectorVersion",
		key: "connectorVersion",
		width: 110,
		render: (value) => value || "—",
	},
	{
		title: "状态",
		dataIndex: "enabled",
		key: "enabled",
		width: 100,
		render: (value?: boolean) => (
			<ReadinessTag ready={isExplicitlyEnabled(value)} readyText="可用" blockedText="停用" />
		),
	},
	{
		title: "能力",
		dataIndex: "capabilities",
		key: "capabilities",
		width: 300,
		render: (values?: string[]) =>
			values?.length ? values.map((value) => <Tag key={value}>{value}</Tag>) : <Text type="secondary">未声明</Text>,
	},
	{
		title: "约束",
		dataIndex: "constraints",
		key: "constraints",
		render: (value?: Record<string, unknown>) => <ConfigurationPreview value={value} />,
	},
];

const authProviderColumns: ColumnsType<ApiAuthProviderDescriptorDTO> = [
	{ title: "认证方式", dataIndex: "label", key: "label", width: 180, render: (value, row) => value || row.id },
	{ title: "标识", dataIndex: "id", key: "id", width: 160 },
	{ title: "说明", dataIndex: "description", key: "description", render: (value) => value || "—" },
	{
		title: "参数",
		key: "fields",
		width: 180,
		render: (_, row) =>
			`${row.fields?.length || 0} 项，其中敏感参数 ${row.fields?.filter((field) => field.sensitive).length || 0} 项`,
	},
	{
		title: "状态",
		dataIndex: "enabled",
		key: "enabled",
		width: 100,
		render: (value?: boolean) => (
			<ReadinessTag ready={isExplicitlyEnabled(value)} readyText="可用" blockedText="停用" />
		),
	},
];

export default function AccessDefaultsPage() {
	const [snapshot, setSnapshot] = useState<AccessDefaultsSnapshot | null>(null);
	const [loading, setLoading] = useState(false);
	const [loadFailed, setLoadFailed] = useState(false);
	const loadRequestIdRef = useRef(0);

	const load = useCallback(async () => {
		const requestId = ++loadRequestIdRef.current;
		setLoading(true);
		setLoadFailed(false);
		setSnapshot(null);
		try {
			const nextSnapshot = await loadAccessDefaultsSnapshot();
			if (loadRequestIdRef.current === requestId) setSnapshot(nextSnapshot);
		} catch {
			if (loadRequestIdRef.current === requestId) setLoadFailed(true);
		} finally {
			if (loadRequestIdRef.current === requestId) setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
		return () => {
			loadRequestIdRef.current += 1;
		};
	}, [load]);

	const destinationReady = Boolean(
		snapshot?.destination.available && snapshot.destination.writerTypeReady && snapshot.destination.writerConfigReady,
	);

	return (
		<div className={styles.page} data-testid="platform-access-defaults-page">
			<PageHeader
				title="默认配置"
				actions={
					<Button icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>
						刷新
					</Button>
				}
			/>
			<p className={styles.pageDescription}>查看数据接入当前生效的版本化策略、运行目标、模板与连接器能力。</p>

			<Alert
				className={styles.snapshotNotice}
				type="info"
				showIcon
				message="默认策略集中管理，任务保存时固化有效配置"
				description="此处展示当前激活策略；每个任务仍可覆盖允许的参数，实际运行使用任务 Revision 中固化的有效配置与校验值。"
			/>

			{loading && !snapshot ? (
				<div className={styles.section}>
					<Spin tip="正在读取配置快照…" />
				</div>
			) : loadFailed || !snapshot ? (
				<div className={styles.errorPanel}>
					<Alert
						type="error"
						showIcon
						message="配置快照加载失败"
						description="为避免展示不完整或过期配置，本页已停止呈现部分结果。请检查接入服务后重试。"
						action={<Button onClick={() => void load()}>重试</Button>}
					/>
				</div>
			) : (
				<>
					<section className={styles.section}>
						<div className={styles.sectionHeader}>
							<div>
								<h2 className={styles.sectionTitle}>当前默认策略</h2>
								<p className={styles.sectionDescription}>统一默认值按版本发布；任务运行不会动态漂移到新版本。</p>
							</div>
							<Tag color={snapshot.policy.status === "ACTIVE" ? "success" : "warning"}>
								{snapshot.policy.status}
							</Tag>
						</div>
						<Descriptions bordered size="small" column={{ xs: 1, md: 2, xl: 4 }}>
							<Descriptions.Item label="策略标识">{snapshot.policy.policyKey}</Descriptions.Item>
							<Descriptions.Item label="版本">v{snapshot.policy.version}</Descriptions.Item>
							<Descriptions.Item label="激活时间">
								{snapshot.policy.activatedAt || "未记录"}
							</Descriptions.Item>
							<Descriptions.Item label="校验值">{snapshot.policy.checksum.slice(0, 12)}</Descriptions.Item>
							<Descriptions.Item label="默认参数" span={4}>
								<ConfigurationPreview value={snapshot.policy.defaults} />
							</Descriptions.Item>
						</Descriptions>
					</section>

					<section className={styles.section}>
						<div className={styles.sectionHeader}>
							<div>
								<h2 className={styles.sectionTitle}>默认数据湖</h2>
								<p className={styles.sectionDescription}>任务未单独指定目标时使用的当前运行目标。</p>
							</div>
							<ReadinessTag ready={destinationReady} readyText="可运行" blockedText="需配置" />
						</div>
						<Descriptions bordered size="small" column={{ xs: 1, md: 2, xl: 4 }}>
							<Descriptions.Item label="目标名称">{snapshot.destination.destinationName || "—"}</Descriptions.Item>
							<Descriptions.Item label="Writer 类型">{snapshot.destination.writerType || "—"}</Descriptions.Item>
							<Descriptions.Item label="Writer 可用">
								<ReadinessTag ready={snapshot.destination.writerTypeReady} />
							</Descriptions.Item>
							<Descriptions.Item label="目标配置">
								<ReadinessTag ready={snapshot.destination.writerConfigReady} />
							</Descriptions.Item>
							<Descriptions.Item label="数据源 ID" span={2}>
								{snapshot.destination.dataSourceId || "—"}
							</Descriptions.Item>
							<Descriptions.Item label="运行提示" span={2}>
								{snapshot.destination.message || "—"}
							</Descriptions.Item>
						</Descriptions>
					</section>

					<section className={styles.section}>
						<div className={styles.sectionHeader}>
							<div>
								<h2 className={styles.sectionTitle}>任务模板默认值</h2>
								<p className={styles.sectionDescription}>模板仅提供创建建议；任务保存后的覆盖参数以任务详情为准。</p>
							</div>
							<Tag>{snapshot.templates.length} 个模板</Tag>
						</div>
						<Table<IngestionTaskTemplateDTO>
							rowKey="id"
							columns={templateColumns}
							dataSource={snapshot.templates}
							scroll={{ x: 1280 }}
							pagination={{ pageSize: 10, showSizeChanger: true }}
							locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无任务模板" /> }}
						/>
					</section>

					<section className={styles.section}>
						<div className={styles.sectionHeader}>
							<div>
								<h2 className={styles.sectionTitle}>连接器能力</h2>
								<p className={styles.sectionDescription}>展示当前运行时声明的能力和约束，不代表外部数据源连接健康。</p>
							</div>
							<Tag>{snapshot.capabilities.length} 个连接器</Tag>
						</div>
						<Table<IngestionConnectorCapabilityDTO>
							rowKey="connectorType"
							columns={capabilityColumns}
							dataSource={snapshot.capabilities}
							scroll={{ x: 980 }}
							pagination={{ pageSize: 10, showSizeChanger: true }}
							locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无连接器能力声明" /> }}
						/>
					</section>

					<section className={styles.section}>
						<div className={styles.sectionHeader}>
							<div>
								<h2 className={styles.sectionTitle}>API 接入契约</h2>
								<p className={styles.sectionDescription}>只展示协议与认证字段定义，不读取或呈现任何凭据值。</p>
							</div>
							<ReadinessTag ready={Boolean(snapshot.apiContract)} readyText="已接通" blockedText="未接通" />
						</div>
						{snapshot.apiContract ? (
							<>
								<Descriptions className={styles.apiSummary} bordered size="small" column={{ xs: 1, md: 2, xl: 4 }}>
									<Descriptions.Item label="契约版本">{snapshot.apiContract.contractVersion || "—"}</Descriptions.Item>
									<Descriptions.Item label="连接器">{snapshot.apiContract.connectorType || "—"}</Descriptions.Item>
									<Descriptions.Item label="默认 Reader">
										{snapshot.apiContract.defaultReaderType || "—"}
									</Descriptions.Item>
									<Descriptions.Item label="同步模式">
										{snapshot.apiContract.syncModes?.join("、") || "—"}
									</Descriptions.Item>
									<Descriptions.Item label="源类型" span={4}>
										{snapshot.apiContract.sourceTypes?.join("、") || "—"}
									</Descriptions.Item>
								</Descriptions>
								<Table<ApiAuthProviderDescriptorDTO>
									rowKey="id"
									columns={authProviderColumns}
									dataSource={snapshot.apiContract.authProviders || []}
									pagination={false}
									locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无认证方式声明" /> }}
								/>
							</>
						) : (
							<Alert type="warning" showIcon message="API 接入契约暂不可用" description="未展示任何推断或兜底配置。" />
						)}
					</section>
				</>
			)}
		</div>
	);
}
