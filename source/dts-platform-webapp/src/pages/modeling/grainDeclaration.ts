export type GrainDeclaration = {
	statement: string;
	grainKeys: string[];
};

export type GrainGateResult =
	| { status: "ready"; reason: string }
	| { status: "missing"; reason: string }
	| { status: "blocked"; reason: string };

export const isGrainDeclared = (grain: GrainDeclaration | undefined | null, fieldNames: string[]): boolean => {
	if (!grain?.statement?.trim() || !Array.isArray(grain.grainKeys) || grain.grainKeys.length === 0) return false;
	const known = new Set(fieldNames.map((field) => field.trim()).filter(Boolean));
	return grain.grainKeys.every((key) => known.has(String(key).trim()));
};

export const resolveGrainDeclaration = (
	grain: GrainDeclaration | undefined | null,
	fieldNames: string[],
): GrainGateResult => {
	if (!grain?.statement?.trim() || !Array.isArray(grain.grainKeys) || grain.grainKeys.length === 0) {
		return { status: "missing", reason: "请填写粒度语句并至少选择一个粒度键" };
	}
	if (!isGrainDeclared(grain, fieldNames)) {
		return { status: "blocked", reason: "粒度键必须来自当前模型字段" };
	}
	return { status: "ready", reason: "已声明模型粒度" };
};
