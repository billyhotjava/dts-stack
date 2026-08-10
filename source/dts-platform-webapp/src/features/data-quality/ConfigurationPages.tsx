import { AppstoreOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Descriptions, Form, Input, Row, Select, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	getQualityScore,
	listQualityRules,
	listQualityTemplates,
	previewTemplateSQL,
	type QualityScoreResult,
} from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import { filterDatasetsByDomain, UNASSIGNED_DOMAIN_ID } from "./datasetDomains";
import {
	ManagePermissionHint,
	QualityMetric,
	QualityPageHeading,
	QualityStatus,
	UnavailableCapability,
} from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import { displayName, hasEffectiveQualityScore, type QualityRule, type QualityTemplate, toList } from "./qualityTypes";
import { buildBatchTemplatePreviewTargets } from "./templateBindings";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";
import { useQualityMaintainerAccess } from "./useQualityAccess";

export function RulesByTablePage() {
	const navigate = useNavigate();
	const { datasets, loading, message, lakeName } = useDefaultLakeDatasets();
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [keyword, setKeyword] = useState("");
	const [domainId, setDomainId] = useState<string>();

	useEffect(() => {
		void listQualityRules()
			.then((response) => setRules(toList<QualityRule>(response)))
			.catch((error) => toast.error(error instanceof Error ? error.message : "规则加载失败"));
	}, []);

	const rows = useMemo(
		() =>
			filterDatasetsByDomain(datasets, domainId)
				.filter((dataset) => !keyword.trim() || dataset.name.toLowerCase().includes(keyword.trim().toLowerCase()))
				.map((dataset) => {
					const related = rules.filter((rule) => String(rule.datasetId) === dataset.id);
					return {
						...dataset,
						ruleCount: related.length,
						enabledCount: related.filter((rule) => rule.enabled).length,
					};
				}),
		[datasets, domainId, keyword, rules],
	);
	const domainOptions = useMemo(
		() =>
			Array.from(
				new Map(
					datasets.map((dataset) => [dataset.domainId || UNASSIGNED_DOMAIN_ID, dataset.domainName || "未归属业务域"]),
				).entries(),
			).map(([value, label]) => ({ value, label })),
		[datasets],
	);

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="按表配置"
				description={`按 ${lakeName} 中的数据资产查看规则覆盖与异常情况，避免跨来源误绑定。`}
				actions={
					<Button type="primary" icon={<AppstoreOutlined />} onClick={() => navigate(qualityPath("batch-wizard"))}>
						批量配置
					</Button>
				}
			/>
			{message ? <Alert showIcon type="warning" message={message} /> : null}
			<Card size="small">
				<Space wrap>
					<Input
						prefix={<SearchOutlined />}
						allowClear
						placeholder="搜索数据资产"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						style={{ width: 360 }}
					/>
					<Select
						allowClear
						placeholder="全部业务域"
						value={domainId}
						onChange={setDomainId}
						options={domainOptions}
						style={{ width: 220 }}
					/>
				</Space>
			</Card>
			<CompactTable
				rowKey="id"
				loading={loading}
				dataSource={rows}
				pagination={{ pageSize: 10 }}
				columns={[
					{
						title: "数据资产",
						dataIndex: "name",
						render: (value, row) => (
							<Button type="link" onClick={() => navigate(qualityPath("table-detail", { datasetId: row.id }))}>
								{value}
							</Button>
						),
					},
					{ title: "Schema", dataIndex: "schemaName", width: 180, render: (value) => displayName(value) },
					{ title: "业务域", dataIndex: "domainName", width: 160, render: (value) => displayName(value) },
					{ title: "规则数", dataIndex: "ruleCount", width: 100 },
					{
						title: "启用中",
						dataIndex: "enabledCount",
						width: 100,
						render: (value) => <Tag color="green">{value}</Tag>,
					},
				]}
			/>
		</div>
	);
}

