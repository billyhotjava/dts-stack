export interface ModelPipelineStepDefinition {
	key: "compile" | "test" | "commit" | "publish";
	title: string;
	interactive: false;
}

export function createModelPipelineSteps(): ModelPipelineStepDefinition[] {
	return [
		{ key: "compile", title: "编译", interactive: false },
		{ key: "test", title: "测试", interactive: false },
		{ key: "commit", title: "提交变更", interactive: false },
		{ key: "publish", title: "发布上线", interactive: false },
	];
}
