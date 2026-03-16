export interface ModelingToolbarActionDefinition {
	key: "compile" | "test" | "commit" | "release" | "sync" | "docs" | "rollback";
	label: string;
}

export function createPrimaryModelingActions(): ModelingToolbarActionDefinition[] {
	return [
		{ key: "compile", label: "编译" },
		{ key: "test", label: "测试" },
		{ key: "release", label: "上线" },
	];
}

export function createSecondaryModelingActions(): ModelingToolbarActionDefinition[] {
	return [
		{ key: "commit", label: "提交变更" },
		{ key: "sync", label: "同步模型" },
		{ key: "docs", label: "文档" },
		{ key: "rollback", label: "回退" },
	];
}

export function shouldRenderInlineGitCommit(): false {
	return false;
}
