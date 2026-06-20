import { create } from "zustand";
import { persist } from "zustand/middleware";
import { unwrap } from "@/mock/client";
import { projectService } from "@/mock/services/projectService";
import type { Project } from "@/types/project";

interface ProjectState {
	/** 当前工作区下的项目 */
	projects: Project[];
	currentId: string | null;
	loading: boolean;
	/** 加载某工作区的项目；切换工作区时调用 */
	loadProjects: (workspaceId: string) => Promise<void>;
	setCurrent: (id: string) => void;
	current: () => Project | null;
}

/**
 * 项目上下文 store。项目隶属工作区，故加载按 workspaceId 过滤。
 * currentId 持久化；切换工作区后若旧项目不在新工作区，则回落到首个项目。
 */
export const useProjectStore = create<ProjectState>()(
	persist(
		(set, get) => ({
			projects: [],
			currentId: null,
			loading: false,
			async loadProjects(workspaceId) {
				set({ loading: true });
				const projects = unwrap(await projectService.listByWorkspace(workspaceId));
				set((state) => ({
					projects,
					loading: false,
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
