import { useEffect, useState } from "react";
import {
	Alert,
	Button,
	Card,
	Collapse,
	Divider,
	Form,
	Input,
	InputNumber,
	Modal,
	Radio,
	Select,
	Space,
	Switch,
	Tag,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import { DeleteOutlined, InboxOutlined, PlusOutlined } from "@ant-design/icons";
import { Upload } from "@/components/upload";
import { toast } from "sonner";
import type { FormInstance } from "antd/es/form";
import type { FileUploadResult } from "@/api/ingestion";
import type { ColumnInfo } from "@/api/sql-workbench";
import type { SyncModeOption, ExtraColumnDef } from "./types";
import { applyPastedOdsFieldsToFileColumns, parsePastedOdsFields } from "../fileOdsPasteMapping.helpers";
import {
	normalizeText,
	normalizeTableName,
	buildFileBaseName,
} from "../ingestionFormHelpers";
import OdsLandingContractCard from "./OdsLandingContractCard";

const { Text } = Typography;

type OdsTableOption = { label: string; value: string };

export type FileUnifiedStepProps = {
	form: FormInstance;
	fileUploadResult: FileUploadResult | null;
	setFileUploadResult: (result: FileUploadResult | null) => void;
	odsColumns: ColumnInfo[];
	odsMatchApplied: boolean;
	defaultDestinationStatus: { destinationName?: string; available?: boolean; writerType?: string; message?: string } | null;

	/* schedule & sync */
	syncModeOptions: SyncModeOption[];
	scheduleType: string;
	activeCapabilitySet: Set<string>;
	capabilityLoadFailed: boolean;
	onSourceCategoryChange: (next: string) => void;

	/* file upload */
	uploadingFile: boolean;
	onFileUpload: (file: File, onSuccess?: (result: any) => void, onError?: (err: any) => void) => void;
	onSheetChange: (sheetIndex: number) => void;

	/* preview controls */
	filePreviewRows: number;
	setFilePreviewRows: (v: number) => void;
	filePreviewCols: number;
	setFilePreviewCols: (v: number) => void;
	refreshFilePreview: () => void;
	previewRefreshing: boolean;
	openErrorPreview: () => void;
	errorPreviewLoading: boolean;

	/* ODS matching */
	lakeDatasourceId: string | number | null;
	odsTableLoading: boolean;
	selectedOdsTable: string | undefined;
	odsTableList: OdsTableOption[];
	loadOdsTables: () => void;
	handleOdsTableSelect: (tableName: string | undefined) => void;
	odsColumnsLoading: boolean;
	applyOdsMapping: () => void;
	unmatchedOdsFields: ColumnInfo[];

	/* cron validator */
	validateCronExpression: (rule: unknown, value: string) => Promise<void>;

	/* extra columns (from FileTargetStep) */
	extraColumns: ExtraColumnDef[];
	setExtraColumns: (cols: ExtraColumnDef[]) => void;

	/* dept options (new) */
	deptOptions: { label: string; value: string }[];
	loadingDeptOptions: boolean;
};

const fileTableNameValidator = (_: any, value: string) => {
	const text = normalizeText(value);
	if (!text) {
		return Promise.resolve();
	}
	const normalized = normalizeTableName(text);
	if (!normalized) {
		return Promise.reject(new Error("目标表名仅支持字母、数字、下划线，可包含 schema"));
	}
	return Promise.resolve();
};

export default function FileUnifiedStep({
	form,
	fileUploadResult,
	setFileUploadResult,
	odsColumns,
	odsMatchApplied,
	defaultDestinationStatus,
	syncModeOptions,
	scheduleType,
	activeCapabilitySet,
	capabilityLoadFailed,
	onSourceCategoryChange,
	uploadingFile,
	onFileUpload,
	onSheetChange,
	filePreviewRows,
	setFilePreviewRows,
	filePreviewCols,
	setFilePreviewCols,
	refreshFilePreview,
	previewRefreshing,
	openErrorPreview,
	errorPreviewLoading,
	lakeDatasourceId,
	odsTableLoading,
	selectedOdsTable,
	odsTableList,
	loadOdsTables,
	handleOdsTableSelect,
	odsColumnsLoading,
	applyOdsMapping,
	unmatchedOdsFields,
	validateCronExpression,
	extraColumns,
	deptOptions,
	loadingDeptOptions,
}: FileUnifiedStepProps) {
	const [batchFieldModalOpen, setBatchFieldModalOpen] = useState(false);
	const [batchFieldText, setBatchFieldText] = useState("");
	const [manualOdsMatchApplied, setManualOdsMatchApplied] = useState(false);
	const [manualMatchedCount, setManualMatchedCount] = useState(0);
	const [manualUnmatchedOdsFields, setManualUnmatchedOdsFields] = useState<string[]>([]);

	const normalizedScheduleType = String(scheduleType || "manual").trim() || "manual";
	const effectiveOdsMatchApplied = odsMatchApplied || manualOdsMatchApplied;
	const effectiveMatchedCount = odsMatchApplied
		? Math.min(odsColumns.length, fileUploadResult?.columns?.length || 0)
		: manualMatchedCount;
	const effectiveUnmatchedFields = odsMatchApplied
		? unmatchedOdsFields.map((column) => column?.name).filter((name): name is string => Boolean(name))
		: manualUnmatchedOdsFields;

	useEffect(() => {
		setManualOdsMatchApplied(false);
		setManualMatchedCount(0);
		setManualUnmatchedOdsFields([]);
		setBatchFieldText("");
	}, [fileUploadResult?.fileId, fileUploadResult?.sheetIndex]);

	return (
		<>
			{/* ─── 基础信息 ─── */}
			<Divider orientation="left">基础信息</Divider>
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item
					name="name"
					label="任务名称"
					rules={[{ required: true, message: "请输入任务名称" }]}
				>
					<Input placeholder="例如：csv-import-task" />
				</Form.Item>
				<Form.Item
					name="ownerDept"
					label="归属部门"
					rules={[{ required: true, message: "请选择归属部门" }]}
				>
					<Select
						showSearch
						placeholder="请选择归属部门"
						loading={loadingDeptOptions}
						options={deptOptions}
						filterOption={(input, option) =>
							(option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
						}
					/>
				</Form.Item>
			</div>
			<Form.Item name="description" label="描述">
				<Input.TextArea rows={2} placeholder="可选，说明任务用途" />
			</Form.Item>

			{/* ─── 数据来源切换 ─── */}
			<Divider orientation="left">数据来源切换</Divider>
			<Form.Item name="sourceCategory" label="数据来源">
				<Radio.Group onChange={(e) => onSourceCategoryChange(e.target.value)}>
					<Radio.Button value="database">数据库</Radio.Button>
					<Radio.Button value="file">文件上传</Radio.Button>
					<Radio.Button value="api">API 接入</Radio.Button>
				</Radio.Group>
			</Form.Item>

			{/* ─── 文件上传 ─── */}
			<Divider orientation="left">文件上传</Divider>
			<Form.Item label="上传文件" required>
				<Upload
					secretModule
					accept=".xlsx,.csv"
					maxCount={1}
					showUploadList={false}
					customRequest={async ({ file, onSuccess, onError }) => {
						onFileUpload(file as File, onSuccess, onError);
					}}
					disabled={uploadingFile}
				>
					<p className="ant-upload-drag-icon">
						<InboxOutlined />
					</p>
					<p className="ant-upload-text">
						{uploadingFile ? "上传中..." : "点击或拖拽上传 Excel / CSV 文件"}
					</p>
					<p className="ant-upload-hint">支持 .xlsx, .csv 格式</p>
				</Upload>
			</Form.Item>
			{fileUploadResult && (
				<Card type="inner" title={`已解析文件: ${fileUploadResult.originalName}`} className="mb-4">
					<Text type="secondary" className="block mb-2">
						文件类型: <Tag>{fileUploadResult.sourceFileType || fileUploadResult.fileType}</Tag>
						检测到 {fileUploadResult.columns?.length || 0} 列
						{typeof fileUploadResult.rowCount === "number" && (
							<>
								{" · "}预览总行数: <Tag color="blue">{fileUploadResult.rowCount}</Tag>
							</>
						)}
						{typeof fileUploadResult.errorCount === "number" && (
							<>
								{" · "}错误行:{" "}
								<Tag color={fileUploadResult.errorCount > 0 ? "red" : "green"}>
									{fileUploadResult.errorCount}
								</Tag>
							</>
						)}
					</Text>
					{Array.isArray(fileUploadResult.sheets) && fileUploadResult.sheets.length > 1 && (
						<Space className="mb-3" wrap>
							<Text type="secondary">选择 Sheet：</Text>
							<Select
								style={{ minWidth: 200 }}
								value={fileUploadResult.sheetIndex}
								options={fileUploadResult.sheets.map((sheet) => ({
									label: sheet.name,
									value: sheet.index,
								}))}
								onChange={(value) => onSheetChange(value)}
							/>
						</Space>
					)}
					<Space className="mb-3" wrap>
						<Text type="secondary">预览行数</Text>
						<InputNumber
							min={1}
							max={2000}
							value={filePreviewRows}
							onChange={(value) => setFilePreviewRows(value ? Number(value) : 20)}
						/>
						<Text type="secondary">预览列数</Text>
						<InputNumber
							min={1}
							max={50}
							value={filePreviewCols}
							onChange={(value) => setFilePreviewCols(value ? Number(value) : 8)}
						/>
						<Button size="small" onClick={refreshFilePreview} loading={previewRefreshing}>
							刷新预览
						</Button>
						{(fileUploadResult.errorCount || 0) > 0 && (
							<Button size="small" onClick={openErrorPreview} loading={errorPreviewLoading}>
								查看错误行
							</Button>
						)}
					</Space>
					{/* ODS 表关联 */}
					{(fileUploadResult.columns?.length ?? 0) > 0 && (
						<div style={{ marginBottom: 12, padding: "8px 12px", background: "#fafafa", borderRadius: 6, border: "1px solid #f0f0f0" }}>
							<Space wrap>
								<Text type="secondary">关联 ODS 表：</Text>
								{lakeDatasourceId ? (
									<Select
										size="small"
										style={{ width: 280 }}
										placeholder="选择 ODS 表以自动匹配字段名"
										allowClear
										showSearch
										loading={odsTableLoading}
										value={selectedOdsTable}
										onFocus={() => { if (!odsTableList.length) loadOdsTables(); }}
										onChange={handleOdsTableSelect}
										options={odsTableList}
										filterOption={(input, option) =>
											(option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
										}
									/>
								) : (
									<Text type="secondary">未配置数据湖连接时，可直接粘贴 ODS 字段列表。</Text>
								)}
								{lakeDatasourceId ? (
									<Button
										size="small"
										type="primary"
										loading={odsColumnsLoading}
										disabled={!odsColumns.length}
										onClick={() => {
											setManualOdsMatchApplied(false);
											setManualMatchedCount(0);
											setManualUnmatchedOdsFields([]);
											applyOdsMapping();
										}}
									>
										自动匹配
									</Button>
								) : null}
								<Button
									size="small"
									onClick={() => setBatchFieldModalOpen(true)}
								>
									粘贴 ODS 字段
								</Button>
								{effectiveOdsMatchApplied && (
									<Text style={{ color: "#52c41a" }}>
										✓ 已匹配 {effectiveMatchedCount} 个字段
									</Text>
								)}
							</Space>
						</div>
					)}
					<CompactTable
						size="small"
						dataSource={fileUploadResult.columns || []}
						rowKey={(_: any, index: any) => String(index)}
						pagination={false}
						columns={[
							{
								title: "显示名称",
								dataIndex: "label",
								sorter: (a, b) => (a.label || "").localeCompare(b.label || ""),
								render: (value: string, _: any, index: number) => (
									<Input
										size="small"
										value={value || ""}
										placeholder="中文名/显示名"
										onChange={(e) => {
											const cols = [...(fileUploadResult.columns || [])];
											cols[index] = { ...cols[index], label: e.target.value };
											setFileUploadResult({ ...fileUploadResult, columns: cols });
										}}
									/>
								),
							},
							{
								title: "字段名",
								dataIndex: "name",
								sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
								render: (value: string, record: any, index: number) => (
									<Space size={4}>
										<Input
											size="small"
											value={value}
											placeholder="英文字段名"
											style={record._odsExtra ? { color: "#999" } : undefined}
											onChange={(e) => {
												const cols = [...(fileUploadResult.columns || [])];
												cols[index] = { ...cols[index], name: e.target.value };
												setFileUploadResult({ ...fileUploadResult, columns: cols });
											}}
										/>
										{effectiveOdsMatchApplied && record._odsMatched && (
											<Tag color="green" style={{ margin: 0 }}>ODS</Tag>
										)}
										{effectiveOdsMatchApplied && record._odsExtra && (
											<Tag color="default" style={{ margin: 0 }}>未关联</Tag>
										)}
									</Space>
								),
							},
							{
								title: "数据类型",
								dataIndex: "type",
								width: 170,
								render: (value: string, _: any, index: number) => (
									<Select
										size="small"
										value={value}
										style={{ width: "100%" }}
										onChange={(v) => {
											const cols = [...(fileUploadResult.columns || [])];
											const patch: Record<string, any> = { type: v };
											if (v === "string" && !cols[index].length) patch.length = 500;
											if (v === "numeric" && !cols[index].precision) { patch.precision = 18; patch.scale = 2; }
											if (v !== "string") patch.length = undefined;
											if (v !== "numeric") { patch.precision = undefined; patch.scale = undefined; }
											cols[index] = { ...cols[index], ...patch };
											setFileUploadResult({ ...fileUploadResult, columns: cols });
										}}
										options={[
											{ label: "VARCHAR", value: "string" },
											{ label: "TEXT", value: "text" },
											{ label: "INTEGER", value: "integer" },
											{ label: "BIGINT", value: "long" },
											{ label: "NUMERIC", value: "numeric" },
											{ label: "DOUBLE PRECISION", value: "double" },
											{ label: "BOOLEAN", value: "boolean" },
											{ label: "DATE", value: "date" },
											{ label: "TIMESTAMP", value: "timestamp" },
											{ label: "JSONB", value: "jsonb" },
										]}
									/>
								),
							},
							{
								title: "类型参数",
								dataIndex: "length",
								width: 180,
								render: (_: any, record: any, index: number) => {
									if (record.type === "string") {
										return (
											<InputNumber
												size="small"
												min={1}
												max={10485760}
												value={record.length ?? 500}
												addonBefore="长度"
												style={{ width: "100%" }}
												onChange={(v) => {
													const cols = [...(fileUploadResult.columns || [])];
													cols[index] = { ...cols[index], length: v ?? 500 };
													setFileUploadResult({ ...fileUploadResult, columns: cols });
												}}
											/>
										);
									}
									if (record.type === "numeric") {
										return (
											<Space size={4}>
												<InputNumber
													size="small"
													min={1}
													max={1000}
													value={record.precision ?? 18}
													addonBefore="精度"
													style={{ width: 110 }}
													onChange={(v) => {
														const cols = [...(fileUploadResult.columns || [])];
														cols[index] = { ...cols[index], precision: v ?? 18 };
														setFileUploadResult({ ...fileUploadResult, columns: cols });
													}}
												/>
												<InputNumber
													size="small"
													min={0}
													max={100}
													value={record.scale ?? 2}
													addonBefore="标度"
													style={{ width: 110 }}
													onChange={(v) => {
														const cols = [...(fileUploadResult.columns || [])];
														cols[index] = { ...cols[index], scale: v ?? 2 };
														setFileUploadResult({ ...fileUploadResult, columns: cols });
													}}
												/>
											</Space>
										);
									}
									return <Text type="secondary">-</Text>;
								},
							},
							{
								title: "操作",
								width: 60,
								align: "center" as const,
								render: (_: any, __: any, index: number) => (
									<Button
										type="text"
										danger
										size="small"
										icon={<DeleteOutlined />}
										onClick={() => {
											const cols = [...(fileUploadResult.columns || [])];
											cols.splice(index, 1);
											setFileUploadResult({ ...fileUploadResult, columns: cols });
										}}
									/>
								),
							},
						]}
					/>
					{effectiveUnmatchedFields.length > 0 && (
						<div style={{ marginTop: 8, padding: "8px 12px", background: "#fff2f0", border: "1px solid #ffccc7", borderRadius: 6 }}>
							<Text type="danger" strong style={{ display: "block", marginBottom: 4 }}>
								⚠ 以下 ODS 字段缺少对应的 Excel 列（将导致下游数仓数据不完整）：
							</Text>
							<Space wrap>
								{effectiveUnmatchedFields.map((fieldName, i) => (
									<Tag key={i} color="error">{fieldName}</Tag>
								))}
							</Space>
						</div>
					)}
					<Button
						type="dashed"
						size="small"
						icon={<PlusOutlined />}
						className="mt-2"
						onClick={() => {
							const cols = [...(fileUploadResult.columns || [])];
							const idx = cols.length + 1;
							cols.push({ name: `col_${idx}`, type: "string", label: "", length: 500 });
							setFileUploadResult({ ...fileUploadResult, columns: cols });
						}}
					>
						添加列
					</Button>
					<Modal
						title="粘贴 ODS 字段列表"
						open={batchFieldModalOpen}
						onCancel={() => setBatchFieldModalOpen(false)}
						onOk={() => {
							const pastedFields = parsePastedOdsFields(batchFieldText);
							if (pastedFields.length === 0) {
								toast.warning("未识别到有效字段");
								return;
							}
							const mappingResult = applyPastedOdsFieldsToFileColumns(fileUploadResult.columns || [], pastedFields);
							setFileUploadResult({ ...fileUploadResult, columns: mappingResult.columns });
							setManualOdsMatchApplied(true);
							setManualMatchedCount(mappingResult.matchedCount);
							setManualUnmatchedOdsFields(mappingResult.unmatchedFields);
							setBatchFieldModalOpen(false);
							toast.success(`已匹配 ${mappingResult.matchedCount} 个字段`);
						}}
						okText="确认"
						cancelText="取消"
					>
						<p className="mb-2 text-gray-500">
							请输入 ODS 字段列表，支持逗号、中文逗号、换行或 Tab 分隔，将按顺序匹配到现有 Excel 列：
						</p>
						<Input.TextArea
							rows={6}
							value={batchFieldText}
							onChange={(e) => setBatchFieldText(e.target.value)}
							placeholder={"field_a,field_b,field_c\n或每行一个字段名"}
						/>
					</Modal>
					{Array.isArray(fileUploadResult.preview) && fileUploadResult.preview.length > 0 && (
						<>
							<Divider orientation="left" className="mt-4">
								预览数据（最多 {filePreviewRows} 行）
							</Divider>
							<CompactTable
								size="small"
								pagination={false}
								rowKey="__row"
								scroll={{ x: true }}
								dataSource={fileUploadResult.preview.map((row, index) => {
									const record: Record<string, any> = { __row: index + 1 };
									(fileUploadResult.columns || []).forEach((col, colIndex) => {
										if (colIndex >= Math.max(1, filePreviewCols)) return;
										record[col.name] = row?.[colIndex] ?? "";
									});
									return record;
								})}
								columns={[
									{ title: "行号", dataIndex: "__row", width: 80 },
									...(fileUploadResult.columns || [])
										.slice(0, Math.max(1, filePreviewCols))
										.map((col) => ({
											title: col.label || col.name,
											dataIndex: col.name,
											ellipsis: true,
										})),
								]}
							/>
							{(fileUploadResult.columns || []).length > Math.max(1, filePreviewCols) && (
								<Text type="secondary" className="block mt-2">
									仅展示前 {Math.max(1, filePreviewCols)} 列，剩余列已省略。
								</Text>
							)}
						</>
					)}
				</Card>
			)}

			{/* ─── 目标表 ─── */}
			<Divider orientation="left">目标表</Divider>
			{defaultDestinationStatus ? (
				<Alert
					type={defaultDestinationStatus.available ? "success" : "warning"}
					showIcon
					message="默认数据湖"
					description={[
						defaultDestinationStatus.destinationName
							? `数据湖：${defaultDestinationStatus.destinationName}`
							: null,
						defaultDestinationStatus.writerType
							? `Writer：${defaultDestinationStatus.writerType}`
							: null,
						defaultDestinationStatus.message ? defaultDestinationStatus.message : null,
					]
						.filter(Boolean)
						.join(" · ")}
					className="mb-4"
				/>
			) : null}
			<Form.Item
				name="fileTableName"
				label="目标表名"
				rules={[{ validator: fileTableNameValidator }]}
				tooltip="仅允许字母、数字、下划线，可包含 schema.table"
			>
				<Input placeholder="例如：ods_patent_info" />
			</Form.Item>
			<Form.Item name="syncPrefix" label="目标表前缀">
				<Input placeholder="例如：ods_erp_" />
			</Form.Item>
			<Form.Item
				name="fileAutoId"
				label="自动生成ID"
				valuePropName="checked"
				tooltip="为文件入湖的目标表追加自增 ID 字段（默认开启）"
			>
				<Switch />
			</Form.Item>
			{fileUploadResult && (
				<Alert
					type="info"
					showIcon
					message={`目标表预览：${normalizeText(form.getFieldValue("fileTableName")) || (normalizeText(form.getFieldValue("syncPrefix")) + buildFileBaseName(fileUploadResult.originalName))}`}
					className="mb-4"
				/>
			)}

			{/* ─── 同步设置 ─── */}
			<Divider orientation="left">同步设置</Divider>
			<Form.Item name="syncMode" label="同步模式" tooltip="同步模式由连接器能力契约驱动">
				<Radio.Group>
					{syncModeOptions.map((item) => (
						<Radio.Button key={item.value} value={item.value} disabled={item.disabled}>
							{item.label}
						</Radio.Button>
					))}
				</Radio.Group>
			</Form.Item>
			<div className="grid gap-4 md:grid-cols-3">
				<Form.Item name="scheduleType" label="调度策略">
					<Select
						options={[
							{ label: "手动触发", value: "manual" },
							{ label: "按间隔执行", value: "interval" },
							{ label: "按 Cron 执行", value: "cron" },
						]}
					/>
				</Form.Item>
				{normalizedScheduleType === "interval" ? (
					<Form.Item
						name="scheduleIntervalMinutes"
						label="执行间隔(分钟)"
						rules={[{ required: true, message: "请输入执行间隔" }]}
					>
						<InputNumber min={1} precision={0} className="w-full" />
					</Form.Item>
				) : null}
				{normalizedScheduleType === "cron" ? (
					<Form.Item
						name="scheduleCron"
						label="Cron 表达式"
						rules={[{ validator: validateCronExpression }]}
					>
						<Input placeholder="例如：0 */30 * * * *" />
					</Form.Item>
				) : null}
			</div>
			<Space size={[8, 8]} wrap className="mb-3">
				<Text type="secondary">当前连接器能力：</Text>
				{["FULL", "INCREMENTAL", "CDC", "BACKFILL"].map((cap) => (
					<Tag key={cap} color={activeCapabilitySet.has(cap) ? "green" : "default"}>
						{cap}
					</Tag>
				))}
				{capabilityLoadFailed ? <Text type="warning">能力探测失败，已使用保守降级策略</Text> : null}
			</Space>

			<OdsLandingContractCard
				sourceKind="file"
				columnPrefix={form.getFieldValue("columnPrefix")}
				columnSuffix={form.getFieldValue("columnSuffix")}
				extraColumns={extraColumns}
			/>

			{/* ─── [Collapse] 运行治理策略 ─── */}
			<Collapse
				size="small"
				className="mb-4"
				items={[
					{
						key: "governance-file",
						label: "运行治理策略（可选）",
						children: (
							<div className="grid gap-4 md:grid-cols-3">
								<Form.Item name="taskConcurrency" label="任务并发上限">
									<InputNumber min={1} precision={0} className="w-full" placeholder="默认 1" />
								</Form.Item>
								<Form.Item name="sourceConcurrency" label="来源并发上限">
									<InputNumber min={0} precision={0} className="w-full" placeholder="0 表示不限" />
								</Form.Item>
								<Form.Item name="projectConcurrency" label="项目并发上限">
									<InputNumber min={0} precision={0} className="w-full" placeholder="0 表示不限" />
								</Form.Item>
								<Form.Item name="projectKey" label="项目标识">
									<Input placeholder="例如 project:patent" />
								</Form.Item>
								<Form.Item name="priority" label="队列优先级">
									<Select
										allowClear
										options={[
											{ label: "HIGH", value: "HIGH" },
											{ label: "MEDIUM", value: "MEDIUM" },
											{ label: "LOW", value: "LOW" },
										]}
									/>
								</Form.Item>
								<Form.Item name="rejectPolicy" label="限流策略">
									<Select
										allowClear
										options={[
											{ label: "REJECT", value: "REJECT" },
											{ label: "QUEUE", value: "QUEUE" },
										]}
									/>
								</Form.Item>
								<Form.Item name="windowStart" label="执行窗口开始">
									<Input placeholder="HH:mm，例如 01:00" />
								</Form.Item>
								<Form.Item name="windowEnd" label="执行窗口结束">
									<Input placeholder="HH:mm，例如 06:00" />
								</Form.Item>
								<Form.Item name="windowTimezone" label="执行窗口时区">
									<Select
										allowClear
										options={[
											{ label: "Asia/Shanghai", value: "Asia/Shanghai" },
											{ label: "UTC", value: "UTC" },
										]}
									/>
								</Form.Item>
							</div>
						),
					},
				]}
			/>

			{/* ─── [Collapse] 数据湖连接覆盖 ─── */}
			<Collapse
				ghost
				className="mb-4"
				items={[
					{
						key: "lake-override",
						label: "数据湖连接覆盖（可选）",
						children: (
							<>
								<Text type="secondary" className="block mb-4">
									若默认数据湖凭据不可用，可在此处手动指定目标库连接信息。留空则使用默认数据湖配置。
								</Text>
								<div className="grid gap-4 md:grid-cols-2">
									<Form.Item name="writerUsername" label="用户名">
										<Input placeholder="数据库账号" />
									</Form.Item>
									<Form.Item name="writerPassword" label="密码">
										<Input.Password placeholder="数据库密码" />
									</Form.Item>
								</div>
								<Form.Item name="writerJdbcUrls" label="JDBC URL（可选覆盖）">
									<Input placeholder="jdbc:postgresql://host:5432/db" />
								</Form.Item>
								<Form.Item name="writerSchema" label="Schema（可选）">
									<Input placeholder="例如 public" />
								</Form.Item>
							</>
						),
					},
				]}
			/>
		</>
	);
}
