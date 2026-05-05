import { useMemo, useState } from "react";
import { Tabs } from "antd";
import { BlockSelectorPanel, WorkflowCanvas } from "@/components/workflow";
import "@/components/workflow/block-selector/styles.css";
import OrchestrationRunsTab from "./OrchestrationRunsTab";

type OrchestrationTabKey = "canvas" | "runs";

const VALID_TABS: ReadonlyArray<OrchestrationTabKey> = ["canvas", "runs"];

function isValidTab(value: string): value is OrchestrationTabKey {
	return (VALID_TABS as ReadonlyArray<string>).includes(value);
}

export default function OrchestrationPage() {
	const [activeKey, setActiveKey] = useState<OrchestrationTabKey>("canvas");

	const tabItems = useMemo(
		() => [
			{
				key: "canvas" as const,
				label: "编排画布",
				// destroyInactiveTabPane=false 默认行为；保留 viewport / store
				children: (
					<div
						style={{
							display: "flex",
							height: "calc(100vh - 220px)",
							minHeight: 480,
							border: "1px solid #e2e8f0",
							borderRadius: 8,
							overflow: "hidden",
							background: "#ffffff",
						}}
					>
						<BlockSelectorPanel />
						<div style={{ flex: 1, minWidth: 0 }}>
							<WorkflowCanvas readonly={false} />
						</div>
					</div>
				),
			},
			{
				key: "runs" as const,
				label: "运行实例",
				children: <OrchestrationRunsTab />,
			},
		],
		[],
	);

	return (
		<Tabs
			activeKey={activeKey}
			onChange={(key) => {
				if (isValidTab(key)) {
					setActiveKey(key);
				}
			}}
			items={tabItems}
			destroyInactiveTabPane={false}
		/>
	);
}
