// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import {
	isConceptDimensionDraft,
	isDimensionTableDraft,
	modelDraftFingerprint,
	resolveConceptDimensionPresentation,
	resolveDimensionFormPresentation,
} from "./modelWorkbenchPresentation";
import type { ConceptDimensionDraft, ModelSpecDraft } from "./services/modelWorkbenchService";

const makeDraft = (patch: Partial<ModelSpecDraft> = {}): ModelSpecDraft => ({
	createKind: "dimension-table",
	base: null,
	planId: "",
	domainId: "20000000-0000-0000-0000-000000000001",
	name: "预算科目维度表",
	description: "统一维护预算科目",
	physicalName: "dim_budget_account",
	materialization: "table",
	grainStatement: "",
	fields: [],
	partitionFields: "",
	loadStrategy: "FULL",
	scdType: "TYPE1",
	reuseScope: "DOMAIN",
	dimensionDefinitionId: "dimension-1",
	standardBindings: [],
	warehouseLayerCode: "DWD",
	implementationMode: "DESIGNER_GENERATED",
	implementationBase: null,
	implementationInputMode: "",
	generationStrategyType: "",
	implementationIdempotencyKey: "implementation-draft-1",
	creationOperationId: "create-draft-1",
	fieldMappings: [],
	casts: {},
	filters: [],
	deduplicateBy: [],
	joins: [],
	groupBy: [],
	aggregations: [],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	factShape: "",
	timeSemanticsType: "",
	timeSemanticsFields: [],
	consumptionScenario: "",
	...patch,
});

const makeConceptDraft = (patch: Partial<ConceptDimensionDraft> = {}): ConceptDimensionDraft => ({
	createKind: "dimension",
	base: null,
	definitionBase: null,
	idempotencyKey: "concept-draft-1",
	domainId: "finance",
	name: "预算科目",
	description: "统一预算科目定义",
	reuseScope: "DOMAIN",
	attributes: [],
	...patch,
});

