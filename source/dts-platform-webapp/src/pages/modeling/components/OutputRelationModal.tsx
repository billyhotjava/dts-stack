import { Alert, Modal, Space, Typography } from "antd";
import type { DbtOutputRelation, SqlModel } from "../sqlModeling.types";

const { Text } = Typography;

export type OutputRelationModalProps = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
	submitting: boolean;
	loading: boolean;
	outputAction: "truncate" | "rebuild" | null;
	outputRelation: DbtOutputRelation | null;
	errorMessage?: string | null;
	activeModel: SqlModel | null;
};

export default function OutputRelationModal({
	open,
	onClose,
	onSubmit,
	submitting,
	loading,
	outputAction,
	outputRelation,
	errorMessage,
	activeModel,
}: OutputRelationModalProps) {
	return (
		<Modal
			open={open}
			title={outputAction === "rebuild" ? "重建产出表" : "清空产出表"}
			onCancel={() => {
				if (submitting) {
					return;
				}
				onClose();
			}}
			onOk={onSubmit}
			confirmLoading={submitting}
			okText={outputAction === "rebuild" ? "确认重建" : "确认清空"}
			okButtonProps={{
				danger: outputAction === "rebuild",
				disabled:
					loading ||
					!!errorMessage ||
					!activeModel?.id ||
					(outputAction === "truncate" && !!outputRelation?.exists && !outputRelation?.truncateAllowed),
			}}
		>
			<Space direction="vertical" size={12} className="w-full">
				{loading ? (
					<div className="py-6 text-center text-sm text-muted-foreground">正在分析当前模型产出 relation...</div>
				) : errorMessage ? (
					<Alert type="error" showIcon message={errorMessage} />
				) : outputRelation ? (
					<>
						<Alert
							type={outputAction === "rebuild" ? "warning" : "info"}
							showIcon
							message={outputRelation.message || (outputAction === "rebuild" ? "将通过 dbt --full-refresh 安全重建产出 relation" : "将清空当前模型产出表数据")}
						/>
						<div className="rounded-md border border-border bg-muted/30 p-3 text-sm">
							<div>
								<Text type="secondary">模型</Text>
								<div className="font-medium">{outputRelation.modelName || activeModel?.name || "-"}</div>
							</div>
							<div className="mt-2">
								<Text type="secondary">产出 relation</Text>
								<div className="font-mono">{outputRelation.qualifiedName || "-"}</div>
							</div>
							<div className="mt-2 grid grid-cols-2 gap-3">
								<div>
									<Text type="secondary">物化方式</Text>
									<div>{outputRelation.materialized || "-"}</div>
								</div>
								<div>
									<Text type="secondary">检测类型</Text>
									<div>{outputRelation.relationType || (outputRelation.checkSkipped ? "待任务检查" : outputRelation.exists ? "-" : "未生成")}</div>
								</div>
								<div>
									<Text type="secondary">当前状态</Text>
									<div>{outputRelation.checkSkipped ? "待后台任务检查" : outputRelation.exists ? "已存在" : "不存在"}</div>
								</div>
								<div>
									<Text type="secondary">下游引用</Text>
									<div>{outputRelation.downstreamRefCount ?? 0}</div>
								</div>
							</div>
							<div className="mt-2">
								<Text type="secondary">构建选择器</Text>
								<div className="font-mono">{outputRelation.selector || activeModel?.dagSelector || "-"}</div>
							</div>
						</div>
						{outputRelation.checkSkipped ? (
							<Alert
								type="warning"
								showIcon
								message={outputRelation.checkMessage || "当前操作未预先检查目标库 relation。"}
							/>
						) : null}
						{outputAction === "truncate" && outputRelation.exists && !outputRelation.truncateAllowed ? (
							<Alert
								type="error"
								showIcon
								message="当前产出 relation 为视图，不支持清空。请改用「重建产出表」。"
							/>
						) : null}
						{outputAction === "rebuild" ? (
							<Alert
								type="warning"
								showIcon
								message="重建将使用 dbt --full-refresh 安全地重建产出表，构建失败时不会丢失原有数据。"
							/>
						) : null}
					</>
				) : (
					<Alert type="error" showIcon message="未能加载当前模型的产出 relation 信息" />
				)}
			</Space>
		</Modal>
	);
}
