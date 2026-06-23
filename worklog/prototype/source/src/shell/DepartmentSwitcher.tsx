import { CheckOutlined, DownOutlined, ApartmentOutlined } from "@ant-design/icons";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useDepartmentStore } from "@/store/departmentStore";

/** 顶栏部门切换器 —— 部门是主组织边界与权限边界。 */
export function DepartmentSwitcher() {
	const departments = useDepartmentStore((s) => s.departments);
	const currentId = useDepartmentStore((s) => s.currentDepartmentId);
	const setCurrent = useDepartmentStore((s) => s.setCurrentDepartment);
	const current = departments.find((d) => d.id === currentId) ?? null;

	const items: MenuProps["items"] = departments.map((d) => ({
		key: d.id,
		label: (
			<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 16, minWidth: 220 }}>
				<span style={{ display: "flex", flexDirection: "column" }}>
					<span style={{ fontWeight: 600 }}>
						{d.name}
						<span style={{ marginLeft: 8, fontSize: 11, color: "var(--ink-subtle)" }}>{d.deptCode}</span>
					</span>
					<span style={{ fontSize: 12, color: "var(--ink-subtle)" }}>
						{d.description} · {d.memberCount} 人
					</span>
				</span>
				{d.id === currentId ? <CheckOutlined style={{ color: "var(--accent)" }} /> : null}
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
				<ApartmentOutlined style={{ color: "var(--accent)" }} />
				<span style={{ fontWeight: 600 }}>{current?.name ?? "选择部门"}</span>
				<DownOutlined style={{ fontSize: 10, color: "var(--ink-subtle)" }} />
			</button>
		</Dropdown>
	);
}
