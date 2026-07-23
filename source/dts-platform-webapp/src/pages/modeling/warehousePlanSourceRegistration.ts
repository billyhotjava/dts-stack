import type { InfraDataSource } from "../../api/services/dataSourcesService.ts";
import type {
	WarehousePlanConfirmationStatus,
	WarehousePlanSourceBindingInput,
	WarehousePlanSourceBindingView,
} from "../../api/warehousePlanApi.ts";

export type VerifiedConnectionChoice = {
	value: string;
	label: string;
	verifiedAt: string;
};

export type CatalogTableSummary = {
	id?: string | null;
	name?: string | null;
	database?: string | null;
	schema?: string | null;
	columnCount?: number | null;
};

export type SelectChoice = {
	value: string;
	label: string;
};

export type ConnectionTableLocator = {
	connectionId: string;
	namespace: string;
	objectName: string;
};

export type WarehousePlanSourceDecisionDraft = {
	confirmationStatus: WarehousePlanConfirmationStatus;
	exclusionReason?: string | null;
};

export function sourceInventoryRequiresFurtherConfirmation(
	bindings: readonly Pick<WarehousePlanSourceBindingView, "confirmationStatus">[],
): boolean {
	return bindings.some((binding) => binding.confirmationStatus === "CANDIDATE");
}

const text = (value: unknown): string => (typeof value === "string" ? value.trim() : "");

const synchronizedColumnCount = (table: CatalogTableSummary): number =>
	typeof table.columnCount === "number" && Number.isFinite(table.columnCount) ? Math.max(0, table.columnCount) : 0;

export const catalogTableNamespace = (table: CatalogTableSummary): string => text(table.schema) || text(table.database);

export const connectionTableRegistrationKey = (namespace: string, objectName: string): string =>
	`${text(namespace).toLowerCase()}\u0000${text(objectName).toLowerCase()}`;

const connectionTableLocatorKey = (locator: ConnectionTableLocator): string =>
	`${text(locator.connectionId).toLowerCase()}\u0000${connectionTableRegistrationKey(locator.namespace, locator.objectName)}`;

export function selectableVerifiedConnections(
	dataSources: readonly InfraDataSource[] | null | undefined,
): VerifiedConnectionChoice[] {
	return (dataSources ?? [])
		.filter(
			(source) =>
				Boolean(text(source.id)) &&
				Boolean(text(source.name)) &&
				Boolean(text(source.jdbcUrl)) &&
				text(source.status).toUpperCase() === "ACTIVE" &&
				Boolean(text(source.lastVerifiedAt)) &&
				source.selectable !== false,
		)
		.map((source) => ({
			value: text(source.id),
			label: `${text(source.name)} · ${text(source.type) || "JDBC"}`,
			verifiedAt: text(source.lastVerifiedAt),
		}))
		.sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));
}

export function catalogSchemaOptions(tables: readonly CatalogTableSummary[] | null | undefined): SelectChoice[] {
	const namespaces = new Set<string>();
	for (const table of tables ?? []) {
		if (!text(table.id) || !text(table.name) || synchronizedColumnCount(table) === 0) continue;
		const namespace = catalogTableNamespace(table);
		if (namespace) namespaces.add(namespace);
	}
	return Array.from(namespaces)
		.sort((left, right) => left.localeCompare(right, "zh-CN"))
		.map((namespace) => ({ value: namespace, label: namespace }));
}

export function catalogTableChoices(
	tables: readonly CatalogTableSummary[] | null | undefined,
	registeredValues: ReadonlySet<string>,
	namespace: string,
): SelectChoice[] {
	const selectedNamespace = text(namespace);
	return (tables ?? [])
		.filter((table) => {
			const id = text(table.id);
			const name = text(table.name);
			const tableNamespace = catalogTableNamespace(table);
			return (
				Boolean(id) &&
				Boolean(name) &&
				synchronizedColumnCount(table) > 0 &&
				tableNamespace === selectedNamespace &&
				!registeredValues.has(id) &&
				!registeredValues.has(connectionTableRegistrationKey(tableNamespace, name))
			);
		})
		.map((table) => {
			const tableNamespace = catalogTableNamespace(table);
			const count = synchronizedColumnCount(table);
			return {
				value: text(table.id),
				label: `${tableNamespace}.${text(table.name)} · ${count} 个字段`,
			};
		})
		.sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));
}

