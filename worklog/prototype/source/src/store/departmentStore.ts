import { create } from "zustand";
import { persist } from "zustand/middleware";
import { unwrap } from "@/mock/client";
import { departmentService } from "@/mock/services/departmentService";
import type { Department } from "@/types/department";

interface DepartmentState {
	departments: Department[];
	currentDepartmentId: string | null;
	ready: boolean;
	loadDepartments: () => Promise<void>;
	setCurrentDepartment: (id: string) => void;
	current: () => Department | null;
}

/**
 * 部门上下文（主组织边界）。currentDepartmentId 持久化。
 * 切换部门会触发 projectSpaceStore 重载该部门的项目空间（App 中联动）。
 */
export const useDepartmentStore = create<DepartmentState>()(
	persist(
		(set, get) => ({
			departments: [],
			currentDepartmentId: null,
			ready: false,
			async loadDepartments() {
				const departments = unwrap(await departmentService.list());
				set((state) => ({
					departments,
					ready: true,
					currentDepartmentId:
						state.currentDepartmentId && departments.some((d) => d.id === state.currentDepartmentId)
							? state.currentDepartmentId
							: (departments[0]?.id ?? null),
				}));
			},
			setCurrentDepartment(id) {
				set({ currentDepartmentId: id });
			},
			current() {
				const { departments, currentDepartmentId } = get();
				return departments.find((d) => d.id === currentDepartmentId) ?? null;
			},
		}),
		{
			name: "dts-proto-department",
			partialize: (state) => ({ currentDepartmentId: state.currentDepartmentId }),
		},
	),
);
