import { Alert, Button, Descriptions, Space, Tag } from "antd";
import { useRouter } from "@/routes/hooks";
import { statusAxisLabel, statusLabel } from "@/utils/customerDisplayLabels";

export function AssetDeliveryStatusPanel({ dataset }: { dataset: Record<string, any> }) {
	const router = useRouter();
	const modelRefs: any[] = Array.isArray(dataset.modelRefs) ? dataset.modelRefs : [];
	const eligibility = String(dataset.consumptionEligibility || "CONDITIONAL");
	const eligibilityLabel =
		eligibility === "ELIGIBLE" ? "可消费" : eligibility === "BLOCKED" ? "不可消费" : "有条件可消费";
	const servingStatus = String(dataset.servingSync?.status || "NOT_APPLICABLE");
	const analysisLabel = ({
		SYNCED: "分析准备完成",
		SYNC_FAILED: "分析准备失败",
		SYNC_PENDING: "分析准备中",
		NOT_APPLICABLE: "不适用",
	} as Record<string, string>)[servingStatus] || "暂无当前证据";
	const qualityStatus = String(dataset.qualityStatus || "UNKNOWN");
	const axisEntries = Object.entries(dataset.statusAxes || {});

	return (
		<Descriptions bordered column={2} size="small" title="交付与模型证据">
			<Descriptions.Item label="消费资格">
				<Tag color={eligibility === "ELIGIBLE" ? "green" : eligibility === "BLOCKED" ? "red" : "gold"}>
					{eligibilityLabel}
				</Tag>
			</Descriptions.Item>
			<Descriptions.Item label="分析准备">
				<Tag color={servingStatus === "SYNCED" ? "green" : servingStatus === "SYNC_FAILED" ? "red" : "default"}>
					{analysisLabel}
				</Tag>
			</Descriptions.Item>
			<Descriptions.Item label="质量状态">
				<Tag color={qualityStatus === "PASSED" ? "green" : qualityStatus === "FAILED" ? "red" : "default"}>
					{statusLabel(qualityStatus)}
				</Tag>
			</Descriptions.Item>
			<Descriptions.Item label="状态投影时间">
				{dataset.projectionUpdatedAt ? new Date(dataset.projectionUpdatedAt).toLocaleString() : "尚未登记"}
			</Descriptions.Item>
			<Descriptions.Item label="五轴状态" span={2}>
				{axisEntries.length ? (
					<Space size={[4, 4]} wrap>
						{axisEntries.map(([axis, value]) => (
							<Tag key={axis}>
								{statusAxisLabel(axis)}：{statusLabel(value)}
							</Tag>
						))}
					</Space>
				) : (
					<Tag>待登记</Tag>
				)}
			</Descriptions.Item>
			<Descriptions.Item label="模型证据" span={2}>
				{modelRefs.length ? (
					<Space size={[4, 4]} wrap>
						{modelRefs
							.filter((ref) => ref?.modelSpecId)
							.map((ref) => (
								<Button
									key={`${ref.tenantId || "default"}-${ref.modelSpecId}`}
									type="link"
									size="small"
									className="px-0"
									onClick={() =>
										router.push(
											`/data-modeling/dimensions/workbench?modelSpecId=${encodeURIComponent(ref.modelSpecId)}`,
										)
									}
								>
									模型 r{ref.modelRevision ?? "-"}
									{ref.serving ? "（当前服务）" : ""} →
								</Button>
							))}
					</Space>
				) : (
					"暂无关联模型"
				)}
			</Descriptions.Item>
		</Descriptions>
	);
}

export function AssetDeliveryEligibilityNotice({ dataset }: { dataset: Record<string, any> }) {
	const eligibility = String(dataset.consumptionEligibility || "CONDITIONAL");
	if (eligibility === "ELIGIBLE" || !Array.isArray(dataset.eligibilityReasons) || !dataset.eligibilityReasons.length) {
		return null;
	}
	return (
		<Alert
			type={eligibility === "BLOCKED" ? "error" : "warning"}
			showIcon
			message="消费资格说明"
			description={dataset.eligibilityReasons.join("；")}
		/>
	);
}
