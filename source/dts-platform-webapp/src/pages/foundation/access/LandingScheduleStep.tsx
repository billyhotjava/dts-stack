import { Alert, Form, Input, InputNumber, Select, Space, Switch, Tag, Typography } from "antd";
import type { FormInstance } from "antd/es/form";
import type { DefaultDestinationStatus } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import type { AccessKind, AccessPlanFormValues } from "./accessPlan.types";

type Props = {
	form: FormInstance<AccessPlanFormValues>;
	kind: AccessKind;
	editing: boolean;
	targetDataSources: InfraDataSource[];
	defaultDestination: DefaultDestinationStatus | null;
};

export function LandingScheduleStep({ form, kind, editing, targetDataSources, defaultDestination }: Props) {
	const scheduleType = Form.useWatch("scheduleType", form) || "manual";
	const syncMode = Form.useWatch("syncMode", form) || "full_refresh";
	const available = Boolean(
		defaultDestination?.available && defaultDestination.writerTypeReady && defaultDestination.writerConfigReady,
	);

	return (
		<div className="space-y-5">
			<div>
				<Typography.Title level={4}>策略与准入</Typography.Title>
				<Typography.Text type="secondary">目标端使用平台托管配置；页面不展示数据库凭据。</Typography.Text>
			</div>
			<Alert
				showIcon
				type={available ? "success" : "warning"}
				message={available ? "平台默认数据湖可用" : "平台默认数据湖不可用"}
				description={[defaultDestination?.destinationName, defaultDestination?.writerType, defaultDestination?.message]
					.filter(Boolean)
					.join(" · ")}
			/>
			<Form.Item name="targetDataSourceId" label="目标数据源" rules={[{ required: true, message: "请选择目标数据源" }]}>
				<Select
					showSearch
					optionFilterProp="label"
					options={targetDataSources.map((item) => ({
						label: `${item.name}${item.recommended ? " · 推荐" : ""} · ${item.type}`,
						value: item.id,
					}))}
				/>
			</Form.Item>
			{kind === "file" ? (
				<Space>
					<Typography.Text>同步模式</Typography.Text>
					<Tag color="blue">全量导入</Tag>
				</Space>
			) : (
				<Form.Item name="syncMode" label="同步模式">
					<Select
						options={[
							{ label: "全量同步", value: "full_refresh" },
							{ label: "增量同步", value: "incremental" },
						]}
					/>
				</Form.Item>
			)}
			{kind !== "file" && syncMode === "incremental" ? (
				<div className="grid gap-4 md:grid-cols-3">
					<Form.Item name="incrementalColumn" label="增量列" rules={[{ required: true, message: "请输入增量列" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="incrementalType" label="增量类型">
						<Select options={["datetime", "number", "string"].map((value) => ({ label: value, value }))} />
					</Form.Item>
					<Form.Item name="initialWatermark" label="初始水位">
						<Input />
					</Form.Item>
				</div>
			) : null}
			<Form.Item name="scheduleType" label="调度策略">
				<Select
					options={[
						{ label: "手动触发", value: "manual" },
						{ label: "固定间隔", value: "interval" },
						{ label: "Cron", value: "cron" },
					]}
				/>
			</Form.Item>
			{scheduleType === "interval" ? (
				<Form.Item name="scheduleIntervalMinutes" label="间隔分钟">
					<InputNumber min={1} className="w-full" />
				</Form.Item>
			) : null}
			{scheduleType === "cron" ? (
				<Form.Item name="scheduleCron" label="Cron 表达式" rules={[{ required: true }]}>
					<Input placeholder="0 0 3 * * *" />
				</Form.Item>
			) : null}
			<div>
				<Form.Item name="airflowEnabled" label="启用调度编排" valuePropName="checked">
					<Switch />
				</Form.Item>
			</div>
			<Alert
				type="info"
				showIcon
				message="接入计划先保存为待准入草稿"
				description={
					editing
						? "保存修改后，当前生效版本继续运行；新草稿完成密级准入后才会生效。"
						: "数据库、API 和离线文件任务均需先完成密级准入，之后才能手动执行或按调度运行。"
				}
			/>
		</div>
	);
}
