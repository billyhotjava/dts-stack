import {
	Alert,
	Button,
	Card,
	Checkbox,
	Col,
	Divider,
	Drawer,
	Empty,
	Input,
	InputNumber,
	Modal,
	Row,
	Select,
	Space,
	Spin,
	Table,
	Tag,
	Typography,
} from "antd";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { getPublishedQueryDataset, type AnalysisDatasetDetail } from "@/api/sql-workbench";
import {
	AnalysisApiError,
	cancelAnalysisQuery,
	createAnalysis,
	createAnalysisDraftFromVersion,
	getAnalysis,
	listAnalysisVersions,
	previewAnalysis,
	publishAnalysis,
	updateAnalysis,
	validateAnalysisPublication,
	type Analysis,
	type AnalysisFilterSelection,
	type AnalysisVersion,
	type PublicationAudience,
	type PublicationValidation,
	type AnalysisQuerySpec,
	type AnalysisQueryResult,
} from "../api/analysisApi";

const { Text, Title } = Typography;

type EditorState =
	| { status: "loading" }
	| { status: "ready"; contract: AnalysisDatasetDetail; analysis?: Analysis }
	| { status: "error"; error: unknown };

type SaveNotice = { kind: "success" | "error"; message: string; correlationId?: string } | null;
type QueryState =
	| { status: "idle" }
	| { status: "queued"; queryId: string }
	| { status: "running"; queryId: string }
	| { status: "success"; result: AnalysisQueryResult }
	| { status: "denied" | "timeout" | "cancelled" | "throttled" | "error"; message: string; queryId?: string };

