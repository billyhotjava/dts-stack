import { useMemo, useState } from "react";
import { Tabs } from "antd";
import { WorkflowCanvas } from "@/components/workflow";
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
					<div style={{ height: "calc(100vh - 220px)", minHeight: 480 }}>
						<WorkflowCanvas readonly={false} />
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
