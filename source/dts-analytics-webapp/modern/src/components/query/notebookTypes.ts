export type FieldRef = {
	fieldId: number;
	joinAlias: string | null;
};

export type MergedField = {
	fieldId: number;
	name: string;
	displayName: string;
	baseType?: string;
	tableAlias: string | null;
	tableName: string;
};

export type JoinType = "left-join" | "inner-join" | "right-join" | "full-join";
export type JoinConditionOp = "=" | "!=" | ">" | ">=" | "<" | "<=";

export type JoinCondition = {
	id: string;
	leftField: FieldRef | null;
	op: JoinConditionOp;
	rightFieldId: number | null;
};

export type JoinConfig = {
	id: string;
	sourceTableId: number | null;
	alias: string;
	strategy: JoinType;
	conditions: JoinCondition[];
	conditionCombine: "and" | "or";
	tableDetail?: import("../../api/analyticsApi").TableDetail;
};

export type FilterOp =
	| "=" | "!=" | ">" | ">=" | "<" | "<="
	| "between" | "in"
	| "is-null" | "not-null"
	| "contains" | "starts-with" | "ends-with"
	| "is-empty" | "not-empty";

export type FilterRow = {
	id: string;
	field: FieldRef | null;
	op: FilterOp;
	value1: string;
	value2: string;
};

export type AggregationOp = "count" | "sum" | "avg" | "min" | "max";

export type AggregationRow = {
	id: string;
	op: AggregationOp;
	field: FieldRef | null;
};

export type NotebookState = {
	sourceTableId: number | null;
	sourceTableDetail?: import("../../api/analyticsApi").TableDetail;
	joins: JoinConfig[];
	selectedFields: FieldRef[];
	filters: FilterRow[];
	aggregations: AggregationRow[];
	groupByFields: FieldRef[];
	orderByKey: string;
	orderByDir: "asc" | "desc";
	limit: number;
};

export function makeId(): string {
	return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function fieldRefKey(ref: FieldRef): string {
	return ref.joinAlias ? `${ref.joinAlias}:${ref.fieldId}` : `${ref.fieldId}`;
}

export function fieldRefEquals(a: FieldRef | null, b: FieldRef | null): boolean {
	if (!a || !b) return false;
	return a.fieldId === b.fieldId && a.joinAlias === b.joinAlias;
}

export function fieldRefToMbql(ref: FieldRef): any[] {
	if (ref.joinAlias) return ["field", ref.fieldId, { "join-alias": ref.joinAlias }];
	return ["field", ref.fieldId, {}];
}

export function parseFieldRefFromMbql(node: unknown): FieldRef | null {
	if (!Array.isArray(node)) return null;
	if (node[0] !== "field") return null;
	const id = Number(node[1]);
	if (!Number.isFinite(id) || id <= 0) return null;
	const opts = node[2];
	const alias = opts && typeof opts === "object" && "join-alias" in opts
		? String((opts as Record<string, unknown>)["join-alias"])
		: null;
	return { fieldId: id, joinAlias: alias };
}
