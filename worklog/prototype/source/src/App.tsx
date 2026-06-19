import { App as AntApp, ConfigProvider, Spin } from "antd";
import zhCN from "antd/locale/zh_CN";
import { useEffect } from "react";
import { RouterProvider } from "react-router";
import { router } from "@/routes/router";
import { DevReset } from "@/shell/DevReset";
import { useProjectStore } from "@/store/projectStore";
import { antdTheme } from "@/ui/theme";

export default function App() {
	const loadProjects = useProjectStore((s) => s.loadProjects);
	const loading = useProjectStore((s) => s.loading);
	const loaded = useProjectStore((s) => s.projects.length > 0);

	useEffect(() => {
		void loadProjects();
	}, [loadProjects]);

	return (
		<ConfigProvider theme={antdTheme} locale={zhCN}>
			<AntApp>
				{loaded ? (
					<>
						<RouterProvider router={router} />
						<DevReset />
					</>
				) : (
					<div style={{ height: "100%", display: "grid", placeItems: "center" }}>
						<Spin spinning={loading} tip="加载项目…">
							<div style={{ width: 1, height: 1 }} />
						</Spin>
					</div>
				)}
			</AntApp>
		</ConfigProvider>
	);
}
