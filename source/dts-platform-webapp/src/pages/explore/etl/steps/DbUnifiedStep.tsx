import { useState } from "react";
import {
	Alert,
	Button,
	Card,
	Collapse,
	Divider,
	Form,
	Input,
	InputNumber,
	Radio,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { TableInfo } from "@/api/ingestion";
import type { IngestionFormContext } from "./types";
import { normalizeText } from "@/utils/textUtils";

const { Text } = Typography;

/* ── helpers copied from DbBasicStep / DbSourceStep ── */

type SyncModeValue = "full_refresh" | "incremental" | "cdc" | "backfill";

const normalizeSyncModeValue = (value?: string): SyncModeValue | null => {
	const text = normalizeText(value).toLowerCase();
	if (!text) return null;
	if (text === "full" || text === "full_refresh" || text === "fullrefresh") return "full_refresh";
	if (text === "incremental" || text === "incr" || text === "delta") return "incremental";
	if (text === "cdc" || text === "realtime" || text === "real_time") return "cdc";
	if (text === "backfill" || text === "history_backfill" || text === "historical_backfill") return "backfill";
	return null;
};

const validateCronExpression = (_: unknown, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	const parts = normalizeText(value).split(/\s+/);
	if (parts.length < 5 || parts.length > 6) {
		return Promise.reject(new Error("Cron 表达式应包含 5~6 个部分"));
	}
	return Promise.resolve();
};

const jsonValidator = (label: string) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error(`${label} JSON 格式错误`));
	}
};

const buildTableKey = (table: TableInfo) =>
	normalizeText(table.schema) ? `${table.schema}.${table.name}` : table.name;

/* ── props ── */

export type DbUnifiedStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "editorMode"
	| "setEditorMode"
	| "connectorCapability"
	| "syncModeOptions"
	| "sourceCategory"
	| "setSourceCategory"
	| "selectedTableKeys"
	| "setSelectedTableKeys"
	| "selectedDataSource"
	| "dataSources"
> & {
	activeCapabilitySet: Set<string>;
	supportsIncremental: boolean;
	supportsCdc: boolean;
	supportsBackfill: boolean;
	capabilityLoadFailed: boolean;
	availableTables: TableInfo[];
	loadingTables: boolean;
	discoveredTableKeys: string[];
	discoverError: string;
	loadingDataSources: boolean;
	onDiscoverTables: () => void;
	onApplyTables: () => void;
	syncSelectedTablesToForm: (tables: string[], opts?: { silent?: boolean }) => void;
	readerTablesValidator: (_: any, value: string) => Promise<void>;
	readerTypeValidator: (_: any, value: string) => Promise<void>;
	deptOptions: { label: string; value: string }[];
	loadingDeptOptions: boolean;
};

/* ── component ── */

