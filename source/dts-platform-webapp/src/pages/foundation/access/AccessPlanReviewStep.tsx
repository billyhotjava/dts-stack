import { Alert, Descriptions, Space, Tag, Typography } from "antd";
import type { ApiConnectionTestResultDTO, ManagedFileUploadResult } from "@/api/ingestion";
import { CLASSIFICATION_LABELS_ZH, normalizeClassification } from "@/utils/classification";
import { ACCESS_KIND_LABELS, type AccessKind, type AccessPlanFormValues } from "./accessPlan.types";

type Props = {
	kind: AccessKind;
	editing: boolean;
	values: AccessPlanFormValues;
	sourceName?: string;
	targetName?: string;
	fileUploadResult: ManagedFileUploadResult | null;
	apiPreview: ApiConnectionTestResultDTO | null;
};

const scheduleText = (values: AccessPlanFormValues) => {
	if (values.scheduleType === "cron") return values.scheduleCron || "Cron 未填写";
	if (values.scheduleType === "interval") return `每 ${values.scheduleIntervalMinutes || "-"} 分钟`;
	return "手动触发";
};

export function AccessPlanReviewStep({ kind, editing, values, sourceName, targetName, fileUploadResult, apiPreview }: Props) {
	const resource =
		kind === "database"
			? values.tableSelectionMode === "all"
				? "全部表"
				: `${values.selectedTables.length} 张表`
			: kind === "api"
				? `${values.apiMethod} ${values.apiResourcePath || "-"}`
				: fileUploadResult?.originalName || "尚未上传";
	const level = normalizeClassification(fileUploadResult?.classification, undefined);

	return (
		<div className="space-y-5">
			<div>
				<Typography.Title level={4}>确认接入计划</Typography.Title>
				<Typography.Text type="secondary">
					确认后创建或更新任务；执行结果和异常数据将在任务运行记录中呈现。
				</Typography.Text>
			</div>
			<Descriptions bordered size="small" column={{ xs: 1, sm: 1, md: 2 }}>
				<Descriptions.Item label="任务">{values.name}</Descriptions.Item>
				<Descriptions.Item label="方式">
					<Tag color="blue">{ACCESS_KIND_LABELS[kind]}</Tag>
				</Descriptions.Item>
				<Descriptions.Item label="来源">
					{kind === "file" ? fileUploadResult?.originalName || "-" : sourceName || "-"}
				</Descriptions.Item>
				<Descriptions.Item label="资源">{resource}</Descriptions.Item>
				<Descriptions.Item label="目标">{targetName || "平台默认数据湖"}</Descriptions.Item>
				<Descriptions.Item label="调度">{scheduleText(values)}</Descriptions.Item>
				<Descriptions.Item label="同步">
					{kind === "file" ? "全量导入" : values.syncMode === "incremental" ? "增量同步" : "全量同步"}
				</Descriptions.Item>
				<Descriptions.Item label="提交后">
					{kind === "file"
						? "保存草稿并等待密级准入"
						: editing
							? "保存为待准入草稿；当前生效版本继续运行"
							: values.runNow
								? "立即运行"
								: "仅保存任务"}
				</Descriptions.Item>
			</Descriptions>
			{kind === "file" ? (
				<Alert
					type={fileUploadResult?.classificationSeal ? "success" : "warning"}
					showIcon
					message={
						<Space>
							<span>文件密级封存</span>
							{level ? <Tag>{CLASSIFICATION_LABELS_ZH[level]}</Tag> : null}
						</Space>
					}
					description={
						fileUploadResult?.classificationSeal ? "封存证据已生成；任务保存后仍需完成准入。" : "尚未生成封存证据。"
					}
				/>
			) : null}
			{kind === "api" && apiPreview ? (
				<Alert
					type={apiPreview.connected ? "success" : "warning"}
					showIcon
					message={apiPreview.connected ? "API 小样本验证通过" : "API 小样本验证未通过"}
					description={apiPreview.connected ? "已完成受限小样本验证。" : "请检查托管连接与资源定义后重试。"}
				/>
			) : null}
		</div>
	);
}
