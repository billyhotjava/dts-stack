import { Alert, Button, Space } from "antd";
import { RefreshCw } from "lucide-react";
import type { CanonicalModelSpecView, ModelSpecRevisionConflictDetails, ModelSpecView } from "../modelSpecV2Contract";
import { MODEL_STATUS_LABELS } from "../modelSpecWorkbench";
import { ModelSpecImplementationMigrationPanel } from "./ModelSpecImplementationMigrationPanel";

type Props = {
	model: ModelSpecView;
	canonicalModel: CanonicalModelSpecView | null;
	roleAllowsEdit: boolean;
	writeDenied: boolean;
	statusAllowsEdit: boolean;
	statusChanged: boolean;
	dependencyContractMismatch: boolean;
	referenceResolutionFailed: boolean;
	saveError: string;
	conflict: ModelSpecRevisionConflictDetails | null;
	onReload: () => Promise<void>;
	onRetryConflict: () => void;
	onDiscardAndReload: () => void;
};

export function ModelSpecDetailNotices({
	model,
	canonicalModel,
	roleAllowsEdit,
	writeDenied,
	statusAllowsEdit,
	statusChanged,
	dependencyContractMismatch,
	referenceResolutionFailed,
	saveError,
	conflict,
	onReload,
	onRetryConflict,
	onDiscardAndReload,
}: Props) {
	return (
		<>
			{model.compatibilityMode === "LEGACY_READONLY" ? (
				<>
					<Alert
						className="mb-3"
						type="warning"
						showIcon
						message="这是迁移期历史模型，可浏览但不能在 canonical 页面修改"
					/>
					<ModelSpecImplementationMigrationPanel
						model={model}
						canMaintain={roleAllowsEdit && !writeDenied}
						onMigrated={() => void onReload()}
					/>
				</>
			) : null}
			{dependencyContractMismatch ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message="历史模型的类别、目标分层、类型专属字段或输入依赖不符合当前四类表规则，仅支持查看"
					description="请通过迁移任务重新登记为 DWD 维度/明细、DWS 汇总或 ADS 应用模型；系统不会静默改写历史 revision。"
				/>
			) : null}
			{referenceResolutionFailed ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message="暂时无法核验已锁定上游版本，页面已只读，请重试"
					description="已保存内容与版本锁定关系均已保留；重新核验成功前不会把临时读取失败误判为历史模型迁移问题。"
					action={
						<Button size="small" icon={<RefreshCw size={14} />} onClick={() => void onReload()}>
							重新核验
						</Button>
					}
				/>
			) : null}
			{!roleAllowsEdit || writeDenied ? (
				<Alert className="mb-3" type="info" showIcon message="当前账号为只读浏览；编辑需要计划维护权限" />
			) : null}
			{statusChanged ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message="模型状态已变化，当前输入已保留；请加载最新状态后继续"
					action={
						<Button size="small" onClick={() => void onReload()}>
							加载最新状态
						</Button>
					}
				/>
			) : null}
			{canonicalModel && !statusAllowsEdit ? (
				<Alert
					className="mb-3"
					type="info"
					showIcon
					message={`当前状态为${MODEL_STATUS_LABELS[canonicalModel.status] || canonicalModel.status}；只有草稿可编辑`}
				/>
			) : null}
			{saveError ? <Alert className="mb-3" type="error" showIcon message={saveError} /> : null}
			{conflict ? (
				<Alert
					className="mb-3"
					type="warning"
					showIcon
					message={`服务器当前为 r${conflict.currentRevision}，你的输入仍保留在页面中`}
					action={
						<Space wrap>
							<Button size="small" onClick={onRetryConflict}>
								保留当前输入并基于 r{conflict.currentRevision} 重试
							</Button>
							<Button size="small" onClick={onDiscardAndReload}>
								放弃当前输入，加载最新版本
							</Button>
						</Space>
					}
				/>
			) : null}
		</>
	);
}
