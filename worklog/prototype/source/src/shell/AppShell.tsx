import { Outlet } from "react-router";
import { CommandPalette } from "./CommandPalette";
import { TopBar } from "./TopBar";
import { StageRail } from "./StageRail";

/**
 * 应用外壳：顶栏 + 左侧阶段轨 + 内容区。
 * 布局用 flex（不用 :has / 容器查询），保证 Chrome 95 兼容。
 */
export function AppShell() {
	return (
		<div style={{ height: "100%", display: "flex", flexDirection: "column" }}>
			<TopBar />
			<div style={{ flex: 1, display: "flex", minHeight: 0 }}>
				<StageRail />
				<main style={{ flex: 1, minWidth: 0, overflow: "auto", padding: 24 }}>
					<Outlet />
				</main>
			</div>
			<CommandPalette />
		</div>
	);
}
