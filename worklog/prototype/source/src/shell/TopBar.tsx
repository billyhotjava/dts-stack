import { BellOutlined, SearchOutlined } from "@ant-design/icons";
import { Avatar } from "antd";
import { ProjectSwitcher } from "./ProjectSwitcher";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";

/** 顶栏：品牌 + 项目上下文 + 全局搜索（占位，S7 实现 ⌘K）+ 账号。 */
export function TopBar() {
	return (
		<header
			style={{
				height: "var(--shell-topbar-h)",
				display: "flex",
				alignItems: "center",
				gap: 16,
				padding: "0 16px",
				background: "var(--surface)",
				borderBottom: "1px solid var(--hairline)",
			}}
		>
			<div style={{ display: "flex", alignItems: "center", gap: 10 }}>
				<div
					style={{
						width: 26,
						height: 26,
						borderRadius: "var(--radius-md)",
						background: "var(--accent)",
						color: "#fff",
						display: "grid",
						placeItems: "center",
						fontWeight: 800,
						fontSize: 13,
						letterSpacing: "-0.04em",
					}}
				>
					D
				</div>
				<span style={{ fontWeight: 700, letterSpacing: "-0.01em" }}>DTS</span>
				<span style={{ color: "var(--hairline-strong)" }}>▸</span>
			</div>

			{/* 工作区(部门) ▸ 项目 两级上下文 */}
			<WorkspaceSwitcher />
			<span style={{ color: "var(--hairline-strong)" }}>▸</span>
			<ProjectSwitcher />

			<div style={{ flex: 1 }} />

			<button
				type="button"
				title="全局搜索（S7 实现 ⌘K）"
				style={{
					display: "inline-flex",
					alignItems: "center",
					gap: 8,
					height: 32,
					padding: "0 12px",
					minWidth: 200,
					color: "var(--ink-subtle)",
					background: "var(--surface-sunken)",
					border: "1px solid var(--hairline)",
					borderRadius: "var(--radius-md)",
					cursor: "pointer",
				}}
			>
				<SearchOutlined />
				<span style={{ fontSize: "var(--text-sm)" }}>搜索</span>
				<span style={{ marginLeft: "auto", fontSize: 11, opacity: 0.7 }}>⌘K</span>
			</button>

			<BellOutlined style={{ fontSize: 16, color: "var(--ink-muted)" }} />
			<div style={{ display: "flex", alignItems: "center", gap: 8 }}>
				<Avatar size={28} style={{ background: "var(--accent-soft)", color: "var(--accent-active)", fontSize: 13 }}>
					崔
				</Avatar>
				<span style={{ fontSize: "var(--text-sm)", color: "var(--ink-muted)" }}>崔耀文</span>
			</div>
		</header>
	);
}
