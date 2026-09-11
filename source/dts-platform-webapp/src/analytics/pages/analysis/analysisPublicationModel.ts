import { CLASSIFICATION_LABELS_ZH, normalizeClassification } from "../../../utils/classification.ts";
import type { PublicationAudience, PublicationIssue } from "../../api/analysisApi";

const CLASSIFICATION_RANK = { PUBLIC: 0, INTERNAL: 1, SECRET: 2, CONFIDENTIAL: 3 } as const;

/** Lowest publication classification the dataset allows; null when the dataset carries none. */
export function publicationClassificationFloor(
	datasetClassification?: string | null,
): PublicationAudience["classification"] | null {
	const level = normalizeClassification(datasetClassification ?? undefined, undefined);
	return level ? (`DATA_${level}` as PublicationAudience["classification"]) : null;
}

/** Raises the chosen classification to the dataset floor; never lowers a stricter choice. */
export function atLeastClassification(
	chosen: PublicationAudience["classification"],
	floor: PublicationAudience["classification"] | null,
): PublicationAudience["classification"] {
	if (!floor) return chosen;
	const chosenLevel = normalizeClassification(chosen, undefined);
	const floorLevel = normalizeClassification(floor, undefined);
	if (!floorLevel) return chosen;
	if (!chosenLevel || CLASSIFICATION_RANK[chosenLevel] < CLASSIFICATION_RANK[floorLevel]) return floor;
	return chosen;
}

export function analysisPublicationIssueMessage(
	issue: PublicationIssue,
	dependencySnapshot?: Record<string, unknown> | null,
): string {
	switch (issue.code) {
		case "ANALYSIS_AUDIENCE_REQUIRED":
			return "请选择至少一个可见部门或可见角色。";
		case "ANALYSIS_EXPIRY_INVALID":
			return "有效期必须晚于当前时间。";
		case "ANALYSIS_CLASSIFICATION_DOWNGRADE": {
			const raw = dependencySnapshot?.classification;
			const level = normalizeClassification(typeof raw === "string" ? raw : undefined, undefined);
			return level
				? `发布密级不能低于数据集密级（${CLASSIFICATION_LABELS_ZH[level]}），请调整为“${CLASSIFICATION_LABELS_ZH[level]}”或更高。`
				: "发布密级不能低于数据集密级，请调高发布密级。";
		}
		default:
			return `分析依赖的数据集校验未通过（${issue.code}），请确认数据集已发布且契约未变化后重新校验。`;
	}
}
