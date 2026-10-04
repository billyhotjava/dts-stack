import { sanitizeModelingReturnTo } from "@/features/modeling/navigation/modelingReturnPath";

/** The only data-management panel a modeling page may ask to open on arrival. */
export type ModelDataManagementFocus = "quality";

const MODELING_RETURN_PREFIX = "/data-modeling/";

export const resolveModelDataManagementFocus = (
	value: string | null | undefined,
): ModelDataManagementFocus | undefined => (value === "quality" ? "quality" : undefined);

/** A return link from the data module may only lead back to a modeling page. */
export const resolveModelingReturnTo = (value: string | null | undefined): string | undefined => {
	const safe = sanitizeModelingReturnTo(value);
	return safe?.startsWith(MODELING_RETURN_PREFIX) ? safe : undefined;
};

export const buildModelWorkbenchReturnUrl = (modelSpecId: string, environment: string, step = "verification") => {
	const params = new URLSearchParams({ modelSpecId, step, environment });
	return `/data-modeling/dimensions/workbench?${params.toString()}`;
};

/**
 * One direct cross-menu jump from a finished model to its data-management panel.
 * The target panel reads `focus` to open the matching section and `returnTo` to offer a single way back.
 */
export const buildModelDataManagementUrl = ({
	modelSpecId,
	environment,
	candidateId,
	focus,
	returnTo,
}: {
	modelSpecId: string;
	environment: string;
	candidateId?: string | null;
	focus?: ModelDataManagementFocus;
	returnTo?: string | null;
}) => {
	const params = new URLSearchParams({ view: "table", modelSpecId, environment });
	if (candidateId) params.set("candidateId", candidateId);
	if (focus) params.set("focus", focus);
	const back = resolveModelingReturnTo(returnTo);
	if (back) params.set("returnTo", back);
	return `/catalog/search?${params.toString()}`;
};
