import { Modal, message } from "antd";
import { findSimilarModels } from "@/api/modelingAccessApi";
import { modelDraftToUpdateCommand, type ModelSpecDraft } from "./services/modelWorkbenchService";
export async function confirmSimilarModel(draft: ModelSpecDraft): Promise<boolean> {
	const model = modelDraftToUpdateCommand(draft);
	if (!draft.planId || model.modelType === "APPLICATION") return true;
	try {
		const matches = await findSimilarModels({
			planId: draft.planId,
			modelType: model.modelType,
			businessProcessId: model.businessProcessId,
			sourceKeys: model.sourceRefs.map((source) => source.ref).join(",") || undefined,
			grainKeys: model.grain?.keys.join(",") || undefined,
			excludeModelSpecId: draft.base?.id,
		});
		if (!matches.length) return true;
		return await new Promise((resolve) =>
			Modal.confirm({
				title: "本部门已有相似公共层模型",
				width: 560,
				content: (
					<>
						<p>可先查看已有模型，确认是否仍需新建或修改。</p>
						<ul>
							{matches.map((model) => (
								<li key={model.modelSpecId}>
									<a
										href={`${window.location.pathname}?modelSpecId=${encodeURIComponent(model.modelSpecId)}`}
										target="_blank"
										rel="noreferrer"
									>
										{model.name}
									</a>
									：{model.matchReasons.join("、")}
								</li>
							))}
						</ul>
					</>
				),
				okText: "继续保存",
				cancelText: "返回编辑",
				onOk: () => resolve(true),
				onCancel: () => resolve(false),
			}),
		);
	} catch (error) {
		const status =
			(error as { response?: { status?: number }; status?: number })?.response?.status ??
			(error as { status?: number })?.status;
		if (status === 401 || status === 403 || status === 404) throw error;
		message.warning("相似模型提示暂不可用，本次可继续保存。");
		return true;
	}
}
