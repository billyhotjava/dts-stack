import { useWorkflowStore } from "../../store/workflow-store";

export function useNodeForm(nodeId: string) {
	const node = useWorkflowStore((s) => s.nodes.find((item) => item.id === nodeId));
	const updateNode = useWorkflowStore((s) => s.updateNode);

	const updateConfig = (patch: Record<string, unknown>) => {
		if (!node) return;
		updateNode(nodeId, {
			data: {
				kind: node.data.kind,
				title: node.data.title,
				config: {
					...(node.data.config ?? {}),
					...patch,
				},
			},
		});
	};

	return { node, config: node?.data.config ?? {}, updateConfig };
}
