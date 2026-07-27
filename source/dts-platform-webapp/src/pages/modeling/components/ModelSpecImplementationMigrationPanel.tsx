import { Alert, Button, Descriptions, Space, Tag, Typography } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import {
	applyModelImplementationMigrations,
	previewModelImplementationMigrations,
	type ModelImplementationMigrationBatch,
} from "@/api/modelSpecApi";
import {
	canApplyImplementationMigration,
	implementationMigrationReason,
} from "../modelSpecCorrection";
import type { ModelSpecView } from "../modelSpecV2Contract";

const { Text } = Typography;

type Props = {
	model: ModelSpecView;
	canMaintain: boolean;
	onMigrated: () => void | Promise<void>;
};

const statusPresentation = (status: string) => {
	if (status === "ELIGIBLE") return { color: "processing", label: "可以迁移" };
	if (status === "CONFLICT") return { color: "warning", label: "需要人工确认" };
	if (status === "ORPHAN") return { color: "error", label: "历史引用失效" };
	return { color: "default", label: "无需迁移" };
};

const migrationErrorMessage = (error: unknown): string => {
	const response = (error as { response?: { status?: number; data?: { code?: string } } })?.response;
	if (response?.status === 409 && response.data?.code === "MODEL_IMPLEMENTATION_MIGRATION_PREVIEW_STALE") {
		return "预检结果已变化，请重新预检后再迁移。";
	}
	if (response?.status === 403) return "当前账号没有维护数据实现的权限。";
	return "历史实现信息暂时无法预检，已保存内容不会被清空，请稍后重试。";
};

export function ModelSpecImplementationMigrationPanel({ model, canMaintain, onMigrated }: Props) {
	const [batch, setBatch] = useState<ModelImplementationMigrationBatch | null>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState("");
	const requestRef = useRef(0);
	const shouldShow = model.compatibilityMode === "LEGACY_READONLY" || Boolean(model.implementationPolicy);
	const result = batch?.results[0] || null;
	const status = result ? statusPresentation(result.decision.status) : null;
	const canApply = canMaintain && canApplyImplementationMigration(batch);
	const targetSettings = useMemo<Array<[string, string]>>(
		() =>
			result
				? [
						["目标物理表", String(result.decision.targetSettings.targetPhysicalName || "未设置")],
						["装载方式", String(result.decision.targetSettings.loadStrategy || "未设置")],
						[
							"分区字段",
							Array.isArray(result.decision.targetSettings.partitionFields)
								? result.decision.targetSettings.partitionFields.join("、") || "无"
								: "无",
						],
					]
				: [],
		[result],
	);

	useEffect(
		() => () => {
			requestRef.current += 1;
		},
		[],
	);

	if (!shouldShow) return null;

	const preview = async () => {
		const requestId = ++requestRef.current;
		setLoading(true);
		setError("");
		try {
			const next = await previewModelImplementationMigrations([model.id]);
			if (requestId === requestRef.current) setBatch(next);
		} catch (failure) {
			if (requestId === requestRef.current) setError(migrationErrorMessage(failure));
		} finally {
			if (requestId === requestRef.current) setLoading(false);
		}
	};

	const apply = async () => {
		if (!batch || !canApply) return;
		const requestId = ++requestRef.current;
		setLoading(true);
		setError("");
		try {
			const next = await applyModelImplementationMigrations([model.id], batch.previewChecksum);
			if (requestId !== requestRef.current) return;
			setBatch(next);
			await onMigrated();
		} catch (failure) {
			if (requestId === requestRef.current) setError(migrationErrorMessage(failure));
		} finally {
			if (requestId === requestRef.current) setLoading(false);
		}
	};

	return (
		<Alert
			className="mb-4"
			type={result?.decision.status === "CONFLICT" || result?.decision.status === "ORPHAN" ? "warning" : "info"}
			showIcon
			message="实现信息待迁移"
			description={
				<Space direction="vertical" size="small" className="w-full">
					<Text>
						该模型保存过旧版物理产出设置。预检只读取历史记录；迁移只新增当前数据实现，不改写历史模型版本。
					</Text>
					{result && status ? (
						<>
							<Space wrap>
								<Tag color={status.color}>{status.label}</Tag>
								<Text>{implementationMigrationReason(result.decision.reasonCode)}</Text>
							</Space>
							{result.decision.status === "CONFLICT" ? (
								<Text type="secondary">
									历史模型版本 r{result.decision.modelRevision}；当前数据实现版本 i
									{result.decision.currentImplementationRevision || "—"}。已有数据实现优先，不会自动覆盖。
								</Text>
							) : null}
							{targetSettings.length > 0 ? (
									<Descriptions size="small" column={1}>
										{targetSettings.map(([label, value]) => (
											<Descriptions.Item key={label} label={label}>
												{value}
											</Descriptions.Item>
									))}
								</Descriptions>
							) : null}
						</>
					) : null}
					{error ? <Text type="danger">{error}</Text> : null}
					<Space wrap>
						<Button size="small" loading={loading} onClick={() => void preview()}>
							{batch ? "重新预检" : "预检历史实现"}
						</Button>
						{canApply ? (
							<Button size="small" type="primary" loading={loading} onClick={() => void apply()}>
								迁入数据实现
							</Button>
						) : null}
						{!canMaintain ? <Text type="secondary">需要计划维护权限才能执行迁移。</Text> : null}
					</Space>
				</Space>
			}
		/>
	);
}
