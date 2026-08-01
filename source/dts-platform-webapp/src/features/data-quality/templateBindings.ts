type TemplateParam = { name?: string; label?: string; type?: string };
type PhysicalDataset = {
	id?: string;
	name?: string;
	schemaName?: string;
	tableName?: string;
	hiveDatabase?: string;
	hiveTable?: string;
};

const parseSchema = (schema: unknown): TemplateParam[] => {
	if (Array.isArray(schema)) return schema as TemplateParam[];
	if (typeof schema !== "string" || !schema.trim()) return [];
	try {
		const parsed = JSON.parse(schema);
		return Array.isArray(parsed) ? parsed : [];
	} catch {
		return [];
	}
};

export const bindTemplateTargetTable = (
	params: Record<string, unknown>,
	dataset: PhysicalDataset,
	paramSchema: unknown,
) => {
	const target = parseSchema(paramSchema).find(
		(param) => param.type === "table_select" && (param.name === "table" || param.label === "目标表"),
	);
	if (!target?.name) return { ...params };
	const physicalTable = String(dataset.hiveTable || dataset.tableName || "").trim();
	if (!physicalTable) throw new Error("所选数据资产缺少可绑定的物理表名称");
	const physicalSchema = String(dataset.hiveDatabase || dataset.schemaName || "").trim();
	const qualifiedTable =
		physicalSchema && !physicalTable.includes(".") ? `${physicalSchema}.${physicalTable}` : physicalTable;
	return { ...params, [target.name]: qualifiedTable };
};

export const buildBatchTemplatePreviewTargets = (
	datasetIds: string[],
	datasets: PhysicalDataset[],
	params: Record<string, unknown>,
	paramSchema: unknown,
) =>
	datasetIds.map((datasetId) => {
		const dataset = datasets.find((item) => String(item.id) === datasetId);
		if (!dataset) throw new Error(`未找到目标数据资产：${datasetId}`);
		return {
			datasetId,
			label: String(dataset.name || datasetId),
			params: bindTemplateTargetTable(params, dataset, paramSchema),
		};
	});
