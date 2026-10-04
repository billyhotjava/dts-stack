import { Tabs } from "antd";
import type { ReactNode } from "react";

export type SubjectWorkspaceTab = "scope" | "data-marts" | "details" | "governance";

export function SubjectWorkspaceTabs({
	activeKey,
	onChange,
	scope,
	dataMarts,
	details,
	governance,
}: {
	activeKey: SubjectWorkspaceTab;
	onChange: (key: SubjectWorkspaceTab) => void;
	scope: ReactNode;
	dataMarts: ReactNode;
	details: ReactNode;
	governance: ReactNode;
}) {
	return (
		<Tabs
			activeKey={activeKey}
			onChange={(key) => onChange(key as SubjectWorkspaceTab)}
			items={[
				{ key: "scope", label: "建模范围", children: scope },
				{ key: "data-marts", label: "数据集市", children: dataMarts },
				{ key: "details", label: "主题域信息", children: details },
				{ key: "governance", label: "治理概览", children: governance },
			]}
		/>
	);
}
