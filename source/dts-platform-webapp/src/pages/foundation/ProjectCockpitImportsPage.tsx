import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import {
	Alert,
	Button,
	Card,
	Descriptions,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Switch,
	Table,
	Tag,
	Typography,
	Upload,
	message,
} from "antd";
import { CloudUploadOutlined, EyeOutlined, InboxOutlined, PlayCircleOutlined, ReloadOutlined } from "@ant-design/icons";
import { listDbtSources } from "@/api/platformApi";
import dataSourcesService, {
	type ExcelImportErrorRow,
	type ExcelImportParseResponse,
	type ExcelImportPrepareResponse,
	type ProjectCockpitBatchIssuePreviewResponse,
	type ProjectCockpitBatchLoadResponse,
} from "@/api/services/dataSourcesService";
import topicBindingService, {
	type TopicBindingDiagnostics,
	type TopicBindingTemplateView,
} from "@/api/services/topicBindingService";
import {
	selectPreferredTopicSource,
	type TopicSourceCandidate,
} from "./topicBindingCenter.helpers";
import { selectRecommendedProjectCockpitBinding } from "./projectCockpitImportBinding.helpers";

type UploadRequestOption = Parameters<NonNullable<import("antd").UploadProps["customRequest"]>>[0];

const { Paragraph, Text, Title } = Typography;
const normalizeText = (value?: string | null) => String(value || "").trim();
const buildTopicSourceKey = (candidate?: TopicSourceCandidate) =>
	candidate ? `${normalizeText(candidate.sourceDataSourceId)}|${normalizeText(candidate.schema)}|${normalizeText(candidate.table)}` : "";
const resolveBatchStatusColor = (status?: string) => {
	if (!status) {
		return "default";
	}
	if (status.includes("REJECTION")) {
		return "error";
	}
	if (status.includes("WARNING")) {
		return "warning";
	}
	return "success";
};

const toPreviewRows = (result: ExcelImportParseResponse | null) => {
	if (!result?.preview?.length || !result.columns?.length) {
		return [];
	}
	return result.preview.map((row, index) => {
		const payload: Record<string, string> = { key: String(index + 1) };
		result.columns.forEach((column, columnIndex) => {
			payload[column.name] = row[columnIndex] ?? "";
		});
		return payload;
	});
};

