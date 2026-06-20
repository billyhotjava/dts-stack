import { App as AntApp, ConfigProvider, Spin } from "antd";
import zhCN from "antd/locale/zh_CN";
import { useEffect } from "react";
import { RouterProvider } from "react-router";
import { router } from "@/routes/router";
import { DevReset } from "@/shell/DevReset";
import { useProjectStore } from "@/store/projectStore";
import { useWorkspaceStore } from "@/store/workspaceStore";
import { antdTheme } from "@/ui/theme";

export default function App() {
	const loadWorkspaces = useWorkspaceStore((s) => s.loadWorkspaces);
	const wsReady = useWorkspaceStore((s) => s.ready);
	const currentWorkspaceId = useWorkspaceStore((s) => s.currentWorkspaceId);
	const loadProjects = useProjectStore((s) => s.loadProjects);

	// 启动：加载工作区（部门）
	useEffect(() => {
		void loadWorkspaces();
	}, [loadWorkspaces]);

	// 工作区变更 → 联动加载该工作区下的项目
	useEffect(() => {
		if (currentWorkspaceId) void loadProjects(currentWorkspaceId);
	}, [currentWorkspaceId, loadProjects]);

	return (
		<ConfigProvider theme={antdTheme} locale={zhCN}>
			<AntApp>
				{wsReady ? (
					<>
						<RouterProvider router={router} />
						<DevReset />
					</>
				) : (
					<div style={{ height: "100%", display: "grid", placeItems: "center" }}>
						<Spin tip="加载工作区…">
							<div style={{ width: 1, height: 1 }} />
						</Spin>
					</div>
				)}
			</AntApp>
		</ConfigProvider>
	);
}
