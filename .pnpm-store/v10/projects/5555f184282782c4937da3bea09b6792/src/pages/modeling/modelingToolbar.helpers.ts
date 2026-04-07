export interface ModelingToolbarActionDefinition {
	key: "compile" | "test" | "build" | "commit" | "release" | "sync" | "docs" | "rollback";
	label: string;
}

export function createPrimaryModelingActions(): ModelingToolbarActionDefinition[] {
	return [
		{ key: "compile", label: "编译" },
		{ key: "test", label: "测试" },
		{ key: "build", label: "构建" },
		{ key: "release", label: "上线" },
	];
}

export function createSecondaryModelingActions(): ModelingToolbarActionDefinition[] {
	return [
		{ key: "commit", label: "提交到 Git" },
		{ key: "sync", label: "同步模型" },
		{ key: "docs", label: "生成文档" },
		{ key: "rollback", label: "回退" },
	];
}

export function shouldRenderInlineGitCommit(): false {
	return false;
}
