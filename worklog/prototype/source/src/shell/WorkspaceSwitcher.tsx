import { CheckOutlined, DownOutlined, TeamOutlined } from "@ant-design/icons";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useWorkspaceStore } from "@/store/workspaceStore";

/** 顶栏工作区（部门）切换器 —— 三层模型的最外层组织边界。 */
export function WorkspaceSwitcher() {
	const workspaces = useWorkspaceStore((s) => s.workspaces);
	const currentWorkspaceId = useWorkspaceStore((s) => s.currentWorkspaceId);
	const setCurrentWorkspace = useWorkspaceStore((s) => s.setCurrentWorkspace);
	const current = workspaces.find((w) => w.id === currentWorkspaceId) ?? null;

	const items: MenuProps["items"] = workspaces.map((w) => ({
		key: w.id,
		label: (
			<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, minWidth: 220 }}>
				<span style={{ display: "flex", flexDirection: "column" }}>
					<span style={{ fontWeight: 600 }}>
						{w.name}
						<span style={{ marginLeft: 8, fontSize: 11, color: "var(--ink-subtle)" }}>{w.deptCode}</span>
					</span>
					<span style={{ fontSize: 12, color: "var(--ink-subtle)" }}>
						{w.description} · {w.memberCount} 人
					</span>
				</span>
				{w.id === currentWorkspaceId ? <CheckOutlined style={{ color: "var(--accent)" }} /> : null}
			</div>
		),
	}));

	return (
		<Dropdown
			trigger={["click"]}
			menu={{ items, onClick: ({ key }) => setCurrentWorkspace(key), selectedKeys: currentWorkspaceId ? [currentWorkspaceId] : [] }}
		>
			<button
				type="button"
				style={{
					display: "inline-flex",
					alignItems: "center",
					gap: 8,
					height: 32,
					padding: "0 10px",
					background: "transparent",
					border: "1px solid transparent",
					borderRadius: "var(--radius-md)",
					cursor: "pointer",
					color: "var(--ink)",
					fontSize: "var(--text-base)",
				}}
			>
				<TeamOutlined style={{ color: "var(--ink-muted)" }} />
				<span style={{ fontWeight: 600 }}>{current?.name ?? "选择工作区"}</span>
				<DownOutlined style={{ fontSize: 10, color: "var(--ink-subtle)" }} />
			</button>
		</Dropdown>
	);
}