export default function ProjectCockpitImportsPage() {
	const navigate = useNavigate();
	const [excelPrepared, setExcelPrepared] = useState<ExcelImportPrepareResponse | null>(null);
	const [excelParseResult, setExcelParseResult] = useState<ExcelImportParseResponse | null>(null);
	const [batchLoadResult, setBatchLoadResult] = useState<ProjectCockpitBatchLoadResponse | null>(null);
	const [batchIssuePreview, setBatchIssuePreview] = useState<ProjectCockpitBatchIssuePreviewResponse | null>(null);
	const [batchIssueSeverity, setBatchIssueSeverity] = useState<"ERROR" | "WARN">("ERROR");
	const [errorRows, setErrorRows] = useState<ExcelImportErrorRow[]>([]);
	const [excelUploading, setExcelUploading] = useState(false);
	const [excelParsing, setExcelParsing] = useState(false);
	const [loadingBatch, setLoadingBatch] = useState(false);
	const [loadingErrors, setLoadingErrors] = useState(false);
	const [loadingBatchIssues, setLoadingBatchIssues] = useState(false);
	const [excelSheetName, setExcelSheetName] = useState<string | undefined>();
	const [excelHeaderRow, setExcelHeaderRow] = useState(1);
	const [excelDataStartRow, setExcelDataStartRow] = useState(2);
	const [excelDelimiter, setExcelDelimiter] = useState(",");
	const [excelDateFormat, setExcelDateFormat] = useState("yyyy-MM-dd HH:mm:ss");
	const [excelSkipErrors, setExcelSkipErrors] = useState(true);
	const [excelFillMerged, setExcelFillMerged] = useState(true);
	const [bindingOpen, setBindingOpen] = useState(false);
	const [bindingLoading, setBindingLoading] = useState(false);
	const [bindingSubmitting, setBindingSubmitting] = useState(false);
	const [bindingTemplates, setBindingTemplates] = useState<TopicBindingTemplateView[]>([]);
	const [bindingDiagnostics, setBindingDiagnostics] = useState<TopicBindingDiagnostics | null>(null);
	const [bindingSources, setBindingSources] = useState<TopicSourceCandidate[]>([]);
	const [selectedTemplateCode, setSelectedTemplateCode] = useState<string>();
	const [selectedEntityCode, setSelectedEntityCode] = useState<string>();
	const [selectedDataSourceId, setSelectedDataSourceId] = useState<string>();
	const [selectedSourceKey, setSelectedSourceKey] = useState<string>();

	const previewColumns = useMemo(() => {
		if (!excelParseResult?.columns?.length) {
			return [];
		}
		return excelParseResult.columns.slice(0, 8).map((column) => ({
			title: column.label || column.name,
			dataIndex: column.name,
			key: column.name,
			ellipsis: true as const,
			width: 180,
		}));
	}, [excelParseResult]);

	const previewRows = useMemo(() => toPreviewRows(excelParseResult), [excelParseResult]);
	const bindingRows = useMemo(() => (Array.isArray(bindingDiagnostics?.rows) ? bindingDiagnostics.rows : []), [bindingDiagnostics]);
	const templateOptions = useMemo(
		() =>
			bindingTemplates.map((item) => ({
				value: item.templateCode,
				label: item.templateName,
			})),
		[bindingTemplates],
	);
	const entityOptions = useMemo(
		() =>
			bindingRows
				.filter((row) => row.templateCode === selectedTemplateCode)
				.map((row) => ({
					value: row.entityCode,
					label: `${row.entityName || row.entityCode}${row.required ? "（必填）" : ""}`,
				})),
		[bindingRows, selectedTemplateCode],
	);
	const selectedBindingRow = useMemo(
		() => bindingRows.find((row) => row.templateCode === selectedTemplateCode && row.entityCode === selectedEntityCode) || null,
		[bindingRows, selectedEntityCode, selectedTemplateCode],
	);
	const uniqueDataSources = useMemo(() => {
		const seen = new Set<string>();
		return bindingSources
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
	}, [bindingSources]);
	const filteredBindingSources = useMemo(() => {
		if (!normalizeText(selectedDataSourceId)) {
			return bindingSources;
		}
		return bindingSources.filter((item) => normalizeText(item.sourceDataSourceId) === normalizeText(selectedDataSourceId));
	}, [bindingSources, selectedDataSourceId]);
	const batchIssueRows = useMemo(() => batchIssuePreview?.rows || [], [batchIssuePreview]);

	useEffect(() => {
		if (!selectedTemplateCode) {
			return;
		}
		const currentEntityExists = bindingRows.some((row) => row.templateCode === selectedTemplateCode && row.entityCode === selectedEntityCode);
		if (!currentEntityExists) {
			const nextEntity = bindingRows.find((row) => row.templateCode === selectedTemplateCode)?.entityCode;
			setSelectedEntityCode(nextEntity);
		}
	}, [bindingRows, selectedEntityCode, selectedTemplateCode]);

	useEffect(() => {
		if (!bindingOpen) {
			return;
		}
		const preferred = selectPreferredTopicSource(filteredBindingSources, selectedDataSourceId);
		const nextKey = buildTopicSourceKey(preferred);
		setSelectedSourceKey((current) =>
			current && filteredBindingSources.some((item) => buildTopicSourceKey(item) === current) ? current : nextKey || undefined,
		);
	}, [bindingOpen, filteredBindingSources, selectedDataSourceId]);

	const resetAll = () => {
		setExcelPrepared(null);
		setExcelParseResult(null);
		setBatchLoadResult(null);
		setBatchIssuePreview(null);
		setBatchIssueSeverity("ERROR");
		setErrorRows([]);
		setExcelSheetName(undefined);
		setExcelHeaderRow(1);
		setExcelDataStartRow(2);
		setExcelDelimiter(",");
		setExcelDateFormat("yyyy-MM-dd HH:mm:ss");
		setExcelSkipErrors(true);
		setExcelFillMerged(true);
		setBindingOpen(false);
		setBindingTemplates([]);
		setBindingDiagnostics(null);
		setBindingSources([]);
		setSelectedTemplateCode(undefined);
		setSelectedEntityCode(undefined);
		setSelectedDataSourceId(undefined);
		setSelectedSourceKey(undefined);
	};

	const handleExcelUpload = async (options: UploadRequestOption) => {
		const file = options.file as File;
		if (!file) return;
		const maxSize = 200 * 1024 * 1024;
		if (file.size > maxSize) {
			message.error("文件超过 200MB 限制");
			options.onError?.(new Error("file_too_large"));
			return;
		}
		setExcelUploading(true);
		try {
			const resp = await dataSourcesService.excelPrepare(file);
			setExcelPrepared(resp);
			setExcelParseResult(null);
			setBatchLoadResult(null);
			setErrorRows([]);
			setExcelSheetName(resp.sheets?.[0]?.name);
			message.success("文件已上传，请继续解析");
			options.onSuccess?.(resp as never);
		} catch (error: any) {
			message.error(error?.message || "文件上传失败");
			options.onError?.(error);
		} finally {
			setExcelUploading(false);
		}
	};

	const handleExcelParse = async () => {
		if (!excelPrepared?.fileId) {
			message.warning("请先上传 Excel/CSV");
			return;
		}
		setExcelParsing(true);
		try {
			const resp = await dataSourcesService.excelParse({
				fileId: excelPrepared.fileId,
				sheetName: excelSheetName,
				headerRow: excelHeaderRow,
				dataStartRow: excelDataStartRow,
				delimiter: excelDelimiter,
				previewLimit: 20,
				skipErrors: excelSkipErrors,
				fillMerged: excelFillMerged,
				dateFormat: excelDateFormat,
			});
			setExcelParseResult(resp);
			setBatchLoadResult(null);
			setBatchIssuePreview(null);
			setBatchIssueSeverity("ERROR");
			setErrorRows([]);
			message.success("解析完成，可以查看预览并执行项目主体域落库");
		} catch (error: any) {
			message.error(error?.message || "解析失败");
		} finally {
			setExcelParsing(false);
		}
	};

	const handleLoadErrors = async () => {
		if (!excelPrepared?.fileId || !excelParseResult?.errorCount) {
			return;
		}
		setLoadingErrors(true);
		try {
			const resp = await dataSourcesService.excelErrors({ fileId: excelPrepared.fileId, limit: 50 });
			setErrorRows(resp.rows || []);
		} catch (error: any) {
			message.error(error?.message || "加载错误预览失败");
		} finally {
			setLoadingErrors(false);
		}
	};

	const handleLoadBatch = async () => {
		if (!excelPrepared?.fileId || !excelParseResult) {
			message.warning("请先完成解析");
			return;
		}
		setLoadingBatch(true);
		try {
			const resp = await dataSourcesService.excelLoadProjectCockpit({ fileId: excelPrepared.fileId });
			setBatchLoadResult(resp);
			const nextSeverity: "ERROR" | "WARN" = resp.rejectedRowCount > 0 ? "ERROR" : "WARN";
			setBatchIssueSeverity(nextSeverity);
			if (resp.issueCount > 0) {
				try {
					const issuePreview = await dataSourcesService.projectCockpitIssues({
						batchId: resp.batchId,
						severity: nextSeverity,
						limit: 50,
					});
					setBatchIssuePreview(issuePreview);
				} catch (error: any) {
					setBatchIssuePreview(null);
					message.warning(error?.message || "批次已落库，但问题明细暂时加载失败");
				}
			} else {
				setBatchIssuePreview(null);
			}
			message.success("项目主体域批次已落库，可继续触发中台建模刷新");
		} catch (error: any) {
			message.error(error?.message || "项目主体域落库失败");
		} finally {
			setLoadingBatch(false);
		}
	};

	const handleLoadBatchIssues = async (severity: "ERROR" | "WARN" = batchIssueSeverity) => {
		if (!batchLoadResult?.batchId) {
			message.warning("请先完成正式落库");
			return;
		}
		setBatchIssueSeverity(severity);
		setLoadingBatchIssues(true);
		try {
			const resp = await dataSourcesService.projectCockpitIssues({
				batchId: batchLoadResult.batchId,
				severity,
				limit: 50,
			});
			setBatchIssuePreview(resp);
		} catch (error: any) {
			message.error(error?.message || "加载落库问题明细失败");
		} finally {
			setLoadingBatchIssues(false);
		}
	};

	const openBindingModal = async () => {
		if (!batchLoadResult) {
			message.warning("请先完成正式落库");
			return;
		}
		setBindingLoading(true);
		setBindingOpen(true);
		try {
			const [templateResp, statusResp, sourceResp] = await Promise.all([
				topicBindingService.listTemplates(),
				topicBindingService.getStatus(),
				listDbtSources() as Promise<TopicSourceCandidate[]>,
			]);
			const nextTemplates = Array.isArray(templateResp) ? templateResp : [];
			const nextDiagnostics = statusResp || null;
			const nextSources = Array.isArray(sourceResp) ? sourceResp : [];
			setBindingTemplates(nextTemplates);
			setBindingDiagnostics(nextDiagnostics);
			setBindingSources(nextSources);
			const recommendation = selectRecommendedProjectCockpitBinding(nextTemplates, nextDiagnostics);
			setSelectedTemplateCode(recommendation?.templateCode || nextTemplates[0]?.templateCode);
			setSelectedEntityCode(recommendation?.entityCode);
			const preferredSource = selectPreferredTopicSource(nextSources);
			setSelectedDataSourceId(normalizeText(preferredSource?.sourceDataSourceId) || undefined);
			setSelectedSourceKey(buildTopicSourceKey(preferredSource) || undefined);
		} catch (error: any) {
			message.error(error?.message || "加载专题绑定上下文失败");
			setBindingOpen(false);
		} finally {
			setBindingLoading(false);
		}
	};

	const closeBindingModal = () => {
		setBindingOpen(false);
	};

	const submitTopicBinding = async () => {
		if (!batchLoadResult) {
			message.warning("请先完成正式落库");
			return;
		}
		if (!selectedTemplateCode || !selectedEntityCode) {
			message.warning("请选择专题模板和逻辑实体");
			return;
		}
		const candidate = filteredBindingSources.find((item) => buildTopicSourceKey(item) === selectedSourceKey);
		if (!candidate?.table) {
			message.warning("请选择一个可用的 ODS 表");
			return;
		}
		setBindingSubmitting(true);
		try {
			await topicBindingService.bindOdsTable({
				templateCode: selectedTemplateCode,
				entityCode: selectedEntityCode,
				dataSourceId: normalizeText(candidate.sourceDataSourceId) || undefined,
				schemaName: normalizeText(candidate.schema) || normalizeText(selectedBindingRow?.expectedSchema) || "ods",
				tableName: candidate.table,
				odsMappingId: normalizeText(candidate.id) || undefined,
				batchId: batchLoadResult.batchId,
				notes: `bind after project import ${batchLoadResult.batchCode}`,
			});
			message.success("专题绑定已完成");
			setBindingDiagnostics((current) => {
				if (!current) {
					return current;
				}
				return {
					...current,
					rows: current.rows.map((row) =>
						row.templateCode === selectedTemplateCode && row.entityCode === selectedEntityCode
							? {
									...row,
									bound: true,
									boundSchemaName: normalizeText(candidate.schema) || normalizeText(selectedBindingRow?.expectedSchema) || "ods",
									boundTableName: candidate.table,
									bindingStatus: "ACTIVE",
								}
							: row,
					),
					missingRequired: current.missingRequired.filter(
						(item) => item !== `${selectedTemplateCode}.${selectedEntityCode}`,
					),
				};
			});
			setBindingOpen(false);
		} catch (error: any) {
			message.error(error?.message || "专题绑定失败");
		} finally {
			setBindingSubmitting(false);
		}
	};

	return (
		<div className="space-y-6">
			<Card
				title="项目主体域接入"
				extra={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={resetAll}>
							重置
						</Button>
						<Button onClick={() => navigate("/foundation/topic-bindings")}>专题绑定中心</Button>
						<Button type="primary" icon={<CloudUploadOutlined />} onClick={handleLoadBatch} disabled={!excelParseResult} loading={loadingBatch}>
							正式落库
						</Button>
					</Space>
				}
			>
				<Space direction="vertical" size={16} style={{ width: "100%" }}>
					<div>
						<Title level={5} style={{ marginBottom: 8 }}>
							标准链路
						</Title>
						<Paragraph type="secondary" style={{ marginBottom: 0 }}>
							上传 Excel/CSV 后，先完成字段解析和容错预览，再将批次正式落入项目主体域。后续由 DTS 中台调度 ODS、dbt 与项目看板 ADS，
							当前页面只负责信息科上传与批次装载，不直接作为看板数据源。
						</Paragraph>
					</div>

					<Upload.Dragger
						name="file"
						multiple={false}
						maxCount={1}
						showUploadList={false}
						accept=".xlsx,.csv"
						customRequest={handleExcelUpload}
						disabled={excelUploading}
					>
						<p className="ant-upload-drag-icon">
							<InboxOutlined />
						</p>
						<p className="ant-upload-text">点击或拖拽上传项目主体域 Excel/CSV 文件</p>
						<p className="ant-upload-hint">{excelPrepared?.fileName || "支持 .xlsx / .csv，最大 200MB"}</p>
					</Upload.Dragger>

					<Form layout="vertical">
						{excelPrepared?.sheets?.length ? (
							<Form.Item label="Sheet">
								<Input value={excelSheetName} onChange={(e) => setExcelSheetName(e.target.value)} list="project-cockpit-sheet-options" />
								<datalist id="project-cockpit-sheet-options">
									{excelPrepared.sheets.map((sheet) => (
										<option key={sheet.name} value={sheet.name} />
									))}
								</datalist>
							</Form.Item>
						) : null}
						<Form.Item label="表头行（1-based）">
							<InputNumber min={1} value={excelHeaderRow} onChange={(value) => setExcelHeaderRow(value || 1)} />
						</Form.Item>
						<Form.Item label="数据起始行（1-based）">
							<InputNumber min={1} value={excelDataStartRow} onChange={(value) => setExcelDataStartRow(value || 2)} />
						</Form.Item>
						<Form.Item label="分隔符">
							<Input value={excelDelimiter} onChange={(e) => setExcelDelimiter(e.target.value || ",")} />
						</Form.Item>
						<Form.Item label="日期格式">
							<Input value={excelDateFormat} onChange={(e) => setExcelDateFormat(e.target.value)} />
						</Form.Item>
						<Form.Item label="合并单元格填充">
							<Switch checked={excelFillMerged} onChange={setExcelFillMerged} />
						</Form.Item>
						<Form.Item label="容错跳过">
							<Switch checked={excelSkipErrors} onChange={setExcelSkipErrors} />
						</Form.Item>
					</Form>

					<Space>
						<Button type="primary" icon={<PlayCircleOutlined />} onClick={handleExcelParse} loading={excelParsing} disabled={!excelPrepared}>
							解析并生成预览
						</Button>
						<Button icon={<EyeOutlined />} onClick={handleLoadErrors} loading={loadingErrors} disabled={!excelParseResult?.errorCount}>
							查看错误预览
						</Button>
					</Space>
				</Space>
			</Card>

			<Card title="批次与解析结果">
				<Descriptions bordered size="small" column={2}>
					<Descriptions.Item label="上传文件">{excelPrepared?.fileName || "-"}</Descriptions.Item>
					<Descriptions.Item label="批次号">{excelPrepared?.batchCode || batchLoadResult?.batchCode || "-"}</Descriptions.Item>
					<Descriptions.Item label="解析行数">{excelParseResult?.rowCount ?? "-"}</Descriptions.Item>
					<Descriptions.Item label="解析错误">{excelParseResult?.errorCount ?? "-"}</Descriptions.Item>
					<Descriptions.Item label="落库状态">
						{batchLoadResult?.status ? (
							<Tag color={resolveBatchStatusColor(batchLoadResult.status)}>{batchLoadResult.status}</Tag>
						) : (
							<Text type="secondary">未落库</Text>
						)}
					</Descriptions.Item>
					<Descriptions.Item label="落库结果">
						{batchLoadResult ? `${batchLoadResult.loadedRowCount} 行 / ${batchLoadResult.issueCount} 条问题` : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="可入湖记录">
						{batchLoadResult ? `${batchLoadResult.acceptedRowCount} 行` : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="拦截记录">
						{batchLoadResult ? `${batchLoadResult.rejectedRowCount} 行` : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="告警记录">
						{batchLoadResult ? `${batchLoadResult.warningRowCount} 行` : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="统计说明">
						{batchLoadResult ? "告警记录已计入可入湖行数，拦截记录可在下方查看明细" : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="后续动作" span={2}>
						{batchLoadResult ? (
							<Space wrap>
								<Button type="primary" onClick={() => void openBindingModal()}>
									绑定到专题
								</Button>
								<Button type="link" onClick={() => navigate("/foundation/topic-bindings")}>
									去专题绑定中心
								</Button>
							</Space>
						) : (
							"-"
						)}
					</Descriptions.Item>
				</Descriptions>
			</Card>

			<Card title="字段预览" extra={excelParseResult ? <Text type="secondary">预览 {previewRows.length} 行，最多展示 8 列</Text> : null}>
				<Table
					rowKey="key"
					size="small"
					scroll={{ x: 1200 }}
					pagination={false}
					dataSource={previewRows}
					columns={previewColumns as never}
					locale={{ emptyText: "完成解析后显示预览" }}
				/>
			</Card>

			<Card title="错误预览与容错说明">
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Paragraph type="secondary" style={{ marginBottom: 0 }}>
						项目主体域接入遵循“脏数据不断链”的原则。格式异常、空值、自由文本和映射缺口会被记录为问题明细，批次仍可落库，后续通过口径支撑页展示覆盖率与待补清单。
					</Paragraph>
					<Table
						rowKey={(_, index) => String(index)}
						size="small"
						pagination={false}
						dataSource={errorRows}
						columns={[
							{ title: "行号", dataIndex: "rowIndex", key: "rowIndex", width: 120 },
							{ title: "问题说明", dataIndex: "message", key: "message" },
						]}
						locale={{ emptyText: excelParseResult?.errorCount ? "点击“查看错误预览”加载明细" : "当前没有错误明细" }}
					/>
				</Space>
			</Card>

			<Card
				title="落库问题明细"
				extra={
					batchLoadResult ? (
						<Space>
							<Select
								value={batchIssueSeverity}
								style={{ width: 180 }}
								options={[
									{ value: "ERROR", label: "拦截记录（ERROR）" },
									{ value: "WARN", label: "告警记录（WARN）" },
								]}
								onChange={(value) => void handleLoadBatchIssues(value)}
							/>
							<Button icon={<ReloadOutlined />} onClick={() => void handleLoadBatchIssues()} loading={loadingBatchIssues}>
								刷新明细
							</Button>
						</Space>
					) : null
				}
			>
				<Space direction="vertical" style={{ width: "100%" }} size={12}>
					<Paragraph type="secondary" style={{ marginBottom: 0 }}>
						这里展示正式落库阶段被拦截或带告警的记录，用来解释“Excel 总行数”和“最终可入湖行数”之间的差异。
					</Paragraph>
					{batchLoadResult ? (
						<Alert
							type={batchIssueSeverity === "ERROR" ? "error" : "warning"}
							showIcon
							message={`当前批次 ${batchLoadResult.batchCode}：已评估 ${batchLoadResult.loadedRowCount} 行，可入湖 ${batchLoadResult.acceptedRowCount} 行，拦截 ${batchLoadResult.rejectedRowCount} 行，告警 ${batchLoadResult.warningRowCount} 行。`}
							description={
								batchIssuePreview
									? `当前展示 ${batchIssueSeverity} 明细 ${batchIssuePreview.rows.length} / ${batchIssuePreview.issueRowCount} 条。`
									: "落库后可按 ERROR/WARN 查看问题明细。"
							}
						/>
					) : null}
					<Table
						rowKey={(row, index) => `${row.rowIndex || 0}-${row.issueCode || "issue"}-${index || 0}`}
						size="small"
						scroll={{ x: 1400 }}
						pagination={false}
						loading={loadingBatchIssues}
						dataSource={batchIssueRows}
						columns={[
							{ title: "行号", dataIndex: "rowIndex", key: "rowIndex", width: 90 },
							{
								title: "级别",
								dataIndex: "severity",
								key: "severity",
								width: 100,
								render: (value: string) => <Tag color={value === "ERROR" ? "error" : "warning"}>{value || "-"}</Tag>,
							},
							{ title: "问题编码", dataIndex: "issueCode", key: "issueCode", width: 260 },
							{ title: "问题说明", dataIndex: "message", key: "message", width: 260 },
							{ title: "项目编号", dataIndex: "projectNo", key: "projectNo", width: 160 },
							{ title: "分系统", dataIndex: "subsystem", key: "subsystem", width: 160 },
							{ title: "节点任务", dataIndex: "nodeTask", key: "nodeTask", width: 180 },
							{ title: "计划日期", dataIndex: "planDate", key: "planDate", width: 160 },
							{ title: "完成状态", dataIndex: "completionStatus", key: "completionStatus", width: 180 },
							{ title: "风险等级", dataIndex: "riskLevel", key: "riskLevel", width: 120 },
						]}
						locale={{
							emptyText: batchLoadResult
								? batchLoadResult.issueCount
									? "当前筛选下没有问题明细"
									: "本批次没有落库问题"
								: "完成正式落库后显示问题明细",
						}}
					/>
				</Space>
			</Card>

			<Modal
				open={bindingOpen}
				title="绑定到专题逻辑实体"
				onCancel={closeBindingModal}
				onOk={() => void submitTopicBinding()}
				confirmLoading={bindingSubmitting}
				okText="完成绑定"
				destroyOnClose
			>
				<Space direction="vertical" size={16} style={{ width: "100%" }}>
					<Alert
						type="info"
						showIcon
						message="当前绑定基于项目主体域批次"
						description={`批次 ${batchLoadResult?.batchCode || "-"} 已落库。请选择专题模板、逻辑实体和现场 ODS 表，系统会把这次批次记录写入专题绑定。`}
					/>
					<Select
						placeholder="选择专题模板"
						loading={bindingLoading}
						value={selectedTemplateCode}
						options={templateOptions}
						onChange={(value) => setSelectedTemplateCode(value)}
					/>
					<Select
						placeholder="选择逻辑实体"
						loading={bindingLoading}
						value={selectedEntityCode}
						options={entityOptions}
						onChange={(value) => setSelectedEntityCode(value)}
					/>
					<Select
						placeholder="选择来源数据源"
						loading={bindingLoading}
						value={selectedDataSourceId}
						options={uniqueDataSources}
						onChange={(value) => setSelectedDataSourceId(value)}
					/>
					<Select
						placeholder="选择现场 ODS 表"
						loading={bindingLoading}
						value={selectedSourceKey}
						options={filteredBindingSources.map((item) => ({
							value: buildTopicSourceKey(item),
							label: `${item.schema || "ods"}.${item.table || "-"}`,
						}))}
						onChange={(value) => setSelectedSourceKey(value)}
					/>
					{selectedBindingRow ? (
						<Descriptions bordered size="small" column={1}>
							<Descriptions.Item label="逻辑 Source">{selectedBindingRow.sourceName || "-"}</Descriptions.Item>
							<Descriptions.Item label="逻辑表">{selectedBindingRow.logicalTableName || "-"}</Descriptions.Item>
							<Descriptions.Item label="要求">{selectedBindingRow.required ? "必填" : "可选"}</Descriptions.Item>
						</Descriptions>
					) : null}
				</Space>
			</Modal>
		</div>
	);
}
