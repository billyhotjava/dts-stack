import { Button } from "antd";
import type { ModelSpecDetailPrimaryAction } from "../modelSpecDetailStageProjection";

export type ModelSpecPrimaryActionContext = {
	saving: boolean;
	advancedImplementationReady: boolean;
	implementationRecoveryMessage: string;
	onAction: () => Promise<void>;
};

type Props = ModelSpecPrimaryActionContext & {
	action: ModelSpecDetailPrimaryAction;
};

export function ModelSpecPrimaryActionButton({
	action,
	saving,
	advancedImplementationReady,
	implementationRecoveryMessage,
	onAction,
}: Props) {
	const deliveryBlocked = action.label === "构建与提交上线" && !advancedImplementationReady;
	return (
		<Button
			type="primary"
			loading={saving}
			disabled={action.disabled || deliveryBlocked}
			title={action.recoveryMessage || (deliveryBlocked ? implementationRecoveryMessage : undefined)}
			onClick={() => void onAction()}
		>
			{action.label}
		</Button>
	);
}
