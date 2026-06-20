import { DownOutlined, FolderOpenOutlined, CheckOutlined } from "@ant-design/icons";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useProjectStore } from "@/store/projectStore";

/** 顶栏项目切换器 —— 项目上下文贯穿所有阶段。 */
export function ProjectSwitcher() {
	const projects = useProjectStore((s) => s.projects);
	const currentId = useProjectStore((s) => s.currentId);
	const setCurrent = useProjectStore((s) => s.setCurrent);
	const current = projects.find((p) => p.id === currentId) ?? null;

	// projects 已是当前工作区下的项目（projectStore 按 workspace 加载）
	const items: MenuProps["items"] = projects.map((p) => ({
		key: p.id,
		label: (
			<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, minWidth: 220 }}>
				<span style={{ display: "flex", flexDirection: "column" }}>
					<span style={{ fontWeight: 600 }}>{p.name}</span>
					<span style={{ fontSize: 12, color: "var(--ink-subtle)" }}>{p.description}</span>
				</span>
				{p.id === currentId ? <CheckOutlined style={{ color: "var(--accent)" }} /> : null}
			</div>
		),
	}));

	return (
		<Dropdown
			trigger={["click"]}
			menu={{ items, onClick: ({ key }) => setCurrent(key), selectedKeys: currentId ? [currentId] : [] }}
		>
			<button
				type="button"
				style={{
					display: "inline-flex",
					alignItems: "center",
					gap: 8,
					height: 32,
					padding: "0 10px",
					background: "var(--surface)",
					border: "1px solid var(--hairline)",
					borderRadius: "var(--radius-md)",
					cursor: "pointer",
					color: "var(--ink)",
					fontSize: "var(--text-base)",
				}}
			>
				<FolderOpenOutlined style={{ color: "var(--accent)" }} />
				<span style={{ fontWeight: 600 }}>{current?.name ?? "选择项目"}</span>
				<DownOutlined style={{ fontSize: 10, color: "var(--ink-subtle)" }} />
			</button>
		</Dropdown>
	);
}