export function DbUnifiedStep({
	form,
	editorMode,
	setEditorMode: _setEditorMode,
	syncModeOptions,
	sourceCategory: _sourceCategory,
	setSourceCategory,
	selectedTableKeys,
	setSelectedTableKeys,
	selectedDataSource: _selectedDataSource,
	dataSources,
	activeCapabilitySet,
	supportsIncremental,
	supportsCdc,
	supportsBackfill,
	capabilityLoadFailed,
	availableTables,
	loadingTables,
	discoveredTableKeys,
	discoverError,
	loadingDataSources,
	onDiscoverTables,
	onApplyTables,
	syncSelectedTablesToForm,
	readerTablesValidator,
	readerTypeValidator,
	deptOptions,
	loadingDeptOptions,
}: DbUnifiedStepProps) {
	const syncMode = Form.useWatch("syncMode", form);
	const scheduleType = Form.useWatch("scheduleType", form);
	const tableSelectionMode = Form.useWatch("tableSelectionMode", form);
	const [tablePageSize, setTablePageSize] = useState(8);

	return (
		<>
			{/* ─── 数据来源切换 ─── */}
			<Divider orientation="left">数据来源切换</Divider>
			<Form.Item name="sourceCategory" label="数据来源">
				<Radio.Group onChange={(e) => setSourceCategory(e.target.value)}>
					<Radio.Button value="database">数据库</Radio.Button>
					<Radio.Button value="file">文件上传</Radio.Button>
				</Radio.Group>
			</Form.Item>

			{/* ─── 基础信息 ─── */}
			<Divider orientation="left">基础信息</Divider>
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item
					name="name"
					label="任务名称"
					rules={[{ required: true, message: "请输入任务名称" }]}
				>
					<Input placeholder="例如：pg-lake-task1" />
				</Form.Item>
				<Form.Item
					name="ownerDept"
					label="归属部门"
					rules={[{ required: true, message: "请选择归属部门" }]}
				>
					<Select
						loading={loadingDeptOptions}
						placeholder={loadingDeptOptions ? "加载中..." : "请选择归属部门"}
						options={deptOptions}
						showSearch
						filterOption={(input, option) =>
							(option?.label ?? "").toLowerCase().includes(input.toLowerCase())
						}
					/>
				</Form.Item>
			</div>
			<Form.Item name="description" label="描述">
				<Input.TextArea rows={2} placeholder="可选，说明任务用途" />
			</Form.Item>

			{/* ─── 数据源 ─── */}
			<Divider orientation="left">数据源</Divider>
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item
					name="sourceDataSourceId"
					label="数据源连接"
					rules={[{ required: true, message: "请选择数据源连接" }]}
				>
					<Select
						loading={loadingDataSources}
						placeholder={loadingDataSources ? "加载中..." : "请选择数据源连接"}
						options={dataSources.map((item) => ({
							label: `${item.name} (${item.type || "unknown"})`,
							value: item.id,
						}))}
						showSearch
						optionFilterProp="label"
					/>
				</Form.Item>
				<Form.Item
					name="readerType"
					label="Reader 类型"
					rules={[{ validator: readerTypeValidator }]}
				>
					<Input placeholder="将根据数据源自动生成" disabled />
				</Form.Item>
			</div>
			<Form.Item name="tableSelectionMode" label="入湖表选择">
				<Radio.Group
					onChange={(e) => {
						const next = normalizeText(e.target?.value) || "all";
						if (next === "all") {
							syncSelectedTablesToForm([], { silent: true });
						}
					}}
				>
					<Radio.Button value="all">全部表（默认）</Radio.Button>
					<Radio.Button value="manual">手动选择</Radio.Button>
				</Radio.Group>
			</Form.Item>
			{tableSelectionMode === "all" ? (
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="tableExclude" label="排除表（每行一个，可选）">
						<Input.TextArea rows={2} placeholder="schema.table 或 table_name" />
					</Form.Item>
				</div>
			) : null}
			{editorMode === "json" ? (
				<Form.Item
					name="readerConfig"
					label="Reader 配置 (JSON)"
					rules={[
						{ required: true, message: "请输入 Reader 配置" },
						{ validator: jsonValidator("Reader 配置") },
					]}
				>
					<Input.TextArea rows={6} placeholder='{"column":["*"],"table":["table_a"]}' />
				</Form.Item>
			) : (
				<>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item
							name="readerTables"
							label="Reader 表（每行一个）"
							dependencies={["tableSelectionMode"]}
							rules={[{ validator: readerTablesValidator }]}
						>
							<Input.TextArea rows={3} placeholder="source_table" />
						</Form.Item>
						<Form.Item name="readerColumns" label="Reader 字段（逗号分隔）">
							<Input placeholder="* 或 id,name,created_at" />
						</Form.Item>
					</div>
					<div className="grid gap-4 md:grid-cols-2">
						<Form.Item name="readerWhere" label="Reader 过滤条件">
							<Input placeholder="可选，例如：status = 1" />
						</Form.Item>
					</div>
					<Collapse
						ghost
						items={[
							{
								key: "reader-advanced",
								label: "Reader 高级参数",
								children: (
									<div className="space-y-4">
										<Form.Item name="readerQuerySql" label="Reader 查询 SQL（每行一条）">
											<Input.TextArea rows={3} placeholder="select * from t where ..." />
										</Form.Item>
										<Form.Item name="readerExtraConfig" label="Reader 扩展配置 JSON">
											<Input.TextArea rows={4} placeholder='{"splitPk":"id"}' />
										</Form.Item>
									</div>
								),
							},
						]}
					/>
				</>
			)}
			<div className="grid gap-4 md:grid-cols-2">
				<Form.Item name="syncPrefix" label="目标表前缀" extra="用于自动生成 ODS 表名（如：ods_erp_ + 源表名）。若 Writer 已指定目标表，可留空。">
					<Input placeholder="从数据源自动推算，可手动修改" />
				</Form.Item>
			</div>

			{/* ─── 源端表发现 ─── */}
			<Divider orientation="left">源端表发现</Divider>
			<Card type="inner">
				<div className="grid gap-4 md:grid-cols-3">
					<Form.Item name="readerSchema" label="Schema（可选）">
						<Input placeholder="例如 public" />
					</Form.Item>
					<Form.Item name="readerTablePattern" label="表名筛选（可选）">
						<Input placeholder="支持 SQL LIKE，例如 ods_%" />
					</Form.Item>
					<Form.Item label="操作">
						<Space>
							<Button onClick={onDiscoverTables} loading={loadingTables}>
								获取表清单
							</Button>
							<Button
								onClick={() => {
									setSelectedTableKeys(discoveredTableKeys);
									syncSelectedTablesToForm(discoveredTableKeys, { silent: true });
								}}
								disabled={!discoveredTableKeys.length}
							>
								全选
							</Button>
							<Button
								onClick={() => {
									setSelectedTableKeys([]);
									syncSelectedTablesToForm([], { silent: true });
								}}
								disabled={!selectedTableKeys.length}
							>
								清空
							</Button>
							<Button onClick={onApplyTables} disabled={!selectedTableKeys.length}>
								应用选择
							</Button>
						</Space>
					</Form.Item>
				</div>
				{discoverError ? (
					<Alert type="warning" message={discoverError} showIcon className="mb-3" />
				) : null}
				<Table
					rowKey={(record) => buildTableKey(record)}
					size="small"
					loading={loadingTables}
					dataSource={availableTables}
					rowSelection={{
						selectedRowKeys: selectedTableKeys,
						onChange: (keys) => {
							const nextKeys = keys.map((key) => String(key));
							syncSelectedTablesToForm(nextKeys, { silent: true });
						},
					}}
					columns={[
						{ title: "Schema", dataIndex: "schema", width: 140 },
						{ title: "表名", dataIndex: "name" },
						{ title: "类型", dataIndex: "type", width: 120 },
					]}
					pagination={{
						pageSize: tablePageSize,
						showSizeChanger: true,
						pageSizeOptions: [8, 20, 50, 100],
						onShowSizeChange: (_current: number, size: number) => setTablePageSize(size),
					}}
				/>
				<Text type="secondary" className="block mt-2">
					已发现 {availableTables.length} 张表，已选择 {selectedTableKeys.length} 张表
				</Text>
			</Card>

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
				{(normalizeText(scheduleType) || "manual") === "interval" ? (
					<Form.Item
						name="scheduleIntervalMinutes"
						label="执行间隔(分钟)"
						rules={[{ required: true, message: "请输入执行间隔" }]}
					>
						<InputNumber min={1} precision={0} className="w-full" />
					</Form.Item>
				) : null}
				{(normalizeText(scheduleType) || "manual") === "cron" ? (
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
				{!supportsIncremental ? <Text type="warning">当前连接器不支持增量</Text> : null}
				{supportsCdc ? <Text type="secondary">已支持 CDC 模式</Text> : null}
				{supportsBackfill ? <Text type="secondary">已支持历史回灌模式</Text> : null}
				{capabilityLoadFailed ? <Text type="warning">能力探测失败，已使用保守降级策略</Text> : null}
			</Space>
			{(normalizeSyncModeValue(syncMode) || "full_refresh") === "incremental" ? (
				<div className="grid gap-4 md:grid-cols-3">
					<Form.Item
						name="incrementalColumn"
						label="增量列"
						rules={[{ required: true, message: "请输入增量列名" }]}
					>
						<Input placeholder="例如 updated_at 或 id" />
					</Form.Item>
					<Form.Item name="incrementalType" label="增量类型">
						<Select
							options={[
								{ label: "datetime", value: "datetime" },
								{ label: "number", value: "number" },
								{ label: "string", value: "string" },
							]}
						/>
					</Form.Item>
					<Form.Item name="initialWatermark" label="初始水位（可选）">
						<Input placeholder="首次运行起点，如 2025-01-01 00:00:00" />
					</Form.Item>
				</div>
			) : null}

			{/* ─── 运行治理策略 ─── */}
			<Collapse
				size="small"
				className="mb-4"
				items={[
					{
						key: "governance-unified",
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

		</>
	);
}
