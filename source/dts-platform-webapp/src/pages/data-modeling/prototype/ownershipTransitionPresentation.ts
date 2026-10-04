import type { OwnershipTransitionValidation } from "@/api/modelImplementationTransitionApi";

export const ownershipTransitionSummary = (validation: OwnershipTransitionValidation, modelRevision: number, implementationRevision: number) => ({
	source: "可视化维护",
	target: "dbt 代码维护",
	modelRevision: `r${modelRevision}`,
	implementationRevision: `r${implementationRevision}`,
	previewChecksum: validation.previewChecksum.slice(0, 12),
	reversibility: validation.reversible ? "本版本可按后续转换规则处理。" : "接管后可视化字段将只读，本版本不可回退。",
	publish: "发布入口保持不变。",
});
