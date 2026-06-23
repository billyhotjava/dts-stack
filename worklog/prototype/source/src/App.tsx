import { App as AntApp, ConfigProvider, Spin } from "antd";
import zhCN from "antd/locale/zh_CN";
import { useEffect } from "react";
import { RouterProvider } from "react-router";
import { router } from "@/routes/router";
import { DevReset } from "@/shell/DevReset";
import { useDepartmentStore } from "@/store/departmentStore";
import { useProjectSpaceStore } from "@/store/projectSpaceStore";
import { antdTheme } from "@/ui/theme";

export default function App() {
	const loadDepartments = useDepartmentStore((s) => s.loadDepartments);
	const ready = useDepartmentStore((s) => s.ready);
	const currentDepartmentId = useDepartmentStore((s) => s.currentDepartmentId);
	const loadSpaces = useProjectSpaceStore((s) => s.loadSpaces);

	// 启动：加载部门（主组织边界）
	useEffect(() => {
		void loadDepartments();
	}, [loadDepartments]);

	// 部门变更 → 联动加载该部门下的项目空间（集成阶段用）
	useEffect(() => {
		if (currentDepartmentId) void loadSpaces(currentDepartmentId);
	}, [currentDepartmentId, loadSpaces]);

	return (
		<ConfigProvider theme={antdTheme} locale={zhCN}>
			<AntApp>
				{ready ? (
					<>
						<RouterProvider router={router} />
						<DevReset />
					</>
				) : (
					<div style={{ height: "100%", display: "grid", placeItems: "center" }}>
						<Spin tip="加载部门…">
							<div style={{ width: 1, height: 1 }} />
						</Spin>
					</div>
				)}
			</AntApp>
		</ConfigProvider>
	);
}
