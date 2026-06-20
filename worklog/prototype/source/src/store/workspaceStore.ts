import { create } from "zustand";
import { persist } from "zustand/middleware";
import { unwrap } from "@/mock/client";
import { workspaceService } from "@/mock/services/workspaceService";
import type { Workspace } from "@/types/workspace";

interface WorkspaceState {
	workspaces: Workspace[];
	currentWorkspaceId: string | null;
	ready: boolean;
	loadWorkspaces: () => Promise<void>;
	setCurrentWorkspace: (id: string) => void;
	current: () => Workspace | null;
}

/**
 * 工作区（部门）上下文。currentWorkspaceId 持久化，
 * 切换工作区会触发 projectStore 重载该工作区的项目（在 App 中联动）。
 */
export const useWorkspaceStore = create<WorkspaceState>()(
	persist(
		(set, get) => ({
			workspaces: [],
			currentWorkspaceId: null,
			ready: false,
			async loadWorkspaces() {
				const workspaces = unwrap(await workspaceService.list());
				set((state) => ({
					workspaces,
					ready: true,
					currentWorkspaceId:
						state.currentWorkspaceId && workspaces.some((w) => w.id === state.currentWorkspaceId)
							? state.currentWorkspaceId
							: (workspaces[0]?.id ?? null),
				}));
			},
			setCurrentWorkspace(id) {
				set({ currentWorkspaceId: id });
			},
			current() {
				const { workspaces, currentWorkspaceId } = get();
				return workspaces.find((w) => w.id === currentWorkspaceId) ?? null;
			},
		}),
		{
			name: "dts-proto-workspace",
			partialize: (state) => ({ currentWorkspaceId: state.currentWorkspaceId }),
		},
	),
);
