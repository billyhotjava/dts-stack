import {
	Collapse,
	Form,
	Input,
	InputNumber,
	Radio,
	Select,
	Space,
	Tag,
	Typography,
} from "antd";
import type { IngestionFormContext } from "./types";

const { Text } = Typography;

type SyncModeValue = "full_refresh" | "incremental" | "cdc" | "backfill";

const normalizeText = (value?: string) => String(value || "").trim();

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

export type DbBasicStepProps = Pick<
	IngestionFormContext,
	| "form"
	| "editorMode"
	| "setEditorMode"
	| "connectorCapability"
	| "syncModeOptions"
	| "sourceCategory"
	| "setSourceCategory"
> & {
	activeCapabilitySet: Set<string>;
	supportsIncremental: boolean;
	supportsCdc: boolean;
	supportsBackfill: boolean;
	capabilityLoadFailed: boolean;
};

export function DbBasicStep({
	form,
	editorMode: _editorMode,
	setEditorMode: _setEditorMode,
	syncModeOptions,
	sourceCategory: _sourceCategory,
	setSourceCategory,
	activeCapabilitySet,
	supportsIncremental,
	supportsCdc,
	supportsBackfill,
	capabilityLoadFailed,
}: DbBasicStepProps) {
	const syncMode = Form.useWatch("syncMode", form);
	const scheduleType = Form.useWatch("scheduleType", form);

	return (
		<>
			<Form.Item name="editorMode" label="配置方式">
				<Radio.Group>
					<Radio.Button value="visual">可视化</Radio.Button>
					<Radio.Button value="json">JSON</Radio.Button>
				</Radio.Group>
			</Form.Item>
			<Form.Item
				name="name"
				label="任务名称"
				rules={[{ required: true, message: "请输入任务名称" }]}
			>
				<Input placeholder="例如：pg-lake-task1" />
			</Form.Item>
			<Form.Item name="description" label="任务描述">
				<Input.TextArea rows={2} placeholder="可选，说明任务用途" />
			</Form.Item>
			<Form.Item name="sourceSystem" label="源系统标识">
				<Input placeholder="可选，例如：erp、crm（用于绑定 DAG）" />
			</Form.Item>
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
				<Collapse
					size="small"
					className="mb-4"
					items={[
						{
							key: "governance-db",
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
				<Form.Item name="sourceCategory" label="数据来源">
					<Radio.Group onChange={(e) => setSourceCategory(e.target.value)}>
						<Radio.Button value="database">数据库</Radio.Button>
						<Radio.Button value="file">文件上传</Radio.Button>
					</Radio.Group>
				</Form.Item>

		</>
	);
}
