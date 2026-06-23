import { create } from "zustand";
import { persist } from "zustand/middleware";
import { unwrap } from "@/mock/client";
import { projectSpaceService } from "@/mock/services/projectSpaceService";
import type { ProjectSpace } from "@/types/projectSpace";

interface ProjectSpaceState {
	/** 当前部门下的项目空间（dev 辅助分组，集成/建模阶段用） */
	spaces: ProjectSpace[];
	currentSpaceId: string | null;
	loadSpaces: (departmentId: string) => Promise<void>;
	setCurrent: (id: string | null) => void;
	current: () => ProjectSpace | null;
}

/**
 * 项目空间上下文（辅助）。随部门切换重载；仅在集成/建模阶段作为可选分组维度。
 * 注意：项目空间不承载黄金主线状态——那归口部门。
 */
export const useProjectSpaceStore = create<ProjectSpaceState>()(
	persist(
		(set, get) => ({
			spaces: [],
			currentSpaceId: null,
			async loadSpaces(departmentId) {
				const spaces = unwrap(await projectSpaceService.listByDepartment(departmentId));
				set((state) => ({
					spaces,
					currentSpaceId:
						state.currentSpaceId && spaces.some((s) => s.id === state.currentSpaceId)
							? state.currentSpaceId
							: (spaces[0]?.id ?? null),
				}));
			},
			setCurrent(id) {
				set({ currentSpaceId: id });
			},
			current() {
				const { spaces, currentSpaceId } = get();
				return spaces.find((s) => s.id === currentSpaceId) ?? null;
			},
		}),
		{
			name: "dts-proto-project-space",
			partialize: (state) => ({ currentSpaceId: state.currentSpaceId }),
		},
	),
);