export function TableQualityDetailPage() {
	const { datasetId = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const { datasets, loading: datasetsLoading } = useDefaultLakeDatasets();
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [score, setScore] = useState<QualityScoreResult>();
	const [loading, setLoading] = useState(true);
	const [loadError, setLoadError] = useState("");
	const loadSequence = useRef(0);
	const activeDatasetId = useRef(datasetId);
	const loadedDatasetId = useRef<string>();
	activeDatasetId.current = datasetId;

	const load = useCallback(async () => {
		const requestedDatasetId = datasetId;
		const sequence = ++loadSequence.current;
		loadedDatasetId.current = undefined;
		setRules([]);
		setScore(undefined);
		setLoading(true);
		setLoadError("");
		try {
			const [ruleResponse, scoreResponse] = await Promise.all([
				listQualityRules(),
				getQualityScore(requestedDatasetId, 30),
			]);
			if (sequence !== loadSequence.current || activeDatasetId.current !== requestedDatasetId) return;
			if (!scoreResponse) throw new Error("资产质量评分未返回有效数据");
			setRules(toList<QualityRule>(ruleResponse).filter((rule) => String(rule.datasetId) === requestedDatasetId));
			setScore(scoreResponse);
			loadedDatasetId.current = requestedDatasetId;
		} catch (error) {
			if (sequence !== loadSequence.current || activeDatasetId.current !== requestedDatasetId) return;
			const message = error instanceof Error ? error.message : "资产质量详情加载失败";
			setLoadError(message);
			toast.error(message);
		} finally {
			if (sequence === loadSequence.current && activeDatasetId.current === requestedDatasetId) setLoading(false);
		}
	}, [datasetId]);

	useEffect(() => {
		void load();
	}, [load]);

	const dataset = datasets.find((item) => item.id === datasetId);
	const loadedDatasetIsCurrent = loadedDatasetId.current === datasetId;
	const visibleRules = loadedDatasetIsCurrent ? rules : [];
	const visibleScore = loadedDatasetIsCurrent ? score : undefined;
	const detailLoading = loading || (!loadError && !loadedDatasetIsCurrent);
	const hasEffectiveRuns = hasEffectiveQualityScore(visibleScore);
	const columns: ColumnsType<QualityRule> = [
		{
			title: "规则",
			dataIndex: "name",
			render: (value, row) => (
				<Button type="link" onClick={() => navigate(qualityPath("rule-detail", { ruleId: row.id }))}>
					{displayName(value)}
				</Button>
			),
		},
		{ title: "类型", dataIndex: "type", width: 140, render: (value) => <Tag>{displayName(value)}</Tag> },
		{ title: "严重性", dataIndex: "severity", width: 110 },
		{ title: "状态", dataIndex: "enabled", width: 100, render: (value) => <QualityStatus status={Boolean(value)} /> },
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={dataset?.name || "表质量详情"}
				description="查看单个默认数据湖资产的质量得分、维度得分与规则覆盖。"
				actions={[
					<Button key="back" onClick={() => navigate(qualityPath("rule-by-table"))}>
						返回资产列表
					</Button>,
					<Button
						key="new"
						type="primary"
						icon={<PlusOutlined />}
						disabled={!canManage || !loadedDatasetIsCurrent || loading || Boolean(loadError)}
						onClick={() => navigate(`${qualityPath("rule-editor")}?datasetId=${encodeURIComponent(datasetId)}`)}
					>
						添加规则
					</Button>,
				]}
			/>
			<Card loading={datasetsLoading} size="small">
				<Descriptions column={{ xs: 1, md: 3 }} size="small">
					<Descriptions.Item label="数据资产 ID">{datasetId}</Descriptions.Item>
					<Descriptions.Item label="Schema">{displayName(dataset?.schemaName)}</Descriptions.Item>
					<Descriptions.Item label="物理表">{displayName(dataset?.tableName || dataset?.name)}</Descriptions.Item>
				</Descriptions>
			</Card>
			{!detailLoading && loadError ? (
				<Alert
					showIcon
					type="error"
					message="资产质量详情加载失败"
					description={loadError}
					action={<Button onClick={() => void load()}>重试</Button>}
				/>
			) : (
				<>
					{!detailLoading && visibleScore && !hasEffectiveRuns ? (
						<Alert showIcon type="info" message="暂无有效检测结果" />
					) : null}
					<div className="dq-metric-grid">
						<QualityMetric
							label="综合质量分"
							value={hasEffectiveRuns ? visibleScore?.overall : "-"}
							note={hasEffectiveRuns ? "近 30 天" : "暂无有效检测结果"}
						/>
						{(hasEffectiveRuns ? visibleScore?.dimensions || [] : []).slice(0, 3).map((item) => (
							<QualityMetric
								key={item.type}
								label={item.type}
								value={item.score}
								note={item.delta == null ? "暂无环比" : `环比 ${item.delta > 0 ? "+" : ""}${item.delta}`}
								color="#13c2c2"
							/>
						))}
					</div>
					<Card title="已绑定规则">
						<CompactTable
							rowKey="id"
							loading={detailLoading}
							columns={columns}
							dataSource={visibleRules}
							pagination={false}
						/>
					</Card>
				</>
			)}
		</div>
	);
}

export function RulesByTemplatePage() {
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const [templates, setTemplates] = useState<QualityTemplate[]>([]);
	const [loading, setLoading] = useState(true);

	useEffect(() => {
		void listQualityTemplates()
			.then((response) => setTemplates(toList<QualityTemplate>(response)))
			.catch((error) => toast.error(error instanceof Error ? error.message : "模板加载失败"))
			.finally(() => setLoading(false));
	}, []);

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="按模板配置"
				description="选择已登记模板，先验证模板 SQL，再进入单规则配置或批量配置预览。"
				actions={<ManagePermissionHint canManage={canManage} />}
			/>
			<Row gutter={[16, 16]}>
				{templates.map((template) => (
					<Col key={template.id} xs={24} md={12} xl={8}>
						<Card
							loading={loading}
							hoverable
							title={template.name || template.code}
							extra={<Tag color="blue">{displayName(template.category)}</Tag>}
						>
							<p className="dq-muted" style={{ minHeight: 44 }}>
								{displayName(template.description, "暂无模板说明")}
							</p>
							<Space>
								<Button onClick={() => navigate(qualityPath("template-detail", { templateId: template.id }))}>
									预览模板
								</Button>
								<Button
									type="primary"
									disabled={!canManage}
									onClick={() =>
										navigate(`${qualityPath("rule-editor")}?templateId=${encodeURIComponent(template.id)}`)
									}
								>
									单资产创建
								</Button>
								<Button
									onClick={() =>
										navigate(`${qualityPath("batch-wizard")}?templateId=${encodeURIComponent(template.id)}`)
									}
								>
									批量预览
								</Button>
							</Space>
						</Card>
					</Col>
				))}
			</Row>
		</div>
	);
}

