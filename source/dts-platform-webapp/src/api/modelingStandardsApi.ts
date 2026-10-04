// Dedicated data-modeling facade. The professional owners remain in platformApi;
// this file only gives the modeling workspace a narrow, typed import boundary.
import { listMetadataStandards as listMetadataStandardsRequest } from "./platformApi";

export type ModelFieldStandardOption = {
	id: string;
	code: string;
	name: string;
	dataType: string | null;
	version: number;
};

type MetadataStandardsPayload =
	| Array<Record<string, unknown>>
	| { content?: Array<Record<string, unknown>> }
	| null
	| undefined;

export async function listModelFieldStandardOptions(): Promise<ModelFieldStandardOption[]> {
	const payload = (await listMetadataStandardsRequest({ page: 0, size: 500 })) as MetadataStandardsPayload;
	const rows = Array.isArray(payload) ? payload : payload?.content || [];
	return rows
		.map((row) => ({
			id: String(row.id || "").trim(),
			code: String(row.fieldNameEn || "").trim(),
			name: String(row.fieldNameCn || row.fieldNameEn || "").trim(),
			dataType: row.dataType == null ? null : String(row.dataType),
			version: Number(row.version),
		}))
		.filter((option) => Boolean(option.id && option.code && option.name) && option.version > 0);
}

export { listMetadataStandardsRequest as listMetadataStandards };

export {
	applyStandardPackageImport,
	archiveStandard,
	createGlossaryTerm,
	createReferenceCode,
	createStandard,
	createWordRoot,
	deleteGlossaryTerm,
	getGlossaryTermReferences,
	getMetadataStandardReferences,
	getReferenceCode,
	getReferenceCodeReferences,
	getStandard,
	listGlossaryTermReviews,
	listGlossaryTerms,
	listGlossaryTermVersions,
	listMeasurementUnits,
	listReferenceCodes,
	listStandards,
	listStandardVersions,
	listWordRoots,
	previewStandardPackageImport,
	updateGlossaryTerm,
	updateReferenceCode,
	updateStandard,
	updateWordRoot,
} from "./platformApi";
