import { ReloadOutlined } from "@ant-design/icons";
import { App as AntApp, Button } from "antd";
import { resetDb } from "@/mock/db";
import { useProjectStore } from "@/store/projectStore";
import { useWorkspaceStore } from "@/store/workspaceStore";

/** 开发入口：把 mock 数据复位为初始样例，并重载工作区与项目 store。 */
export function DevReset() {
	const loadWorkspaces = useWorkspaceStore((s) => s.loadWorkspaces);
	const currentWorkspaceId = useWorkspaceStore((s) => s.currentWorkspaceId);
	const loadProjects = useProjectStore((s) => s.loadProjects);
	const { message } = AntApp.useApp();

	return (
		<div style={{ position: "fixed", right: 16, bottom: 16, zIndex: 1000 }}>
			<Button
				size="small"
				icon={<ReloadOutlined />}
				onClick={async () => {
					resetDb();
					await loadWorkspaces();
					if (currentWorkspaceId) await loadProjects(currentWorkspaceId);
					message.success("已重置为样例数据");
				}}
			>
				重置样例数据
			</Button>
		</div>
	);
}
