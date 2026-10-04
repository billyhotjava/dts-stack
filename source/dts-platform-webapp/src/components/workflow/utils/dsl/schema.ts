import { z } from "zod";

export const workflowPositionSchema = z.object({
	x: z.number(),
	y: z.number(),
});

export const workflowViewportSchema = z.object({
	x: z.number(),
	y: z.number(),
	zoom: z.number(),
});

export const serializedNodeSchema: z.ZodType<SerializedNode> = z.lazy(() =>
	z.object({
		id: z.string().min(1),
		type: z.string().min(1),
		position: workflowPositionSchema,
		data: z.record(z.string(), z.unknown()),
		children: z.array(serializedNodeSchema).optional(),
		childEdges: z.array(serializedEdgeSchema).optional(),
	}),
);

export const serializedEdgeSchema = z.object({
	id: z.string().min(1),
	source: z.string().min(1),
	target: z.string().min(1),
	sourceHandle: z.string().optional(),
	targetHandle: z.string().optional(),
});

function nodeKind(node: SerializedNode): string {
	const kind = node.data.kind;
	return typeof kind === "string" ? kind : node.type;
}

function validateNoNestedAdvanced(
	nodes: SerializedNode[],
	insideAdvanced: boolean,
	ctx: z.RefinementCtx,
	path: Array<string | number>,
) {
	for (let index = 0; index < nodes.length; index += 1) {
		const node = nodes[index];
		const kind = nodeKind(node);
		const advanced = kind === "iteration" || kind === "loop";
		if (insideAdvanced && advanced) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: [...path, index, "type"],
				message: "Nested iteration/loop nodes are not supported in DSL v1.0",
			});
		}
		validateNoNestedAdvanced(node.children ?? [], insideAdvanced || advanced, ctx, [...path, index, "children"]);
	}
}

export const workflowDslSchema = z
	.object({
		dslVersion: z.string().min(1),
		metadata: z
			.object({
				createdAt: z.string().optional(),
				updatedAt: z.string().optional(),
			})
			.optional(),
		nodes: z.array(serializedNodeSchema),
		edges: z.array(serializedEdgeSchema),
		viewport: workflowViewportSchema,
	})
	.superRefine((dsl, ctx) => validateNoNestedAdvanced(dsl.nodes, false, ctx, ["nodes"]));

export type SerializedNode = {
	id: string;
	type: string;
	position: { x: number; y: number };
	data: Record<string, unknown>;
	children?: SerializedNode[];
	childEdges?: SerializedEdge[];
};

export type SerializedEdge = z.infer<typeof serializedEdgeSchema>;
export type WorkflowDsl = z.infer<typeof workflowDslSchema>;
