import api from "@/api/apiClient";
import type {
	ModelImplementationCasToken,
	ModelImplementationValidation,
	ModelImplementationView,
	ModelImplementationWriteCommand,
} from "@/pages/modeling/modelImplementationContract";
import { toModelImplementationEtag } from "@/pages/modeling/modelImplementationContract";
import { toModelSpecEtag, type ModelSpecCasToken } from "@/pages/modeling/modelSpecV2Contract";

const implementationUrl = (id: string, suffix = "") =>
	`/modeling/model-specs/${encodeURIComponent(id)}/implementation${suffix}`;

const versionHeaders = (expected: ModelSpecCasToken) => ({ "If-Match": toModelSpecEtag(expected) });
const writeHeaders = (expected: ModelSpecCasToken, implementation: ModelImplementationCasToken | null) => ({
	...versionHeaders(expected),
	"If-Match-Implementation": implementation ? toModelImplementationEtag(implementation) : "*",
});

export const saveModelImplementation = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken | null,
	data: ModelImplementationWriteCommand,
) =>
	api.put<ModelImplementationView>({
		url: implementationUrl(expected.id, "/inputs"),
		headers: writeHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);

export const validateModelImplementation = (expected: ModelSpecCasToken, data: ModelImplementationWriteCommand) =>
	api.post<ModelImplementationValidation>({
		url: implementationUrl(expected.id, "/inputs/validate"),
		headers: versionHeaders(expected),
		data,
		_skipErrorToast: true,
	} as any);

export const convertModelImplementationToDesignerGenerated = (
	expected: ModelSpecCasToken,
	implementation: ModelImplementationCasToken | null,
	data: ModelImplementationWriteCommand,
) =>
	api.post<ModelImplementationView>({
		url: implementationUrl(expected.id, "/convert-to-designer-generated"),
		headers: writeHeaders(expected, implementation),
		data,
		_skipErrorToast: true,
	} as any);
