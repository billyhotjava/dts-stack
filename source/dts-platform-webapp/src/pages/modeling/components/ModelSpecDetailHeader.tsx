import { Button, Space, Tag, Typography } from "antd";
import { ArrowLeft } from "lucide-react";
import type { ModelSpecDetailPrimaryAction } from "../modelSpecDetailStageProjection";
import type { CanonicalModelSpecView, ModelSpecView } from "../modelSpecV2Contract";
import { MODEL_STATUS_LABELS, MODEL_TYPE_LABELS } from "../modelSpecWorkbench";
import { ModelSpecPrimaryActionButton, type ModelSpecPrimaryActionContext } from "./ModelSpecPrimaryActionButton";
import { ModelSpecReclassificationWizard } from "./ModelSpecReclassificationWizard";

const { Text, Title } = Typography;

type Props = {
	model: ModelSpecView;
	canonicalModel: CanonicalModelSpecView | null;
	canEdit: boolean;
	primaryAction?: ModelSpecDetailPrimaryAction;
	primaryActionContext: ModelSpecPrimaryActionContext;
	onBack: () => void;
	onReload: () => Promise<void>;
};

export function ModelSpecDetailHeader({
	model,
	canonicalModel,
	canEdit,
	primaryAction,
	primaryActionContext,
	onBack,
	onReload,
}: Props) {
	return (
		<header className="mb-4 flex flex-wrap items-start justify-between gap-3">
			<div>
				<Button type="link" className="!px-0" onClick={onBack}>
					<ArrowLeft size={15} />
					{model.modelType === "DIMENSION" ? "返回维度目录" : "返回模型中心"}
				</Button>
				<Title level={3} className="!mb-1 !mt-1">
					{model.name}
				</Title>
				<Space wrap>
					<Tag color="blue">{MODEL_TYPE_LABELS[model.modelType]}</Tag>
					<Tag>{model.layer}</Tag>
					<Tag>
						{model.compatibilityMode === "LEGACY_READONLY"
							? "兼容只读"
							: MODEL_STATUS_LABELS[model.status] || model.status}
					</Tag>
					<Text type="secondary">版本 r{model.revision}</Text>
				</Space>
			</div>
			{primaryAction ? (
				<Space wrap>
					{canonicalModel?.status === "DRAFT" ? (
						<ModelSpecReclassificationWizard model={canonicalModel} canMaintain={canEdit} onApplied={onReload} />
					) : null}
					<ModelSpecPrimaryActionButton action={primaryAction} {...primaryActionContext} />
				</Space>
			) : null}
		</header>
	);
}