function idempotencyKey(): string {
	return `analysis-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

function clientQueryId(): string {
	return `query-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`;
}

function errorMessage(error: unknown): string {
	if (error instanceof AnalysisApiError) {
		return `${error.message}${error.errorCode ? `（${error.errorCode}）` : ""}`;
	}
	return error instanceof Error ? error.message : "未知错误";
}

function metricCode(metric: Record<string, unknown>): string {
	return String(metric.code ?? "");
}

function metricLabel(metric: Record<string, unknown>): string {
	return String(metric.label ?? metric.code ?? "未命名度量");
}

function initialSpec(contract: AnalysisDatasetDetail): AnalysisQuerySpec {
	return {
		apiVersion: "dts.analysis/v1",
		dataset: {
			id: contract.dataset.datasetId,
			version: contract.dataset.version,
			contractVersion: contract.dataset.semanticContractVersion,
			checksum: contract.dataset.contractChecksum,
		},
		dimensions: [],
		metrics: [],
		derivedMetrics: [],
		filters: [],
		timeRange: null,
		orderBy: [],
		limit: 5000,
		visualization: { type: "table", settings: {} },
	};
}

export default function AnalysisEditorPage() {
	const { id } = useParams();
	const [searchParams] = useSearchParams();
	const navigate = useNavigate();
	const [reloadKey, setReloadKey] = useState(0);
	const [editor, setEditor] = useState<EditorState>({ status: "loading" });
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [spec, setSpec] = useState<AnalysisQuerySpec | null>(null);
	const [saving, setSaving] = useState(false);
	const [saveNotice, setSaveNotice] = useState<SaveNotice>(null);
	const [contractConflict, setContractConflict] = useState(false);
	const [queryState, setQueryState] = useState<QueryState>({ status: "idle" });
	const [publishOpen, setPublishOpen] = useState(false);
	const [publicationBusy, setPublicationBusy] = useState(false);
	const [publicationValidation, setPublicationValidation] = useState<PublicationValidation | null>(null);
	const [publicationError, setPublicationError] = useState<string | null>(null);
	const [audience, setAudience] = useState<PublicationAudience>({
		deptCodes: [],
		roleCodes: [],
		classification: "DATA_INTERNAL",
		expiresAt: null,
	});
	const [versionsOpen, setVersionsOpen] = useState(false);
	const [versions, setVersions] = useState<AnalysisVersion[]>([]);
	const [versionsLoading, setVersionsLoading] = useState(false);
	const queryAbortRef = useRef<AbortController | null>(null);

	useEffect(() => {
		let cancelled = false;
		setEditor({ status: "loading" });
		setSaveNotice(null);
		setContractConflict(false);
		setQueryState({ status: "idle" });
		const load = async () => {
			try {
				if (id) {
					const analysis = await getAnalysis(id);
					const contract = await getPublishedQueryDataset(
						analysis.querySpec.dataset.id,
						analysis.querySpec.dataset.version,
					);
					if (cancelled) return;
					setName(analysis.name);
					setDescription(analysis.description ?? "");
					setSpec(analysis.querySpec);
					setEditor({ status: "ready", contract, analysis });
					return;
				}

				const datasetId = searchParams.get("datasetId")?.trim();
				const version = Number(searchParams.get("version"));
				const checksum = searchParams.get("checksum")?.trim();
				if (!datasetId || !Number.isInteger(version) || version < 1 || !checksum) {
					throw new Error("缺少已发布数据集的 datasetId、version 或 checksum，请从数据集目录发起分析。");
				}
				const contract = await getPublishedQueryDataset(datasetId, version);
				if (contract.dataset.contractChecksum !== checksum) {
					throw new AnalysisApiError(409, "数据集契约校验值已变化", { errorCode: "ANALYSIS_CONTRACT_CHECKSUM_CONFLICT" });
				}
				if (cancelled) return;
				setName(`${contract.dataset.name} 分析`);
				setDescription("");
				setSpec(initialSpec(contract));
				setEditor({ status: "ready", contract });
			} catch (error) {
				if (cancelled) return;
				if (error instanceof AnalysisApiError && error.status === 409) setContractConflict(true);
				setEditor({ status: "error", error });
			}
		};
		void load();
		return () => {
			cancelled = true;
		};
	}, [id, reloadKey, searchParams]);

	const contract = editor.status === "ready" ? editor.contract : null;
	const analysis = editor.status === "ready" ? editor.analysis : undefined;
	const dimensions = contract?.dimensions ?? [];
	const metrics = contract?.metrics ?? [];
	const selectedFields = useMemo(
		() => [
			...(spec?.dimensions.map((item) => item.field) ?? []),
			...(spec?.metrics.map((item) => item.code) ?? []),
			...(spec?.derivedMetrics.map((item) => item.code) ?? []),
		],
		[spec],
	);

	const updateSpec = useCallback((mutate: (current: AnalysisQuerySpec) => AnalysisQuerySpec) => {
		setSpec((current) => (current ? mutate(current) : current));
		setSaveNotice(null);
	}, []);

	const toggleDimension = (field: string, checked: boolean) => {
		updateSpec((current) => ({
			...current,
			dimensions: checked
				? [...current.dimensions, { field, alias: null }]
				: current.dimensions.filter((item) => item.field !== field),
			orderBy: checked ? current.orderBy : current.orderBy.filter((item) => item.field !== field),
		}));
	};

	const toggleMetric = (code: string, checked: boolean) => {
		updateSpec((current) => ({
			...current,
			metrics: checked
				? [...current.metrics, { code, alias: null }]
				: current.metrics.filter((item) => item.code !== code),
			orderBy: checked ? current.orderBy : current.orderBy.filter((item) => item.field !== code),
		}));
	};

	const updateFilter = (index: number, patch: Partial<AnalysisFilterSelection>) => {
		updateSpec((current) => ({
			...current,
			filters: current.filters.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
		}));
	};

	const save = async () => {
		if (!spec || !name.trim()) {
			setSaveNotice({ kind: "error", message: "请填写分析名称。" });
			return;
		}
		if (selectedFields.length === 0) {
			setSaveNotice({ kind: "error", message: "请至少选择一个维度或度量。" });
			return;
		}
		setSaving(true);
		setSaveNotice(null);
		setContractConflict(false);
		try {
			const saved = analysis
				? await updateAnalysis(analysis.id, {
						name: name.trim(),
						description: description.trim() || null,
						querySpec: spec,
						versionNo: analysis.versionNo,
					})
				: await createAnalysis(
						{
							name: name.trim(),
							description: description.trim() || null,
							querySpec: spec,
						},
						idempotencyKey(),
					);
			setEditor((current) => (current.status === "ready" ? { ...current, analysis: saved } : current));
			setSpec(saved.querySpec);
			setSaveNotice({ kind: "success", message: "分析草稿已保存" });
			if (!analysis) navigate(`/bi/questions/${encodeURIComponent(String(saved.id))}/edit`, { replace: true });
		} catch (error) {
			const conflict = error instanceof AnalysisApiError && error.status === 409;
			setContractConflict(conflict);
			setSaveNotice({
				kind: "error",
				message: conflict ? `契约已变化：${errorMessage(error)}` : errorMessage(error),
				correlationId: error instanceof AnalysisApiError ? error.correlationId : undefined,
			});
		} finally {
			setSaving(false);
		}
	};

	const createNewDraft = () => {
		const dataset = spec?.dataset;
		if (!dataset) return;
		navigate(
			`/bi/questions/new?datasetId=${encodeURIComponent(dataset.id)}&version=${dataset.version}&checksum=${encodeURIComponent(dataset.checksum)}`,
		);
	};

	const executeQuery = async () => {
		if (!spec || selectedFields.length === 0) {
			setQueryState({ status: "error", message: "请至少选择一个维度或度量后再执行。" });
			return;
		}
		const queryId = clientQueryId();
		const controller = new AbortController();
		queryAbortRef.current = controller;
		setQueryState({ status: "queued", queryId });
		await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
		setQueryState({ status: "running", queryId });
		try {
			const result = await previewAnalysis(spec, queryId, controller.signal);
			setQueryState({ status: "success", result });
		} catch (error) {
			if (controller.signal.aborted) {
				setQueryState({ status: "cancelled", message: "已取消", queryId });
			} else if (error instanceof AnalysisApiError && error.status === 403) {
				setQueryState({ status: "denied", message: `无权访问：${errorMessage(error)}`, queryId });
			} else if (error instanceof AnalysisApiError && error.status === 504) {
				setQueryState({ status: "timeout", message: `查询超时：${errorMessage(error)}`, queryId });
			} else if (error instanceof AnalysisApiError && error.status === 429) {
				setQueryState({ status: "throttled", message: `查询繁忙：${errorMessage(error)}`, queryId });
			} else if (error instanceof AnalysisApiError && error.errorCode === "ANALYSIS_QUERY_CANCELLED") {
				setQueryState({ status: "cancelled", message: "已取消", queryId });
			} else {
				setQueryState({ status: "error", message: errorMessage(error), queryId });
			}
		} finally {
			if (queryAbortRef.current === controller) queryAbortRef.current = null;
		}
	};

	const cancelQuery = () => {
		if (queryState.status !== "queued" && queryState.status !== "running") return;
		const queryId = queryState.queryId;
		const controller = queryAbortRef.current;
		void cancelAnalysisQuery(queryId).finally(() => controller?.abort());
		setQueryState({ status: "cancelled", message: "已取消", queryId });
	};

	const validatePublication = async (): Promise<PublicationValidation | null> => {
		if (!analysis) {
			setPublicationError("请先保存草稿，再进行发布校验。");
			return null;
		}
		setPublicationBusy(true);
		setPublicationError(null);
		try {
			const expiresAt = audience.expiresAt ? new Date(audience.expiresAt).toISOString() : null;
			const result = await validateAnalysisPublication(analysis.id, { ...audience, expiresAt });
			setPublicationValidation(result);
			return result;
		} catch (error) {
			setPublicationError(errorMessage(error));
			return null;
		} finally {
			setPublicationBusy(false);
		}
	};

	const openPublication = () => {
		setPublishOpen(true);
		setPublicationValidation(null);
		setPublicationError(null);
		if (analysis) void validatePublication();
	};

	const publish = async () => {
		if (!analysis) return;
		const checked = await validatePublication();
		if (!checked?.valid) return;
		setPublicationBusy(true);
		try {
			const expiresAt = audience.expiresAt ? new Date(audience.expiresAt).toISOString() : null;
			const result = await publishAnalysis(analysis.id, { ...audience, expiresAt });
			setSaveNotice({ kind: "success", message: `分析已发布为 v${result.versionNo}` });
			setPublishOpen(false);
			setReloadKey((value) => value + 1);
		} catch (error) {
			setPublicationError(errorMessage(error));
		} finally {
			setPublicationBusy(false);
		}
	};

	const openVersions = async () => {
		if (!analysis) return;
		setVersionsOpen(true);
		setVersionsLoading(true);
		try {
			setVersions(await listAnalysisVersions(analysis.id));
		} catch (error) {
			setSaveNotice({ kind: "error", message: errorMessage(error) });
		} finally {
			setVersionsLoading(false);
		}
	};

	const createDraftFromVersion = async (revisionId: number) => {
		if (!analysis) return;
		setVersionsLoading(true);
		try {
			const draft = await createAnalysisDraftFromVersion(analysis.id, revisionId);
			navigate(`/bi/questions/${encodeURIComponent(String(draft.id))}/edit`);
		} catch (error) {
			setSaveNotice({ kind: "error", message: errorMessage(error) });
		} finally {
			setVersionsLoading(false);
		}
	};

	if (editor.status === "loading") {
		return (
			<div style={{ minHeight: 420, display: "grid", placeItems: "center" }}>
				<Space direction="vertical" align="center">
					<Spin size="large" />
					<Text type="secondary">正在加载分析契约</Text>
				</Space>
			</div>
		);
	}

	if (editor.status === "error") {
		return (
			<Card>
				<Alert
					type="error"
					showIcon
					message={contractConflict ? "契约已变化" : "分析加载失败"}
					description={errorMessage(editor.error)}
					action={<Button onClick={() => setReloadKey((value) => value + 1)}>重新加载</Button>}
				/>
			</Card>
		);
	}

	if (!spec || !contract) return null;
	const canWrite = (analysis?.lifecycleStatus ?? "DRAFT") === "DRAFT" && (analysis?.permissions.write ?? true);
	const fieldOptions = dimensions.map((field) => ({ label: field.label || field.code, value: field.code }));
	const orderOptions = selectedFields.map((field) => ({ label: field, value: field }));
	const queryResult = queryState.status === "success" ? queryState.result : null;
	const resultColumnNames = (queryResult?.columns ?? []).map((column, index) =>
		String(column.name ?? column.display_name ?? column.label ?? selectedFields[index] ?? `column_${index + 1}`),
	);
	const resultColumns = resultColumnNames.map((column) => ({ title: column, dataIndex: column, key: column }));
	const resultRows = (queryResult?.rows ?? []).map((row, rowIndex) => {
		const record: Record<string, unknown> = { __key: rowIndex };
		resultColumnNames.forEach((column, columnIndex) => {
			record[column] = row[columnIndex];
		});
		return record;
	});
	const queryStatusLabel = (() => {
		switch (queryState.status) {
			case "queued": return "排队中";
			case "running": return "执行中";
			case "success": return queryState.result.truncated ? "结果已截断" : "执行成功";
			case "denied": return "无权访问";
			case "timeout": return "查询超时";
			case "cancelled": return "已取消";
			case "throttled": return "查询繁忙";
			case "error": return "执行失败";
			default: return "等待执行";
		}
	})();

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 12, minHeight: "calc(100vh - 132px)" }}>
			<Card size="small">
				<Row gutter={[16, 12]} align="middle">
					<Col flex="auto">
						<Space direction="vertical" size={2} style={{ width: "100%" }}>
							<Space wrap>
								<Title level={4} style={{ margin: 0 }}>治理分析编辑器</Title>
								<Tag color="blue">{contract.dataset.warehouseLayer}</Tag>
								<Tag>{analysis?.lifecycleStatus ?? "DRAFT"}</Tag>
							</Space>
							<Text type="secondary">
								{contract.dataset.name} · v{contract.dataset.version} · {contract.dataset.semanticContractVersion}
							</Text>
						</Space>
					</Col>
					<Col>
						<Space>
							<Button onClick={() => navigate("/bi/data")}>返回数据集</Button>
							{analysis && <Button onClick={() => void openVersions()}>版本历史</Button>}
							{analysis?.lifecycleStatus === "DRAFT" && (
								<Button disabled={!analysis.permissions.publish} onClick={openPublication}>校验</Button>
							)}
							{analysis?.lifecycleStatus === "DRAFT" && (
								<Button type="primary" ghost disabled={!analysis.permissions.publish} onClick={openPublication}>发布</Button>
							)}
							<Button type="primary" loading={saving} disabled={!canWrite} onClick={() => void save()}>
								保存草稿
							</Button>
						</Space>
					</Col>
				</Row>
			</Card>

			{!canWrite && <Alert type="info" showIcon message="该分析已发布，只能查看；如需调整请创建新草稿。" />}
			{saveNotice && (
				<Alert
					type={saveNotice.kind}
					showIcon
					message={saveNotice.message}
					description={saveNotice.correlationId ? `请求号：${saveNotice.correlationId}` : undefined}
					action={contractConflict ? <Button onClick={createNewDraft}>基于固定版本新建草稿</Button> : undefined}
				/>
			)}

			<Row gutter={12} style={{ flex: 1, minHeight: 0 }}>
				<Col xs={24} xl={6} style={{ display: "flex" }}>
					<Card title="字段与度量" size="small" style={{ width: "100%", overflow: "auto" }}>
						{dimensions.length === 0 && metrics.length === 0 ? (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可分析字段" />
						) : (
							<Space direction="vertical" size={8} style={{ width: "100%" }}>
								<Text strong>维度</Text>
								{dimensions.map((field) => (
									<Checkbox
										key={field.code}
										checked={spec.dimensions.some((item) => item.field === field.code)}
										disabled={!canWrite}
										onChange={(event) => toggleDimension(field.code, event.target.checked)}
									>
										{field.label || field.code} <Text type="secondary">({field.code})</Text>
									</Checkbox>
								))}
								<Divider style={{ margin: "8px 0" }} />
								<Text strong>度量</Text>
								{metrics.map((metric) => (
									<Checkbox
										key={metricCode(metric)}
										checked={spec.metrics.some((item) => item.code === metricCode(metric))}
										disabled={!canWrite}
										onChange={(event) => toggleMetric(metricCode(metric), event.target.checked)}
									>
										{metricLabel(metric)} <Text type="secondary">({metricCode(metric)})</Text>
									</Checkbox>
								))}
							</Space>
						)}
					</Card>
				</Col>

				<Col xs={24} xl={11} style={{ display: "flex" }}>
					<Card
						title="结果预览"
						size="small"
						style={{ width: "100%" }}
						extra={
							<Space>
								<Button
									type="primary"
									disabled={queryState.status === "queued" || queryState.status === "running"}
									onClick={() => void executeQuery()}
								>
									执行查询
								</Button>
								{(queryState.status === "queued" || queryState.status === "running") && (
									<Button danger onClick={cancelQuery}>取消查询</Button>
								)}
							</Space>
						}
					>
						{selectedFields.length === 0 ? (
							<Empty description="选择字段后可执行受治理查询" />
						) : (
							<Space direction="vertical" size={16} style={{ width: "100%" }}>
								{queryState.status === "idle" && (
									<Alert
										type="info"
										showIcon
										message="查询尚未执行"
										description="草稿只保存结构化分析定义；执行时将统一应用权限、行级策略和字段脱敏。"
									/>
								)}
								{["denied", "timeout", "cancelled", "throttled", "error"].includes(queryState.status) && (
									<Alert
										type={queryState.status === "cancelled" ? "warning" : "error"}
										showIcon
										message={queryStatusLabel}
										description={"message" in queryState ? queryState.message : undefined}
									/>
								)}
								<Card size="small" title="查询状态">
									<Space wrap size="large">
										<Text>状态：{queryStatusLabel}</Text>
										<Text>行数：{queryResult?.rowCount ?? "—"}</Text>
										<Text>耗时：{queryResult ? `${queryResult.durationMs} ms` : "—"}</Text>
										<Text>缓存：{queryResult ? (queryResult.cacheHit ? "命中" : "未命中") : "—"}</Text>
									</Space>
								</Card>
								{queryResult && (
									<Table
										size="small"
										rowKey="__key"
										columns={resultColumns}
										dataSource={resultRows}
										pagination={{ pageSize: 20, showSizeChanger: false }}
										scroll={{ x: "max-content", y: 420 }}
									/>
								)}
								<Space wrap>
									{selectedFields.map((field) => <Tag key={field}>{field}</Tag>)}
								</Space>
							</Space>
						)}
					</Card>
				</Col>

				<Col xs={24} xl={7} style={{ display: "flex" }}>
					<Card title="分析配置" size="small" style={{ width: "100%", overflow: "auto" }}>
						<Space direction="vertical" size={12} style={{ width: "100%" }}>
							<div>
								<Text strong>名称</Text>
								<Input value={name} maxLength={255} disabled={!canWrite} onChange={(event) => setName(event.target.value)} />
							</div>
							<div>
								<Text strong>说明</Text>
								<Input.TextArea value={description} rows={2} disabled={!canWrite} onChange={(event) => setDescription(event.target.value)} />
							</div>
							<div>
								<Text strong>展示方式</Text>
								<Select
									style={{ width: "100%" }}
									value={spec.visualization.type}
									disabled={!canWrite}
									options={["table", "bar", "line", "area", "pie", "number", "scatter"].map((value) => ({ label: value, value }))}
									onChange={(value) => updateSpec((current) => ({ ...current, visualization: { ...current.visualization, type: value } }))}
								/>
							</div>
							<div>
								<Text strong>最大返回行数</Text>
								<InputNumber
									style={{ width: "100%" }}
									min={1}
									max={10000}
									value={spec.limit}
									disabled={!canWrite}
									onChange={(value) => updateSpec((current) => ({ ...current, limit: Number(value ?? 5000) }))}
								/>
							</div>

							<Divider style={{ margin: "4px 0" }}>筛选</Divider>
							{spec.filters.map((filter, index) => (
								<Space.Compact key={`${filter.field}-${index}`} block>
									<Select
										style={{ width: "34%" }}
										value={filter.field}
										options={fieldOptions}
										disabled={!canWrite}
										onChange={(field) => updateFilter(index, { field })}
									/>
									<Select
										style={{ width: "26%" }}
										value={filter.op}
										disabled={!canWrite}
										options={["EQ", "NE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"].map((value) => ({ label: value, value }))}
										onChange={(op) => updateFilter(index, { op })}
									/>
									<Input
										style={{ width: "40%" }}
										value={filter.values.join(",")}
										disabled={!canWrite || filter.op === "IS_NULL" || filter.op === "IS_NOT_NULL"}
										onChange={(event) => updateFilter(index, { values: event.target.value.split(",").map((value) => value.trim()).filter(Boolean) })}
									/>
									<Button disabled={!canWrite} onClick={() => updateSpec((current) => ({ ...current, filters: current.filters.filter((_, itemIndex) => itemIndex !== index) }))}>删除</Button>
								</Space.Compact>
							))}
							<Button
								block
								disabled={!canWrite || dimensions.length === 0 || spec.filters.length >= 50}
								onClick={() => updateSpec((current) => ({ ...current, filters: [...current.filters, { field: dimensions[0]?.code ?? "", op: "EQ", values: [] }] }))}
							>
								添加筛选条件
							</Button>

							<Divider style={{ margin: "4px 0" }}>排序</Divider>
							{spec.orderBy.map((order, index) => (
								<Space.Compact key={`${order.field}-${index}`} block>
									<Select
										style={{ width: "55%" }}
										value={order.field}
										options={orderOptions}
										disabled={!canWrite}
										onChange={(field) => updateSpec((current) => ({ ...current, orderBy: current.orderBy.map((item, itemIndex) => itemIndex === index ? { ...item, field } : item) }))}
									/>
									<Select
										style={{ width: "30%" }}
										value={order.direction}
										disabled={!canWrite}
										options={[{ label: "升序", value: "ASC" }, { label: "降序", value: "DESC" }]}
										onChange={(direction) => updateSpec((current) => ({ ...current, orderBy: current.orderBy.map((item, itemIndex) => itemIndex === index ? { ...item, direction } : item) }))}
									/>
									<Button disabled={!canWrite} onClick={() => updateSpec((current) => ({ ...current, orderBy: current.orderBy.filter((_, itemIndex) => itemIndex !== index) }))}>删除</Button>
								</Space.Compact>
							))}
							<Button
								block
								disabled={!canWrite || selectedFields.length === 0 || spec.orderBy.length >= 10}
								onClick={() => updateSpec((current) => ({ ...current, orderBy: [...current.orderBy, { field: selectedFields[0] ?? "", direction: "ASC" }] }))}
							>
								添加排序
							</Button>
						</Space>
					</Card>
				</Col>
			</Row>

			<Drawer
				title="发布分析"
				open={publishOpen}
				width={560}
				onClose={() => setPublishOpen(false)}
				extra={
					<Space>
						<Button loading={publicationBusy} onClick={() => void validatePublication()}>重新校验</Button>
						<Button
							type="primary"
							loading={publicationBusy}
							disabled={!publicationValidation?.valid}
							onClick={() => void publish()}
						>
							确认发布
						</Button>
					</Space>
				}
			>
				<Space direction="vertical" size={16} style={{ width: "100%" }}>
					<Alert
						type="info"
						showIcon
						message="发布将钉定当前数据集版本与契约校验值"
						description={`${contract.dataset.name} · v${contract.dataset.version} · ${contract.dataset.contractChecksum}`}
					/>
					<div>
						<Text strong>可见部门</Text>
						<Select
							mode="tags"
							value={audience.deptCodes}
							onChange={(deptCodes) => { setAudience((value) => ({ ...value, deptCodes })); setPublicationValidation(null); }}
							placeholder="输入部门编码后回车"
							style={{ width: "100%" }}
						/>
					</div>
					<div>
						<Text strong>可见角色</Text>
						<Select
							mode="tags"
							value={audience.roleCodes}
							onChange={(roleCodes) => { setAudience((value) => ({ ...value, roleCodes })); setPublicationValidation(null); }}
							placeholder="输入角色编码后回车"
							style={{ width: "100%" }}
						/>
					</div>
					<div>
						<Text strong>发布密级</Text>
						<Select
							value={audience.classification}
							onChange={(classification) => { setAudience((value) => ({ ...value, classification })); setPublicationValidation(null); }}
							options={[
								{ label: "公开", value: "DATA_PUBLIC" },
								{ label: "内部", value: "DATA_INTERNAL" },
								{ label: "保密", value: "DATA_CONFIDENTIAL" },
								{ label: "敏感", value: "DATA_SENSITIVE" },
								{ label: "秘密", value: "DATA_SECRET" },
							]}
							style={{ width: "100%" }}
						/>
					</div>
					<div>
						<Text strong>有效期（可选）</Text>
						<Input
							type="datetime-local"
							value={audience.expiresAt ?? ""}
							onChange={(event) => { setAudience((value) => ({ ...value, expiresAt: event.target.value || null })); setPublicationValidation(null); }}
						/>
					</div>
					{publicationError && <Alert type="error" showIcon message="发布校验失败" description={publicationError} />}
					{publicationValidation && (
						<>
							<Alert
								type={publicationValidation.valid ? "success" : "error"}
								showIcon
								message={publicationValidation.valid ? "校验通过，可以发布" : "存在发布阻断项"}
							/>
							{publicationValidation.blockers.map((blocker) => (
								<Alert key={`${blocker.code}-${blocker.path}`} type="error" showIcon message={blocker.code} description={`${blocker.path}：${blocker.message}`} />
							))}
							<Card size="small" title="依赖快照">
								<pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-all", maxHeight: 240, overflow: "auto" }}>
									{JSON.stringify(publicationValidation.dependencySnapshot, null, 2)}
								</pre>
							</Card>
						</>
					)}
				</Space>
			</Drawer>

			<Modal
				title="版本历史"
				open={versionsOpen}
				footer={null}
				width={720}
				onCancel={() => setVersionsOpen(false)}
			>
				<Spin spinning={versionsLoading}>
					<Space direction="vertical" size={8} style={{ width: "100%" }}>
						{versions.length === 0 && !versionsLoading ? <Empty description="暂无发布版本" /> : null}
						{versions.map((version) => (
							<Card
								key={version.revisionId}
								size="small"
								title={<Space><Text strong>v{version.versionNo}</Text><Tag>{version.status}</Tag></Space>}
								extra={<Button size="small" onClick={() => void createDraftFromVersion(version.revisionId)}>基于此版本创建草稿</Button>}
							>
								<Text type="secondary">
									{version.publishedAt ? new Date(version.publishedAt).toLocaleString() : new Date(version.createdAt).toLocaleString()}
									{" · "}{version.contractChecksum ?? "无校验值"}
								</Text>
							</Card>
						))}
					</Space>
				</Spin>
			</Modal>
		</div>
	);
}
