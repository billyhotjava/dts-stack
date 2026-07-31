import { Alert, Button, Card, Descriptions, Space, Tag, Typography } from "antd";
import type { IngestionExecutionDTO, IngestionTaskDTO } from "@/api/ingestion";
import { useRouter } from "@/routes/hooks";
import { inferAccessKind } from "./accessPlanPayload";

const { Text } = Typography;

export const qualityDatasetIdFromRef = (value?: string) => {
	const normalized = String(value || "").trim();
	const match = /^dataset:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i.exec(
		normalized,
	);
	return match?.[1];
};

const qualityRoute = (path: "/governance/rules" | "/governance/quality", datasetId?: string, runId?: string) => {
	const params = new URLSearchParams();
	if (datasetId) params.set("datasetId", datasetId);
	if (runId) params.set("runId", runId);
	const query = params.toString();
	return query ? `${path}?${query}` : path;
};

export function AccessQualityPanel({
	task,
	latestExecution,
}: {
	task: IngestionTaskDTO;
	latestExecution: IngestionExecutionDTO | null;
}) {
	const router = useRouter();
	const kind = inferAccessKind(task);
	const datasetId = qualityDatasetIdFromRef(task.qualityPolicyRef || latestExecution?.qualityPolicyRef);
	const runId = latestExecution?.qualityRunId;
	const stage = kind === "file" ? "发布前文件预检" : "同步批次写入后检查";

	return (
		<div style={{ display: "grid", gap: 16 }}>
			<Alert
				type="info"
				showIcon
				message="数据质量模块是规则唯一事实源"
				description="接入任务只冻结已发布的数据集规则绑定引用，并记录质量运行编号；规则、版本、SQL、阈值和失败明细仍在数据质量模块维护。"
			/>
			<Card title={kind === "file" ? "文件预检" : "异常数据与运行后检查"}>
				<Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
					<Descriptions.Item label="检查阶段">{stage}</Descriptions.Item>
					<Descriptions.Item label="绑定状态">
						<Tag color={datasetId ? "success" : "warning"}>{datasetId ? "已冻结" : "未绑定"}</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="质量数据集">{datasetId || "未记录"}</Descriptions.Item>
					<Descriptions.Item label="最近质量运行">{runId || "尚无运行"}</Descriptions.Item>
					{kind === "file" ? (
						<Descriptions.Item label="文件预检状态" span={2}>
							{task.preCheckStatus || "尚未执行"}
						</Descriptions.Item>
					) : null}
				</Descriptions>
				{datasetId ? (
					<Text type="secondary" style={{ display: "block", marginTop: 12 }}>
						当前 Revision 运行时按该数据集已发布的规则绑定触发检查，不在接入侧复制规则。
					</Text>
				) : (
					<Alert
						style={{ marginTop: 12 }}
						type="warning"
						showIcon
						message="尚未冻结权威质量绑定"
						description={
							kind === "file"
								? "当前只能确认文件安全、格式和解析门禁，不能宣称业务质量规则已通过。请先在数据质量模块为目标数据集发布规则绑定。"
								: "任务可以运行，但本 Revision 不会产生正式质量运行。请为目标数据集发布规则绑定后重新保存并准入 Revision。"
						}
					/>
				)}
				<Space wrap style={{ marginTop: 16 }}>
					<Button onClick={() => router.push(qualityRoute("/governance/rules", datasetId))}>查看数据质量规则</Button>
					<Button
						type="primary"
						disabled={!datasetId && !runId}
						onClick={() => router.push(qualityRoute("/governance/quality", datasetId, runId))}
					>
						查看质量运行
					</Button>
				</Space>
			</Card>
		</div>
	);
}

export function AccessStructureDriftPanel({ task }: { task: IngestionTaskDTO }) {
	const router = useRouter();
	const kind = inferAccessKind(task);
	return (
		<div style={{ display: "grid", gap: 16 }}>
			<Alert
				type="info"
				showIcon
				message="结构漂移由元数据域统一判定"
				description="接入详情提供同一工作上下文入口；结构快照、漂移事件、策略和处置工单仍以元数据管理为唯一事实源。"
			/>
			<Card title="当前结构上下文">
				<Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
					<Descriptions.Item label="接入方式">{kind === "database" ? "数据库" : kind === "api" ? "API" : "离线文件"}</Descriptions.Item>
					<Descriptions.Item label="源连接标识">{task.sourceDataSourceId || "文件制品，无数据库连接"}</Descriptions.Item>
					<Descriptions.Item label="当前 Revision">{task.revisionNumber ? `R${task.revisionNumber}` : "未版本化"}</Descriptions.Item>
					<Descriptions.Item label="漂移结果">未在接入任务内复制</Descriptions.Item>
				</Descriptions>
				<Space wrap style={{ marginTop: 16 }}>
					<Button type="primary" onClick={() => router.push("/catalog/metadata")}>查看结构漂移台账</Button>
					<Button onClick={() => router.push("/catalog/metadata-management")}>查看元数据资产</Button>
				</Space>
			</Card>
		</div>
	);
}
