import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { Project } from "@/types/project";
import { projectService } from "@/mock/services/projectService";
import { unwrap } from "@/mock/client";

interface ProjectState {
	projects: Project[];
	currentId: string | null;
	loading: boolean;
	loadProjects: () => Promise<void>;
	setCurrent: (id: string) => void;
	current: () => Project | null;
}

/**
 * 项目上下文 store。currentId 持久化到 localStorage，
 * 使刷新后仍停留在同一项目（与现网 contextStore 一致的持久化策略）。
 */
export const useProjectStore = create<ProjectState>()(
	persist(
		(set, get) => ({
			projects: [],
			currentId: null,
			loading: false,
			async loadProjects() {
				set({ loading: true });
				const projects = unwrap(await projectService.list());
				set((state) => ({
					projects,
					loading: false,
					// 若尚无选中项或选中项已不存在，默认选第一个
					currentId:
						state.currentId && projects.some((p) => p.id === state.currentId)
							? state.currentId
							: (projects[0]?.id ?? null),
				}));
			},
			setCurrent(id) {
				set({ currentId: id });
			},
			current() {
				const { projects, currentId } = get();
				return projects.find((p) => p.id === currentId) ?? null;
			},
		}),
		{
			name: "dts-proto-project",
			partialize: (state) => ({ currentId: state.currentId }),
		},
	),
);
