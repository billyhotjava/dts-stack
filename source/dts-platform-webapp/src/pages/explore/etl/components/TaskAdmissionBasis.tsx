import { Alert, Descriptions, Tag, Typography } from "antd";
import type { IngestionTaskDTO } from "@/api/ingestion";
import { CLASSIFICATION_LABELS_ZH, type ClassificationLevel } from "@/utils/classification";
import { resolveTaskAdmissionBasis } from "../taskAdmissionBasis.helpers";

const { Text } = Typography;

const CLASSIFICATION_COLORS: Record<ClassificationLevel, string> = {
	PUBLIC: "green",
	INTERNAL: "blue",
	SECRET: "orange",
	CONFIDENTIAL: "red",
};

type TaskAdmissionBasisProps = {
	task: IngestionTaskDTO;
};

const classificationTag = (level?: ClassificationLevel) =>
	level ? (
		<Tag color={CLASSIFICATION_COLORS[level]}>
			{CLASSIFICATION_LABELS_ZH[level]}（{level}）
		</Tag>
	) : (
		<Text type="danger">未提供</Text>
	);

export default function TaskAdmissionBasis({ task }: TaskAdmissionBasisProps) {
	const basis = resolveTaskAdmissionBasis(task);
	const admissionColor = basis.status === "admitted" ? "success" : basis.status === "pending" ? "gold" : "error";
	const evidenceColor =
		basis.evidenceStatus === "complete" ? "success" : basis.evidenceStatus === "unverifiable" ? "warning" : "error";
	const alertType =
		basis.status === "blocked" || basis.evidenceStatus === "incomplete"
			? "error"
			: basis.evidenceStatus === "unverifiable"
				? "warning"
				: basis.status === "admitted"
					? "success"
					: "info";

	return (
		<div className="space-y-3" data-testid="platform-transform-admission-basis">
			<Alert
				showIcon
				type={alertType}
				message={`准入依据：${basis.evidenceStatusLabel}`}
				description={`${basis.evidenceReason}；流程判定：${basis.statusReason}。当前结果来自任务密级封存，不会修改源表或目标表结构。`}
			/>
			<Descriptions size="small" bordered column={{ xs: 1, sm: 2, lg: 4 }}>
				<Descriptions.Item label="准入状态">
					<Tag color={admissionColor}>{basis.statusLabel}</Tag>
				</Descriptions.Item>
				<Descriptions.Item label="有效密级">{classificationTag(basis.effectiveLevel)}</Descriptions.Item>
				<Descriptions.Item label="密级来源">{basis.sourceLabel}</Descriptions.Item>
				<Descriptions.Item label="依据完整性">
					<Tag color={evidenceColor}>{basis.evidenceStatusLabel}</Tag>
				</Descriptions.Item>
				<Descriptions.Item label="字段覆盖">{basis.fieldCoverageLabel}</Descriptions.Item>
				<Descriptions.Item label="最高字段密级">
					{basis.highestFieldLevel ? classificationTag(basis.highestFieldLevel) : "未单独配置"}
				</Descriptions.Item>
				<Descriptions.Item label="封存版本">
					{basis.snapshotVersion === undefined ? "-" : `v${basis.snapshotVersion}`}
				</Descriptions.Item>
				<Descriptions.Item label="封存时间">
					{basis.sealedAt ? new Date(basis.sealedAt).toLocaleString("zh-CN") : "-"}
				</Descriptions.Item>
			</Descriptions>
		</div>
	);
}
