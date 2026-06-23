import {
	ApiOutlined,
	AppstoreOutlined,
	CheckOutlined,
	CloudServerOutlined,
	LockOutlined,
	SafetyCertificateOutlined,
	SettingOutlined,
} from "@ant-design/icons";
import { NavLink } from "react-router";
import { StatusDot } from "@/ui/components";
import { useDepartmentStore } from "@/store/departmentStore";
import { deriveStageStatuses, STAGES } from "./stages";
import type { StageStatus } from "@/types/department";

const PLATFORM_ITEMS = [
	{ key: "serve", label: "数据服务", path: "/platform/serve", icon: <ApiOutlined /> },
	{ key: "govern", label: "治理", path: "/platform/govern", icon: <SafetyCertificateOutlined /> },
	{ key: "security", label: "安全", path: "/platform/security", icon: <LockOutlined /> },
	{ key: "ops", label: "运维", path: "/platform/ops", icon: <CloudServerOutlined /> },
	{ key: "settings", label: "设置", path: "/platform/settings", icon: <SettingOutlined /> },
];

function StageMarker({ status, index }: { status: StageStatus; index: number }) {
	if (status === "done") {
		return (
			<span
				style={{
					width: 18,
					height: 18,
					borderRadius: "50%",
					background: "var(--success)",
					color: "#fff",
					display: "grid",
					placeItems: "center",
					fontSize: 10,
					flex: "0 0 auto",
				}}
			>
				<CheckOutlined />
			</span>
		);
	}
	if (status === "active") {
		return (
			<span style={{ width: 18, display: "grid", placeItems: "center", flex: "0 0 auto" }}>
				<StatusDot tone="active" pulse size={10} />
			</span>
		);
	}
	return (
		<span
			style={{
				width: 18,
				height: 18,
				borderRadius: "50%",
				border: "1.5px solid var(--hairline-strong)",
				color: "var(--ink-subtle)",
				display: "grid",
				placeItems: "center",
				fontSize: 10,
				flex: "0 0 auto",
			}}
		>
			{index}
		</span>
	);
}

const rowStyle = (active: boolean): React.CSSProperties => ({
	display: "flex",
	alignItems: "center",
	gap: 10,
	height: 40,
	padding: "0 12px",
	borderRadius: "var(--radius-md)",
	color: active ? "var(--accent-active)" : "var(--ink)",
	background: active ? "var(--accent-soft)" : "transparent",
	fontWeight: active ? 600 : 500,
	textDecoration: "none",
	fontSize: "var(--text-base)",
});

/** 左侧导航轨：上半=黄金主线 4 阶段（按当前部门派生状态），下半=平台旁路区。 */
export function StageRail() {
	const current = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	const statuses = current ? deriveStageStatuses(current) : null;

	return (
		<nav
			aria-label="主导航"
			style={{
				width: "var(--shell-rail-w)",
				flex: "0 0 auto",
				background: "var(--surface)",
				borderRight: "1px solid var(--hairline)",
				padding: 12,
				display: "flex",
				flexDirection: "column",
				gap: 4,
				overflowY: "auto",
			}}
		>
			<NavLink to="/portal" style={({ isActive }) => rowStyle(isActive)}>
				<AppstoreOutlined style={{ width: 18, textAlign: "center" }} />
				<span>部门门户</span>
			</NavLink>

			<div
				style={{
					fontSize: 11,
					fontWeight: 600,
					letterSpacing: "0.08em",
					textTransform: "uppercase",
					color: "var(--ink-subtle)",
					padding: "12px 12px 4px",
				}}
			>
				黄金主线
			</div>

			{STAGES.map((stage) => {
				const status = statuses?.[stage.key] ?? "todo";
				return (
					<NavLink key={stage.key} to={stage.path} style={({ isActive }) => rowStyle(isActive)}>
						<StageMarker status={status} index={stage.index} />
						<span>{stage.label}</span>
						<span style={{ marginLeft: "auto", fontSize: 11, color: "var(--ink-subtle)" }}>
							{status === "done" ? "完成" : status === "active" ? "进行中" : "待开始"}
						</span>
					</NavLink>
				);
			})}

			<div style={{ height: 1, background: "var(--hairline)", margin: "12px 4px" }} />

			<div
				style={{
					fontSize: 11,
					fontWeight: 600,
					letterSpacing: "0.08em",
					textTransform: "uppercase",
					color: "var(--ink-subtle)",
					padding: "0 12px 4px",
				}}
			>
				平台
			</div>

			{PLATFORM_ITEMS.map((item) => (
				<NavLink key={item.key} to={item.path} style={({ isActive }) => rowStyle(isActive)}>
					<span style={{ width: 18, textAlign: "center" }}>{item.icon}</span>
					<span>{item.label}</span>
				</NavLink>
			))}
		</nav>
	);
}
