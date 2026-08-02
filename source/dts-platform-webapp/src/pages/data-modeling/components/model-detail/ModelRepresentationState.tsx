export function ModelRepresentationState({
	state,
	message,
}: {
	state: "loading" | "empty" | "error" | "blocked";
	message?: string;
}) {
	const defaults = {
		loading: "正在读取固定修订的模型表示…",
		empty: "请选择一个模型。",
		error: "模型表示读取失败，请按关联编号联系管理员。",
		blocked: "当前修订缺少可信表示证据，暂不能展示。",
	};
	return (
		<output aria-live="polite" className={`dm-object-tree__empty dm-representation-state is-${state}`}>
			{message || defaults[state]}
		</output>
	);
}
