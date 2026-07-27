import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const pageUrl = new URL("./ModelSpecDetailPage.tsx", import.meta.url);
const logicalUrl = new URL("./components/ModelSpecLogicalDesignStage.tsx", import.meta.url);
const implementationUrl = new URL("./components/ModelSpecImplementationStage.tsx", import.meta.url);
const implementationPresentationUrl = new URL("./components/ModelSpecImplementationPresentation.tsx", import.meta.url);
const physicalUrl = new URL("./components/ModelSpecPhysicalAssetStage.tsx", import.meta.url);
const read = (url: URL) => readFileSync(url, "utf8");

test("three-stage detail isolates logical design, canonical implementation and physical evidence", () => {
	for (const url of [pageUrl, logicalUrl, implementationUrl, implementationPresentationUrl, physicalUrl]) {
		assert.equal(existsSync(url), true, `${url.pathname} is missing`);
	}
	const page = read(pageUrl);
	const logical = read(logicalUrl);
	const implementation = read(implementationUrl);
	const implementationPresentation = read(implementationPresentationUrl);
	const implementationSurface = `${implementation}\n${implementationPresentation}`;
	const physical = read(physicalUrl);

	assert.doesNotMatch(page, /ModelSpecEditorFields/);
	assert.match(page, /ModelSpecLogicalDesignStage/);
	assert.match(page, /ModelSpecImplementationStage/);
	assert.match(page, /ModelSpecPhysicalAssetStage/);
	assert.match(logical, /ModelSpecFieldsTab/);
	assert.match(logical, /ModelSpecStandardsTab/);
	assert.match(logical, /ModelSpecDependencyPanel/);
	assert.match(logical, /grainStatement|grainKeysText/);
	assert.match(logical, /factShape|timeSemanticsType|dimensionRefIds/);
	assert.match(logical, /dimensionScdType/);
	assert.doesNotMatch(
		logical,
		/name="physicalName"|name="loadStrategy"|name="retentionDays"|name="partitionFieldsText"/,
	);
	assert.match(logical, /timeFieldOptions/);
	assert.match(logical, /name="timeFieldNames"/);
	assert.match(logical, /mode="multiple"/);
	assert.doesNotMatch(logical, /sourceBindingId|saveModelImplementation/);
	assert.match(
		read(new URL("./components/ModelSpecFieldsTab.tsx", import.meta.url)),
		/name=\{\[field\.name, "displayName"\]\}/,
	);
	assert.match(implementation, /inputMode.*PHYSICAL_ASSET/s);
	assert.match(implementation, /model\.modelType === "DIMENSION"/);
	assert.match(implementation, /model\.modelType === "FACT"/);
	assert.doesNotMatch(implementation, /CONTROLLED_SEED/);
	assert.match(implementationSurface, /数据来源方式/);
	assert.match(implementation, /已登记的数据表/);
	assert.match(implementation, /已有模型输出/);
	assert.match(implementation, /系统生成（仅日期维度）/);
	assert.match(implementation, /generationStrategy\?\.type === "DATE_DIMENSION"/);
	assert.match(implementation, /canUseDateGenerator/);
	assert.match(implementation, /currentImplementation\?\.inputMode === "GENERATED"/);
	assert.match(implementationSurface, /name="inputIds"/);
	assert.match(implementationSurface, /mode="multiple".*inputOptions/s);
	assert.match(implementation, /settings\.joins/);
	assert.match(implementationSurface, /src_0、src_1/);
	assert.match(implementation, /inputIndex: index \+ 1/);
	assert.match(implementation, /input\.resolvedVersion/);
	assert.match(implementation, /input\.checksum/);
	assert.match(implementation, /generatedImplementationIdentity/);
	assert.match(implementation, /const projectKey = "dts"/);
	assert.match(
		implementation,
		/const implementationIdentity = useMemo\(\s*\(\) => generatedImplementationIdentity\(model\)/s,
	);
	assert.match(implementationPresentation, /高级信息：系统技术标识与预处理/);
	assert.doesNotMatch(implementation, /name="projectKey"|name="dbtUniqueId"/);
	assert.match(implementationSurface, /fieldMappings|deduplicateBy|castType|ownership/);
	assert.match(implementation, /initializeFieldMappings/);
	assert.match(implementationPresentation, /按模型字段初始化/);
	assert.match(implementationPresentation, /模型字段/);
	assert.match(implementationPresentation, /目标表摘要/);
	assert.doesNotMatch(implementationPresentation, /model\.implementationPolicy|来自逻辑设计，如需调整/);
	assert.match(implementation, /settings\.targetPhysicalName/);
	assert.match(implementation, /settings\.loadStrategy/);
	assert.match(implementation, /settings\.partitionFields/);
	assert.match(implementation, /settings\.retentionDays/);
	assert.match(implementationPresentation, /装载策略/);
	assert.match(implementationPresentation, /数据保留/);
	assert.match(implementation, /表（完整物化）/);
	assert.match(implementation, /视图（查询时计算）/);
	assert.match(implementation, /增量表（只处理变化）/);
	assert.doesNotMatch(implementationSurface, /Input\.TextArea|JSON\.parse/);
	assert.match(implementation, /persistedDraft/);
	assert.match(implementation, /ModelImplementationCasToken/);
	assert.match(implementation, /resolvePhysicalAssetImplementationInputs/);
	assert.match(implementation, /resolveUpstreamModelImplementationInputs/);
	assert.match(implementation, /isUpstreamModelImplementationPinned/);
	assert.match(implementation, /persistedUpstreamInputs/);
	assert.match(implementation, /incompletePersistedIds/);
	assert.match(implementation, /缺少完整实现 pin/);
	assert.match(implementation, /adoptedCurrentPinIdSet/);
	assert.match(implementation, /adoptCurrentPins/);
	assert.match(implementationSurface, />\s*采用当前版本\s*</);
	assert.match(implementationSurface, /已固定/);
	assert.match(implementationSurface, /待固定/);
	assert.match(implementationSurface, /采用当前实现 \/ 升级引用/);
	assert.match(implementationSurface, /普通保存会原样保留 modelSpec 与 implementation 六元 pin/);
	assert.match(implementationSurface, /管理规划来源/);
	assert.match(implementationPresentation, /系统管理.*ephemeral.*无物理表/s);
	assert.match(physical, /physicalAssetRef/);
	assert.match(physical, /latestPublicationEvent\?\.eventType === "RELEASE".*status === "PUBLISHED"/s);
	assert.match(physical, /event\.eventType === "RELEASE" \|\| event\.eventType === "ROLLBACK"/);
	assert.match(physical, /publishedAssetRefs/);
	assert.match(physical, /\/catalog\/datasets\/\$\{encodeURIComponent\(assetRef\)\}/);
	assert.match(physical, /页面不会根据输入来源或表名推断资产/);
	assert.match(physical, /构建产物已生成，但尚未发布为可消费资产/);
	assert.doesNotMatch(physical, /发布已完成，但部分资产登记尚未完成/);
	assert.match(physical, /compile|编译/);
	assert.match(physical, /血缘时间线/);
	assert.doesNotMatch(physical, /高级 dbt|onOpenAdvanced/);
	assert.match(implementationSurface, /进入高级 dbt 工作台/);
});

test("source and physical failures stay local while conflict and readonly preserve the current stage", () => {
	const page = read(pageUrl);
	const implementation = read(implementationUrl);
	const physical = read(physicalUrl);

	assert.doesNotMatch(page, /sourceVerificationPending/);
	assert.doesNotMatch(page, /disabled=\{sourceLoading\}/);
	assert.match(page, /activeStage !== "implementation"/);
	assert.match(page, /activeStage === "physical"/);
	assert.match(page, /setPhysicalError/);
	assert.match(page, /setPhysicalTimeline\(null\)/);
	assert.match(page, /Form\.useWatch\(\[\], \{ form, preserve: true \}\)/);
	assert.match(page, /form\.resetFields\(\)/);
	assert.match(page, /implementationUpstreamOptions/);
	assert.match(implementation, /response\?\.status === 409/);
	assert.match(implementation, /MODEL_IMPLEMENTATION_REVISION_CONFLICT/);
	assert.match(implementation, /getModelLifecycle/);
	assert.match(implementation, /rebaseImplementationCas/);
	assert.match(implementation, /setImplementationCas\(latest\)/);
	assert.match(implementation, /加载最新实现并保留当前输入/);
	assert.match(implementation, /operationRequestRef/);
	assert.doesNotMatch(implementation, /changeStage\(/);
	assert.match(page, /readOnly=\{!canEdit\}/);
	assert.match(page, /retryModelReleaseRegistration/);
	assert.match(physical, /latestPublicationEvent\.status === "PARTIAL".*latestPublicationEvent\.status === "PENDING"/s);
	assert.match(page, /max-\[390px\]:grid-cols-1/);
});