export function appendConfirmedConnectionTableSource(
	existing: readonly WarehousePlanSourceBindingInput[],
	locator: ConnectionTableLocator,
): WarehousePlanSourceBindingInput[] {
	const connectionId = text(locator.connectionId);
	const namespace = text(locator.namespace);
	const objectName = text(locator.objectName);
	if (!connectionId || !namespace || !objectName) {
		throw new Error("connection table locator is required");
	}
	return [
		...existing,
		{
			sourceType: "CONNECTION_TABLE",
			locator: { connectionId, namespace, objectName },
			confirmationStatus: "CONFIRMED",
			exclusionReason: null,
		},
	];
}

export function rebaseWarehousePlanSourceDrafts(
	originalBindings: readonly WarehousePlanSourceBindingView[],
	localDrafts: readonly WarehousePlanSourceDecisionDraft[],
	latestBindings: readonly WarehousePlanSourceBindingView[],
): WarehousePlanSourceDecisionDraft[] {
	const localByBindingId = new Map<string, WarehousePlanSourceDecisionDraft>();
	for (const [index, binding] of originalBindings.entries()) {
		const local = localDrafts[index];
		const confirmationStatus = local?.confirmationStatus || binding.confirmationStatus;
		const exclusionReason = confirmationStatus === "EXCLUDED" ? text(local?.exclusionReason) || null : null;
		const originalExclusionReason =
			binding.confirmationStatus === "EXCLUDED" ? text(binding.exclusionReason) || null : null;
		if (confirmationStatus !== binding.confirmationStatus || exclusionReason !== originalExclusionReason) {
			localByBindingId.set(binding.bindingId, { confirmationStatus, exclusionReason });
		}
	}
	return latestBindings.map((binding) => {
		const local = localByBindingId.get(binding.bindingId);
		return local
			? { ...local }
			: {
					confirmationStatus: binding.confirmationStatus,
					exclusionReason: binding.exclusionReason ?? null,
				};
	});
}

export function mergeConfirmedConnectionTableSource(
	existing: readonly WarehousePlanSourceBindingInput[],
	latestBindings: readonly WarehousePlanSourceBindingView[],
	locator: ConnectionTableLocator,
): WarehousePlanSourceBindingInput[] {
	const pendingKey = connectionTableLocatorKey(locator);
	const matchingBinding = latestBindings.find(
		(binding) =>
			binding.sourceType === "CONNECTION_TABLE" &&
			connectionTableLocatorKey({
				connectionId: text(binding.locator?.connectionId),
				namespace: text(binding.locator?.namespace),
				objectName: text(binding.locator?.objectName),
			}) === pendingKey,
	);
	if (!matchingBinding) return appendConfirmedConnectionTableSource(existing, locator);
	return existing.map((binding) =>
		binding.bindingId === matchingBinding.bindingId
			? { ...binding, confirmationStatus: "CONFIRMED", exclusionReason: null }
			: { ...binding },
	);
}

export function mergeCandidateCatalogTableSource(
	existing: readonly WarehousePlanSourceBindingInput[],
	latestBindings: readonly WarehousePlanSourceBindingView[],
	assetId: string,
): WarehousePlanSourceBindingInput[] {
	const normalizedAssetId = text(assetId);
	if (!normalizedAssetId) throw new Error("catalog asset id is required");
	const alreadyRegistered = latestBindings.some(
		(binding) => binding.sourceType === "CATALOG_TABLE" && text(binding.locator?.assetId) === normalizedAssetId,
	);
	if (alreadyRegistered) return existing.map((binding) => ({ ...binding }));
	return [
		...existing,
		{
			sourceType: "CATALOG_TABLE",
			locator: { assetId: normalizedAssetId },
			confirmationStatus: "CANDIDATE",
			exclusionReason: null,
		},
	];
}
