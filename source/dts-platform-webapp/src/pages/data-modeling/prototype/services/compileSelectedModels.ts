import { compileModelLifecycle, getModelLifecycle, getModelSpec } from "@/api/modelSpecApi";
import type { CanonicalModelSpecView, ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";

const COMPILE_CONCURRENCY = 5;
const canonical = (model: ModelSpecView): model is CanonicalModelSpecView =>
	model.compatibilityMode === "CANONICAL" && model.contractVersion === 2;
export async function compileSelectedModels(
	models: CanonicalModelSpecView[],
	modelSpecIds = models.map((model) => model.id),
) {
	const known = new Map(models.map((model) => [model.id, model]));
	const plannedIds = Array.from(new Set(modelSpecIds));
	for (let offset = 0; offset < plannedIds.length; offset += COMPILE_CONCURRENCY) {
		const batch = await Promise.all(
			plannedIds.slice(offset, offset + COMPILE_CONCURRENCY).map(async (modelSpecId) => {
				const resolved = known.get(modelSpecId) || (await getModelSpec(modelSpecId));
				if (!canonical(resolved)) throw new Error("构建计划包含不可编译的历史模型，请刷新计划后重试。");
				return resolved;
			}),
		);
		await Promise.all(
			batch.map(async (model) => {
				const lifecycle = await getModelLifecycle(model.id);
				if (!lifecycle.implementation)
					throw new Error(
						plannedIds.length === 1
							? "当前模型尚未保存可编译的加工配置，请先保存加工配置后重试。"
							: `${model.name} 尚未保存可编译的加工配置，请先保存加工配置后重试。`,
					);
				await compileModelLifecycle(model, lifecycle.implementation, crypto.randomUUID());
			}),
		);
	}
}
