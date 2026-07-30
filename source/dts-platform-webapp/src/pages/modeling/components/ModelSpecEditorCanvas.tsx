import { Button, Card, Drawer, Space } from "antd";
import { Database, Rocket } from "lucide-react";
import type { ReactNode } from "react";
import type { ModelSpecDetailStage } from "../modelSpecDetailNavigation";
import type { ModelSpecDetailPrimaryAction } from "../modelSpecDetailStageProjection";
import { ModelSpecPrimaryActionButton, type ModelSpecPrimaryActionContext } from "./ModelSpecPrimaryActionButton";

type Props = {
	activeStage: ModelSpecDetailStage;
	implementationDisabled: boolean;
	logical: ReactNode;
	implementation: ReactNode;
	physical: ReactNode;
	drawerAction?: ModelSpecDetailPrimaryAction;
	primaryActionContext: ModelSpecPrimaryActionContext;
	onStageChange: (stage: ModelSpecDetailStage) => void;
};

export function ModelSpecEditorCanvas({
	activeStage,
	implementationDisabled,
	logical,
	implementation,
	physical,
	drawerAction,
	primaryActionContext,
	onStageChange,
}: Props) {
	const activeDrawerAction = drawerAction ? (
		<ModelSpecPrimaryActionButton action={drawerAction} {...primaryActionContext} />
	) : null;
	return (
		<section data-testid="model-spec-editor-canvas">
			<div className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2">
				<div className="text-sm font-medium text-slate-700">单页逻辑设计</div>
				<Space wrap>
					<Button
						icon={<Database size={15} />}
						disabled={implementationDisabled}
						title={implementationDisabled ? "先完成逻辑设计后再配置数据实现" : undefined}
						onClick={() => onStageChange("implementation")}
					>
						数据实现
					</Button>
					<Button icon={<Rocket size={15} />} onClick={() => onStageChange("physical")}>
						发布结果
					</Button>
				</Space>
			</div>

			<Card data-testid="model-spec-logical-canvas">{logical}</Card>

			<Drawer
				open={activeStage === "implementation"}
				width={1040}
				title="数据实现"
				extra={activeStage === "implementation" ? activeDrawerAction : null}
				destroyOnClose={false}
				maskClosable={false}
				onClose={() => onStageChange("logical")}
			>
				{implementation}
			</Drawer>

			<Drawer
				open={activeStage === "physical"}
				width={1040}
				title="发布结果"
				extra={activeStage === "physical" ? activeDrawerAction : null}
				destroyOnClose={false}
				onClose={() => onStageChange("logical")}
			>
				{physical}
			</Drawer>
		</section>
	);
}
