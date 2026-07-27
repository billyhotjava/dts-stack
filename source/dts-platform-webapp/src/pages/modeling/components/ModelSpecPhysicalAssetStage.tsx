import { Alert, Button, Card, Empty, List, Space, Spin, Tag, Timeline, Typography } from "antd";
import { useEffect, useState } from "react";
import type { ModelLifecycleArtifact, ModelLifecycleTimeline } from "@/api/modelSpecApi";
import type { CanonicalModelSpecView } from "../modelSpecV2Contract";

const { Text } = Typography;

type PhysicalArtifact = ModelLifecycleArtifact & { physicalAssetRef?: string | null };

type Props = {
	model: CanonicalModelSpecView;
	timeline: ModelLifecycleTimeline | null;
	loading: boolean;
	error: string;
	onRetry: () => void;
	onRetryReleaseRegistration: (releaseId: string) => Promise<void>;
};

const eventLabels: Record<string, string> = {
	COMPILE: "编译",
	TEST: "测试",
	REVIEW_SUBMITTED: "提交审核",
	REVIEW_APPROVED: "审核通过",
	RELEASE: "发布",
	ROLLBACK: "回滚",
	RUN: "运行",
};

export function ModelSpecPhysicalAssetStage({
	model,
	timeline,
	loading,
	error,
	onRetry,
	onRetryReleaseRegistration,
}: Props) {
	const [registrationRetrying, setRegistrationRetrying] = useState(false);
	const [registrationRetryError, setRegistrationRetryError] = useState("");
	const realArtifacts = ((timeline?.artifacts || []) as PhysicalArtifact[]).filter(
		(artifact) =>
			Boolean(artifact.physicalAssetRef) &&
			artifact.revision === model.revision &&
			artifact.modelChecksum === model.checksum &&
			artifact.implementationRevision === timeline?.implementation?.implementationRevision,
	);
	const latestRelease = [...(timeline?.events || [])]
		.filter(
			(event) =>
				event.eventType === "RELEASE" && event.revision === model.revision && event.modelChecksum === model.checksum,
		)
		.sort((left, right) => right.createdAt.localeCompare(left.createdAt))[0];
	const recoverableRelease =
		latestRelease && (latestRelease.status === "PARTIAL" || latestRelease.status === "PENDING")
			? latestRelease
			: undefined;

	useEffect(() => {
		setRegistrationRetrying(false);
		setRegistrationRetryError("");
	}, [model.id, model.revision]);

	const retryRegistration = async () => {
		if (!recoverableRelease || registrationRetrying) return;
		setRegistrationRetrying(true);
		setRegistrationRetryError("");
		try {
			await onRetryReleaseRegistration(recoverableRelease.id);
		} catch {
			setRegistrationRetryError("发布登记尚未恢复，请保留当前页面并稍后重试。");
		} finally {
			setRegistrationRetrying(false);
		}
	};
	return (
		<div className="min-w-0" data-testid="model-spec-physical-stage">
			<Alert
				className="mb-4"
				type="info"
				showIcon
				message="发布结果与运行证据"
				description="此阶段只展示真实目标资产、DDL/构建产物、编译测试发布证据及血缘时间线；临时 STG 不作为物理表登记。"
			/>
			{error ? (
				<Alert
					className="mb-4"
					type="warning"
					showIcon
					message={error}
					action={
						<Button size="small" onClick={onRetry}>
							重试加载
						</Button>
					}
				/>
			) : null}
			{recoverableRelease ? (
				<Alert
					className="mb-4"
					type="warning"
					showIcon
					message={
						recoverableRelease.status === "PARTIAL" ? "发布已完成，但部分资产登记尚未完成" : "发布登记仍在等待完成"
					}
					description="重试后页面会重新读取服务端生命周期，不会用本地状态推测发布结果。"
					action={
						<Button size="small" type="primary" loading={registrationRetrying} onClick={() => void retryRegistration()}>
							重试发布登记
						</Button>
					}
				/>
			) : null}
			{registrationRetryError ? (
				<Alert className="mb-4" type="error" showIcon message={registrationRetryError} />
			) : null}
			<Card className="mb-4" size="small" title="目标物理对象">
				<Space wrap>
					<Tag color="blue">{model.layer}</Tag>
					<Text>{model.name}</Text>
					<Text type="secondary">目标节点由已验证的数据实现生成，不在此页手工编辑 DDL。</Text>
				</Space>
			</Card>
			{loading && !timeline ? (
				<div className="flex min-h-40 items-center justify-center">
					<Spin tip="正在读取物理资产" />
				</div>
			) : null}
			<Card className="mb-4" size="small" title="真实物理资产与 DDL / 构建产物">
				{realArtifacts.length ? (
					<List
						size="small"
						dataSource={realArtifacts}
						renderItem={(artifact) => (
							<List.Item>
								<Space direction="vertical" size={1}>
									<Space wrap>
										<Text strong>{artifact.path}</Text>
										<Tag>{artifact.artifactType}</Tag>
										<Tag color={artifact.status === "READY" ? "success" : "default"}>{artifact.status}</Tag>
									</Space>
									<Text type="secondary">
										物理资产 {artifact.physicalAssetRef} · 校验 {artifact.checksum}
									</Text>
								</Space>
							</List.Item>
						)}
					/>
				) : (
					<Empty
						image={Empty.PRESENTED_IMAGE_SIMPLE}
						description="尚无已登记的真实物理资产；完成编译、测试和发布后将在这里显示。"
					/>
				)}
			</Card>
			<Card size="small" title="编译、测试、部署与血缘时间线">
				{timeline?.events.length ? (
					<Timeline
						items={timeline.events.map((event) => ({
							color: event.status === "PASSED" || event.status === "PUBLISHED" ? "green" : undefined,
							children: (
								<Space direction="vertical" size={0}>
									<Text>
										{eventLabels[event.eventType] || event.eventType} · {event.status}
									</Text>
									<Text type="secondary">
										{event.createdAt}
										{event.externalRef ? ` · ${event.externalRef}` : ""}
									</Text>
								</Space>
							),
						}))}
					/>
				) : (
					<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无编译、测试、部署或血缘运行证据" />
				)}
			</Card>
		</div>
	);
}
