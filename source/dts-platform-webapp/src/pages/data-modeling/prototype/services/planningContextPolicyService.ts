import type { Sprint64BusinessProcess } from "@/api/sprint64GovernanceApi";

export type BusinessProcessBindingResolution = {
	processes: Sprint64BusinessProcess[];
	selectedId: string | null;
	showSelector: boolean;
	message: string;
};

export function resolveBusinessProcessBinding(
	currentId: string | null | undefined,
	items: readonly Sprint64BusinessProcess[],
): BusinessProcessBindingResolution {
	const processes = items.filter(
		(item) => item.confirmed && String(item.lifecycleStatus || "ACTIVE").toUpperCase() !== "RETIRED",
	);
	const current = processes.find((item) => item.id === currentId)?.id || null;
	if (!processes.length) {
		return {
			processes,
			selectedId: null,
			showSelector: false,
			message: "当前数据域尚无有效业务过程，请先定义能表达业务事件与事实粒度的业务过程。",
		};
	}
	if (processes.length === 1) {
		return {
			processes,
			selectedId: processes[0].id,
			showSelector: false,
			message: `已自动绑定业务过程：${processes[0].name}`,
		};
	}
	return {
		processes,
		selectedId: current,
		showSelector: true,
		message: "当前数据域存在多个有效业务过程，请按事实粒度选择。",
	};
}
