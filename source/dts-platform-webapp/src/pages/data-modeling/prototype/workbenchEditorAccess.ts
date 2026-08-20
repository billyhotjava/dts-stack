import type { ModelDraft } from "./services/modelWorkbenchService";
import { statusLabel } from "@/utils/customerDisplayLabels";

export type WorkbenchEditorMode = "CREATE_DRAFT" | "EDIT_DRAFT" | "VIEW_VERSION" | "LEGACY_READONLY" | "NO_PERMISSION";

export type WorkbenchEditorAccess = {
	mode: WorkbenchEditorMode;
	readOnly: boolean;
	message: string;
};

export function resolveWorkbenchEditorAccess(canMaintain: boolean, draft: ModelDraft | null): WorkbenchEditorAccess {
	if (!canMaintain) {
		return {
			mode: "NO_PERMISSION",
			readOnly: true,
			message: "当前账号仅有查看权限，不能修改模型。",
		};
	}
	if (!draft) return { mode: "VIEW_VERSION", readOnly: true, message: "" };
	if (draft.createKind === "dimension") {
		if (!draft.definitionBase) return { mode: "CREATE_DRAFT", readOnly: false, message: "" };
		if (draft.definitionBase.status === "DRAFT") return { mode: "EDIT_DRAFT", readOnly: false, message: "" };
		if (draft.definitionBase.status === "CURRENT") {
			return {
				mode: "EDIT_DRAFT",
				readOnly: false,
				message: "当前维度定义为当前有效版本；保存修改将生成新的当前有效修订，已绑定模型继续使用原修订。",
			};
		}
		return {
			mode: "VIEW_VERSION",
			readOnly: true,
			message: `当前维度定义为${statusLabel(draft.definitionBase.status)}，只能查看。`,
		};
	}
	if (!draft.base) return { mode: "CREATE_DRAFT", readOnly: false, message: "" };
	if (draft.base.compatibilityMode === "LEGACY_READONLY") {
		return {
			mode: "LEGACY_READONLY",
			readOnly: true,
			message: "该模型使用历史兼容契约，只能查看，不能在当前工作台修改。",
		};
	}
	if (draft.base.status === "DRAFT") return { mode: "EDIT_DRAFT", readOnly: false, message: "" };
	return {
		mode: "VIEW_VERSION",
		readOnly: true,
		message:
			draft.base.status === "PUBLISHED"
				? "发布版本不可原地修改；创建新草稿版本后，可继续使用可视化或代码方式编辑。"
				: `当前模型版本为${statusLabel(draft.base.status)}，只能查看。`,
	};
}
