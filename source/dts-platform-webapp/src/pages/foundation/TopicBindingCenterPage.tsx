import { useEffect, useMemo, useState } from "react";
import {
	Alert,
	Button,
	Card,
	Descriptions,
	Modal,
	Select,
	Space,
	Statistic,
	Table,
	Tag,
	Typography,
	message,
} from "antd";
import { CheckCircleOutlined, LinkOutlined, ReloadOutlined } from "@ant-design/icons";
import { listDbtSources } from "@/api/platformApi";
import topicBindingService, {
	type TopicBindingDiagnostics,
	type TopicBindingRow,
	type TopicBindingTemplateView,
} from "@/api/services/topicBindingService";
import { EmptyState } from "@/components/empty-state";
import {
	buildTopicTemplateSummaries,
	selectPreferredTopicSource,
	type TopicSourceCandidate,
} from "./topicBindingCenter.helpers";

const { Paragraph, Text, Title } = Typography;

const normalizeText = (value?: string | null) => String(value || "").trim();

const buildTopicSourceKey = (candidate?: TopicSourceCandidate) =>
	candidate ? `${normalizeText(candidate.sourceDataSourceId)}|${normalizeText(candidate.schema)}|${normalizeText(candidate.table)}` : "";

export default function TopicBindingCenterPage() {
	const [templates, setTemplates] = useState<TopicBindingTemplateView[]>([]);
	const [diagnostics, setDiagnostics] = useState<TopicBindingDiagnostics | null>(null);
	const [sources, setSources] = useState<TopicSourceCandidate[]>([]);
	const [loading, setLoading] = useState(false);
	const [sourcesLoading, setSourcesLoading] = useState(false);
	const [bindingSubmitting, setBindingSubmitting] = useState(false);
	const [selectedTemplateCode, setSelectedTemplateCode] = useState<string>();
	const [bindingRow, setBindingRow] = useState<TopicBindingRow | null>(null);
	const [selectedDataSourceId, setSelectedDataSourceId] = useState<string>();
	const [selectedSourceKey, setSelectedSourceKey] = useState<string>();

	const loadTemplatesAndStatus = async () => {
		setLoading(true);
		try {
			const [templateResp, statusResp] = await Promise.all([topicBindingService.listTemplates(), topicBindingService.getStatus()]);
			const nextTemplates = Array.isArray(templateResp) ? templateResp : [];
			setTemplates(nextTemplates);
			setDiagnostics(statusResp || null);
			setSelectedTemplateCode((current) => current || nextTemplates[0]?.templateCode);
		} catch (error: any) {
			message.error(error?.message || "加载专题绑定中心失败");
			setTemplates([]);
			setDiagnostics(null);
		} finally {
			setLoading(false);
		}
	};

	const loadSources = async () => {
		setSourcesLoading(true);
		try {
			const resp = (await listDbtSources()) as TopicSourceCandidate[];
			setSources(Array.isArray(resp) ? resp : []);
		} catch (error: any) {
			message.error(error?.message || "加载 ODS 候选表失败");
			setSources([]);
		} finally {
			setSourcesLoading(false);
		}
	};

	useEffect(() => {
		void loadTemplatesAndStatus();
		void loadSources();
	}, []);

	const summaries = useMemo(
		() => buildTopicTemplateSummaries(templates, diagnostics),
		[templates, diagnostics],
	);

	const activeSummary = useMemo(() => {
		if (!summaries.length) {
			return undefined;
		}
		return summaries.find((item) => item.templateCode === selectedTemplateCode) || summaries[0];
	}, [selectedTemplateCode, summaries]);

	const uniqueDataSources = useMemo(() => {
		const seen = new Set<string>();
		return sources
			.filter((item) => normalizeText(item.sourceDataSourceId))
			.filter((item) => {
				const key = normalizeText(item.sourceDataSourceId);
				if (seen.has(key)) {
					return false;
				}
				seen.add(key);
				return true;
			})
			.map((item) => ({
				value: normalizeText(item.sourceDataSourceId),
				label: item.sourceDataSourceName || normalizeText(item.sourceDataSourceId),
			}));
	}, [sources]);

	const filteredSources = useMemo(() => {
		if (!normalizeText(selectedDataSourceId)) {
			return sources;
		}
		return sources.filter((item) => normalizeText(item.sourceDataSourceId) === normalizeText(selectedDataSourceId));
	}, [selectedDataSourceId, sources]);

	useEffect(() => {
		if (!bindingRow) {
			return;
		}
		const preferred = selectPreferredTopicSource(filteredSources, selectedDataSourceId);
		const nextKey = buildTopicSourceKey(preferred);
		setSelectedSourceKey((current) => (current && filteredSources.some((item) => buildTopicSourceKey(item) === current) ? current : nextKey));
	}, [bindingRow, filteredSources, selectedDataSourceId]);

	const openBindingModal = (row: TopicBindingRow) => {
		const preferred = selectPreferredTopicSource(sources);
		setBindingRow(row);
		setSelectedDataSourceId(normalizeText(preferred?.sourceDataSourceId) || undefined);
		setSelectedSourceKey(buildTopicSourceKey(preferred) || undefined);
	};

	const closeBindingModal = () => {
		setBindingRow(null);
		setSelectedDataSourceId(undefined);
		setSelectedSourceKey(undefined);
	};

	const submitBinding = async () => {
		if (!bindingRow) {
			return;
		}
		const candidate = filteredSources.find((item) => buildTopicSourceKey(item) === selectedSourceKey);
		if (!candidate?.table) {
			message.warning("请选择一个可用的 ODS 表");
			return;
		}
		setBindingSubmitting(true);
		try {
			await topicBindingService.bindOdsTable({
				templateCode: bindingRow.templateCode,
				entityCode: bindingRow.entityCode,
				dataSourceId: normalizeText(candidate.sourceDataSourceId) || undefined,
				schemaName: normalizeText(candidate.schema) || normalizeText(bindingRow.expectedSchema) || "ods",
				tableName: candidate.table,
				notes: "bind from topic center",
			});
			message.success("专题绑定已更新");
			closeBindingModal();
			await loadTemplatesAndStatus();
		} catch (error: any) {
			message.error(error?.message || "专题绑定失败");
		} finally {
			setBindingSubmitting(false);
		}
	};

	const columns = [
		{
			title: "逻辑实体",
			dataIndex: "entityName",
			key: "entityName",
			render: (_: unknown, row: TopicBindingRow) => (
				<div>
					<div className="font-medium">{row.entityName || row.entityCode}</div>
					<div className="text-xs text-slate-500">{row.entityCode}</div>
				</div>
			),
		},
		{
			title: "逻辑 Source",
			key: "source",
			render: (_: unknown, row: TopicBindingRow) => (
				<div className="text-xs">
					<div>{row.sourceName || "-"}</div>
					<div className="text-slate-500">{row.logicalTableName || "-"}</div>
				</div>
			),
		},
		{
			title: "当前绑定",
			key: "binding",
			render: (_: unknown, row: TopicBindingRow) =>
				row.bound ? (
					<div className="text-xs">
						<div className="font-medium">{`${row.boundSchemaName || "ods"}.${row.boundTableName || "-"}`}</div>
						<div className="text-slate-500">{row.bindingStatus || "ACTIVE"}</div>
					</div>
				) : (
					<Tag color="red">未绑定</Tag>
				),
		},
		{
			title: "要求",
			dataIndex: "required",
			key: "required",
			width: 90,
			render: (value: boolean) => (value ? <Tag color="gold">必填</Tag> : <Tag>可选</Tag>),
		},
		{
			title: "操作",
			key: "action",
			width: 130,
			render: (_: unknown, row: TopicBindingRow) => (
				<Button size="small" type="link" icon={<LinkOutlined />} onClick={() => openBindingModal(row)}>
					{row.bound ? "换绑" : "绑定"}
				</Button>
			),
		},
	];

	return (
		<div className="space-y-6">
			<Card
				title="专题绑定中心"
				extra={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={() => { void loadTemplatesAndStatus(); void loadSources(); }} loading={loading || sourcesLoading}>
							刷新
						</Button>
					</Space>
				}
			>
				<Space direction="vertical" size={12} style={{ width: "100%" }}>
					<div>
						<Title level={5} style={{ marginBottom: 8 }}>
							环境级专题绑定
						</Title>
						<Paragraph type="secondary" style={{ marginBottom: 0 }}>
							专题模板只依赖稳定逻辑实体。现场真实 ODS 表名在这里完成绑定，dbt 运行前由平台编译为动态 source 与 vars，
							避免模型写死现场物理表名。
						</Paragraph>
					</div>
					{diagnostics?.missingRequired?.length ? (
						<Alert
							type="warning"
							showIcon
							message={`当前仍缺少 ${diagnostics.missingRequired.length} 个必填绑定`}
							description={diagnostics.missingRequired.join("，")}
						/>
					) : (
						<Alert type="success" showIcon icon={<CheckCircleOutlined />} message="当前已无缺失的必填专题绑定" />
					)}
					<div className="grid gap-4 md:grid-cols-3">
						{summaries.map((item) => (
							<Card
								key={item.templateCode}
								size="small"
								hoverable
								onClick={() => setSelectedTemplateCode(item.templateCode)}
								className={item.templateCode === activeSummary?.templateCode ? "border-blue-500" : undefined}
							>
								<div className="font-medium">{item.templateName}</div>
								<div className="text-xs text-slate-500 mt-1">{item.templateCode}</div>
								<div className="grid grid-cols-3 gap-2 mt-3">
									<Statistic title="实体" value={item.rows.length} />
									<Statistic title="已绑" value={item.boundCount} />
									<Statistic title="缺失" value={item.missingRequiredCount} valueStyle={{ color: item.missingRequiredCount ? "#d4380d" : undefined }} />
								</div>
							</Card>
						))}
					</div>
				</Space>
			</Card>

			{activeSummary ? (
				<Card
					title={activeSummary.templateName}
					extra={
						<Select
							style={{ width: 240 }}
							value={activeSummary.templateCode}
							options={summaries.map((item) => ({ value: item.templateCode, label: item.templateName }))}
							onChange={setSelectedTemplateCode}
						/>
					}
				>
					<Space direction="vertical" size={12} style={{ width: "100%" }}>
						<Descriptions bordered size="small" column={3}>
							<Descriptions.Item label="模板编码">{activeSummary.templateCode}</Descriptions.Item>
							<Descriptions.Item label="绑定范围">{activeSummary.bindingScope || "GLOBAL"}</Descriptions.Item>
							<Descriptions.Item label="状态">{activeSummary.status || "ACTIVE"}</Descriptions.Item>
						</Descriptions>
						<Table
							rowKey={(row) => `${row.templateCode}.${row.entityCode}`}
							columns={columns as never}
							dataSource={activeSummary.rows}
							pagination={false}
							loading={loading}
							locale={{
								emptyText: <EmptyState title="暂无逻辑实体" description="当前专题模板还没有配置逻辑实体" compact />,
							}}
						/>
					</Space>
				</Card>
			) : (
				<Card>
					<EmptyState title="暂无专题模板" description="后端初始化完成后，专题模板会在这里显示" compact />
				</Card>
			)}

			<Modal
				open={!!bindingRow}
				title={`绑定逻辑实体：${bindingRow?.entityName || bindingRow?.entityCode || ""}`}
				onCancel={closeBindingModal}
				onOk={() => void submitBinding()}
				okText="保存绑定"
				confirmLoading={bindingSubmitting}
				width={720}
			>
				<Space direction="vertical" size={16} style={{ width: "100%" }}>
					<Descriptions bordered size="small" column={2}>
						<Descriptions.Item label="专题">{bindingRow?.templateName || bindingRow?.templateCode || "-"}</Descriptions.Item>
						<Descriptions.Item label="逻辑实体">{bindingRow?.entityCode || "-"}</Descriptions.Item>
						<Descriptions.Item label="逻辑 Source">{bindingRow?.sourceName || "-"}</Descriptions.Item>
						<Descriptions.Item label="逻辑表">{bindingRow?.logicalTableName || "-"}</Descriptions.Item>
					</Descriptions>
					<Select
						showSearch
						allowClear
						placeholder="选择数据源"
						value={selectedDataSourceId}
						options={uniqueDataSources}
						onChange={(value) => setSelectedDataSourceId(value)}
					/>
					<Select
						showSearch
						placeholder="选择 ODS 表"
						value={selectedSourceKey}
						loading={sourcesLoading}
						options={filteredSources.map((item) => ({
							value: buildTopicSourceKey(item),
							label: `${item.schema || "ods"}.${item.table || "-"}${item.sourceDataSourceName ? ` · ${item.sourceDataSourceName}` : ""}`,
						}))}
						onChange={(value) => setSelectedSourceKey(value)}
					/>
					<Text type="secondary">
						当前绑定会在 dbt 运行前被编译为运行时 source/vars。若现场重新接入了新的 ODS 表，只需要在这里换绑，不需要修改模型 SQL。
					</Text>
				</Space>
			</Modal>
		</div>
	);
}