describe("dimension workbench presentation", () => {
	it("derives dimension form labels from the selected domain and definition", () => {
		const view = resolveDimensionFormPresentation({
			draft: makeDraft({ physicalName: "dim_budget_account" }),
			domains: [
				{ id: "10000000-0000-0000-0000-000000000001", code: "business", name: "财务业务" },
				{ id: "20000000-0000-0000-0000-000000000001", code: "finance", name: "财务域", parentCode: "business" },
			] satisfies CatalogDomain[],
			definition: { id: "dimension-1", name: "预算科目", ownerId: "owner-1" } as DimensionDefinitionView,
			currentOwnerId: "current-user",
		});

		expect(view).toMatchObject({
			warehouseLayer: "公共层 / 维度层",
			businessCategory: "财务业务",
			tableNamingRule: "DIM 表命名规范",
			lifecycle: "未配置",
			owner: "owner-1",
		});
	});

	it("marks unavailable authority and invalid physical names as unconfigured", () => {
		const missing = resolveDimensionFormPresentation({
			draft: makeDraft({ physicalName: "Bad-Name" }),
			domains: [{ id: "20000000-0000-0000-0000-000000000001", code: "finance", name: "财务域" }],
			definition: null,
			currentOwnerId: "",
		});

		expect(missing.businessCategory).toBe("未配置");
		expect(missing.owner).toBe("未配置");
		expect(missing.tableNamingRule).toBe("不符合 DIM 表命名规范");
	});

	it("presents a new concept dimension with a server-generated code placeholder", () => {
		const view = resolveConceptDimensionPresentation({
			draft: makeConceptDraft({
				domainId: "20000000-0000-0000-0000-000000000001",
			}),
			domains: [
				{ id: "10000000-0000-0000-0000-000000000001", code: "business", name: "财务业务" },
				{ id: "20000000-0000-0000-0000-000000000001", code: "finance", name: "财务域", parentCode: "business" },
			],
		});

		expect(view).toEqual({
			warehouseLayer: "公共层 / 维度层",
			businessCategory: "财务业务",
			systemCode: "保存后生成",
		});
	});

	it("shows only the system code returned by a saved concept dimension", () => {
		const view = resolveConceptDimensionPresentation({
			draft: makeConceptDraft({
				domainId: "20000000-0000-0000-0000-000000000001",
				definitionBase: { systemCode: "dim_generated_001" } as DimensionDefinitionView,
			}),
			domains: [],
		});

		expect(view.systemCode).toBe("dim_generated_001");
		expect(view.businessCategory).toBe("未配置");
	});

	it("renders configured retention days from the saved implementation policy", () => {
		const view = resolveDimensionFormPresentation({
			draft: makeDraft({
				base: {
					implementationPolicy: { retentionDays: 30 },
				} as ModelSpecDraft["base"],
			}),
			domains: [],
			definition: null,
			currentOwnerId: "",
		});

		expect(view.lifecycle).toBe("30 天");
	});

	it("separates concept dimensions from dimension tables and fingerprints editable values only", () => {
		expect(isConceptDimensionDraft(makeConceptDraft())).toBe(true);
		expect(isConceptDimensionDraft(makeDraft())).toBe(false);
		expect(isDimensionTableDraft(makeDraft())).toBe(true);
		expect(isDimensionTableDraft(makeDraft({ createKind: "fact" }))).toBe(false);
		expect(modelDraftFingerprint(makeDraft({ name: "A" }))).not.toBe(modelDraftFingerprint(makeDraft({ name: "B" })));
		expect(modelDraftFingerprint(makeDraft({ businessProcessId: "process-a" }))).not.toBe(
			modelDraftFingerprint(makeDraft({ businessProcessId: "process-b" })),
		);
		const type2Profile = {
			hierarchies: [{ code: "region", name: "区域层级", levels: [{ fieldName: "province_id", order: 1 }] }],
			scdPolicy: {
				type: "TYPE2" as const,
				effectiveFromField: "effective_from",
				effectiveToField: "effective_to",
				currentFlagField: "is_current",
			},
		};
		expect(modelDraftFingerprint(makeDraft({ dimensionProfile: type2Profile }))).not.toBe(
			modelDraftFingerprint(
				makeDraft({
					dimensionProfile: { ...type2Profile, scdPolicy: { ...type2Profile.scdPolicy, effectiveFromField: "valid_from" } },
				}),
			),
		);
		expect(modelDraftFingerprint(makeDraft({ dimensionProfile: type2Profile }))).not.toBe(
			modelDraftFingerprint(
				makeDraft({
					dimensionProfile: {
						...type2Profile,
						hierarchies: [{ ...type2Profile.hierarchies[0], name: "行政区域层级" }],
					},
				}),
			),
		);
		expect(modelDraftFingerprint(makeDraft({ dimensionProfile: type2Profile }))).toBe(
			modelDraftFingerprint(makeDraft({ dimensionProfile: type2Profile })),
		);
		expect(
			modelDraftFingerprint(makeDraft({ fieldMappings: [{ sourceField: "src_0.id", targetField: "id" }] })),
		).not.toBe(modelDraftFingerprint(makeDraft()));
		expect(
			modelDraftFingerprint(
				makeDraft({
					implementationInputMode: "PHYSICAL_ASSET",
					sourceRefs: [
						{
							kind: "TABLE",
							ref: "ods_budget",
							layer: "ODS",
							role: "PRIMARY",
							sortOrder: 0,
							sourceBindingId: "50000000-0000-0000-0000-000000000001",
							resolvedVersion: "v1",
						},
					],
				}),
			),
		).not.toBe(modelDraftFingerprint(makeDraft()));
		expect(
			modelDraftFingerprint(
				makeConceptDraft({ attributes: [{ code: "A", name: "属性", primaryKey: true, order: 1 }] }),
			),
		).not.toBe(modelDraftFingerprint(makeConceptDraft()));
		expect(
			modelDraftFingerprint(
				makeConceptDraft({ attributes: [{ code: "A", name: "属性", primaryKey: false, order: 1 }] }),
			),
		).not.toBe(
			modelDraftFingerprint(
				makeConceptDraft({ attributes: [{ code: "A", name: "属性", primaryKey: true, order: 1 }] }),
			),
		);
		expect(modelDraftFingerprint(makeDraft({ planId: "plan-a" }))).toBe(
			modelDraftFingerprint(makeDraft({ planId: "plan-b" })),
		);
		expect(modelDraftFingerprint(makeDraft({ dataMartId: "mart-a" }))).not.toBe(
			modelDraftFingerprint(makeDraft({ dataMartId: "mart-b" })),
		);
		expect(modelDraftFingerprint(makeDraft({ subjectDomainId: "subject-a" }))).not.toBe(
			modelDraftFingerprint(makeDraft({ subjectDomainId: "subject-b" })),
		);
		expect(modelDraftFingerprint(makeConceptDraft({ name: "A" }))).not.toBe(
			modelDraftFingerprint(makeConceptDraft({ name: "B" })),
		);
	});
});
