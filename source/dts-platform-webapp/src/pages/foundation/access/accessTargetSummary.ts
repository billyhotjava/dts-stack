import type { IngestionTaskDTO } from "@/api/ingestion";

export function isModelBoundAccess(task: IngestionTaskDTO): boolean {
	return Boolean(task.destinationConfig?.modelTarget);
}

/** Only project business identifiers; never return connection configuration. */
export function accessTargetMappings(task?: IngestionTaskDTO | null) {
	if (!task) return [];
	const bound = task.destinationConfig?.modelTarget;
	if (bound?.tableName && bound?.schemaName) {
		return [{ key: "model", source: String(task.sourceConfig?._originalName || "接入资源"), target: `${bound.schemaName}.${bound.tableName}` }];
	}
	if (task.tableMapping?.length) {
		return task.tableMapping.map((mapping, index) => ({
			key: `${mapping.source || "source"}-${mapping.target || "target"}-${index}`,
			source: mapping.source || "未记录",
			target: mapping.target || "未记录",
		}));
	}
	const fileTarget = task.sourceConfig?._fileLanding?.targetTable;
	return fileTarget ? [{ key: "file", source: String(task.sourceConfig?._originalName || "文件"), target: String(fileTarget) }] : [];
}
