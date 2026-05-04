import { createContext, useContext, useMemo, type ReactNode } from "react";

/**
 * WorkflowCanvasContext — 透传画布外部入参（不进 zustand store，避免污染撤销重做）。
 *
 * - readonly: 是否禁用编辑（节点/连线添加、拖拽、删除）
 * - projectId: 业务项目 ID，DSL 序列化时附带，用于后端校验归属
 * - onSave: 工具栏「保存」按钮触发；T07 OrchestrationPage 接入时绑定
 */

export interface WorkflowContextValue {
	readonly: boolean;
	projectId?: string;
	onSave?: () => void | Promise<void>;
}

const DEFAULT_VALUE: Readonly<WorkflowContextValue> = Object.freeze({ readonly: false });

const WorkflowContext = createContext<WorkflowContextValue>(DEFAULT_VALUE);

export interface WorkflowContextProviderProps {
	value?: Partial<WorkflowContextValue>;
	children: ReactNode;
}

export function WorkflowContextProvider({ value, children }: WorkflowContextProviderProps) {
	const merged = useMemo<WorkflowContextValue>(
		() => ({ ...DEFAULT_VALUE, ...(value ?? {}) }),
		[value?.readonly, value?.projectId, value?.onSave],
	);
	return <WorkflowContext.Provider value={merged}>{children}</WorkflowContext.Provider>;
}

export function useWorkflowContext(): WorkflowContextValue {
	return useContext(WorkflowContext);
}
