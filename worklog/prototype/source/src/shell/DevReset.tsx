import { ReloadOutlined } from "@ant-design/icons";
import { App as AntApp, Button } from "antd";
import { resetDb } from "@/mock/db";
import { useDepartmentStore } from "@/store/departmentStore";
import { useProjectSpaceStore } from "@/store/projectSpaceStore";

/** 开发入口：把 mock 数据复位为初始样例，并重载部门与项目空间 store。 */
export function DevReset() {
	const loadDepartments = useDepartmentStore((s) => s.loadDepartments);
	const currentDepartmentId = useDepartmentStore((s) => s.currentDepartmentId);
	const loadSpaces = useProjectSpaceStore((s) => s.loadSpaces);
	const { message } = AntApp.useApp();

	return (
		<div style={{ position: "fixed", right: 16, bottom: 16, zIndex: 1000 }}>
			<Button
				size="small"
				icon={<ReloadOutlined />}
				onClick={async () => {
					resetDb();
					await loadDepartments();
					if (currentDepartmentId) await loadSpaces(currentDepartmentId);
					message.success("已重置为样例数据");
				}}
			>
				重置样例数据
			</Button>
		</div>
	);
}