type BatchPreviewForm = { templateId: string; datasetIds: string[]; params: string };
type BatchPreviewResult = { datasetId: string; label: string; sql: string };

export function BatchConfigurationPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const linkedTemplateId = searchParams.get("templateId") || undefined;
	const canManage = useQualityMaintainerAccess();
	const { datasets, message } = useDefaultLakeDatasets();
	const [templates, setTemplates] = useState<QualityTemplate[]>([]);
	const [previewResults, setPreviewResults] = useState<BatchPreviewResult[]>([]);
	const [previewing, setPreviewing] = useState(false);
	const [form] = Form.useForm<BatchPreviewForm>();

	useEffect(() => {
		void listQualityTemplates()
			.then((response) => {
				setTemplates(toList<QualityTemplate>(response));
				if (linkedTemplateId) form.setFieldValue("templateId", linkedTemplateId);
			})
			.catch((error) => toast.error(error instanceof Error ? error.message : "模板加载失败"));
	}, [form, linkedTemplateId]);

	const preview = async () => {
		try {
			const values = await form.validateFields();
			setPreviewing(true);
			const template = templates.find((item) => String(item.id) === values.templateId);
			if (!template) throw new Error("未找到所选质量规则模板");
			const params = JSON.parse(values.params || "{}") as Record<string, unknown>;
			const targets = buildBatchTemplatePreviewTargets(values.datasetIds, datasets, params, template.paramSchema);
			const results = await Promise.all(
				targets.map(async (target) => {
					const result = await previewTemplateSQL(values.templateId, target.params);
					const sql =
						typeof result === "string"
							? result
							: String((result as { sql?: string })?.sql || JSON.stringify(result, null, 2));
					if (!sql.trim()) throw new Error(`${target.label} 未生成可执行检测 SQL`);
					return { datasetId: target.datasetId, label: target.label, sql: sql.trim() };
				}),
			);
			setPreviewResults(results);
		} catch (error) {
			if ((error as { errorFields?: unknown })?.errorFields) return;
			toast.error(
				error instanceof SyntaxError
					? "模板参数必须是有效 JSON"
					: error instanceof Error
						? error.message
						: "模板预览失败",
			);
		} finally {
			setPreviewing(false);
		}
	};

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="批量配置规则"
				description="当前可验证模板与目标资产；批量原子创建和发布尚无后端合同。"
				actions={[
					<ManagePermissionHint key="permission" canManage={canManage} />,
					<Button key="back" onClick={() => navigate(qualityPath("rule-by-table"))}>
						返回按表配置
					</Button>,
				]}
			/>
			<UnavailableCapability capability="atomic-batch" title="批量提交暂未开放" />
			{message ? <Alert showIcon type="warning" message={message} /> : null}
			<Card title="批量配置预览">
				<Form
					form={form}
					layout="vertical"
					initialValues={{ params: "{}" }}
					style={{ maxWidth: 820 }}
					onValuesChange={() => setPreviewResults([])}
				>
					<Form.Item name="templateId" label="规则模板" rules={[{ required: true, message: "请选择模板" }]}>
						<Select options={templates.map((item) => ({ value: item.id, label: item.name || item.code || item.id }))} />
					</Form.Item>
					<Form.Item name="datasetIds" label="目标数据资产" rules={[{ required: true, message: "请选择目标资产" }]}>
						<Select
							mode="multiple"
							options={datasets.map((item) => ({ value: item.id, label: item.name }))}
							disabled={Boolean(message)}
						/>
					</Form.Item>
					<Form.Item name="params" label="模板参数（JSON）">
						<Input.TextArea rows={5} className="dq-code-block" />
					</Form.Item>
					<Space>
						<Button loading={previewing} disabled={!canManage} onClick={() => void preview()}>
							验证模板 SQL
						</Button>
						<UnavailableCapability capability="atomic-batch" compact />
					</Space>
				</Form>
				{previewResults.length ? (
					<Space direction="vertical" size={12} style={{ width: "100%", marginTop: 16 }}>
						{previewResults.map((result) => (
							<Card key={result.datasetId} size="small" title={`${result.label} · ${result.datasetId}`}>
								<pre className="dq-code-block">{result.sql}</pre>
							</Card>
						))}
					</Space>
				) : null}
			</Card>
		</div>
	);
}
