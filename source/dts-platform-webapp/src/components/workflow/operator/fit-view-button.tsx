import { useCallback } from "react";
import { useReactFlow } from "@xyflow/react";

export function FitViewButton() {
	const { fitView } = useReactFlow();
	const onClick = useCallback(() => {
		fitView({ duration: 200, padding: 0.1 });
	}, [fitView]);

	return (
		<button
			type="button"
			className="workflow-operator__btn"
			onClick={onClick}
			aria-label="适配画布"
			title="适配画布到所有节点"
		>
			⤢
		</button>
	);
}
