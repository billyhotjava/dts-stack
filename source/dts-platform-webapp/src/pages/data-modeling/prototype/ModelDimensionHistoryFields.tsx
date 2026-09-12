import type {
	ModelSpecDimensionHierarchy,
	ModelSpecScdPolicy,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import { dimensionProfileForSave } from "./services/modelDraftConfiguration";
import type { ModelSpecDraft } from "./services/modelWorkbenchService";

type Props = { draft: ModelSpecDraft; onChange: (patch: Partial<ModelSpecDraft>) => void };

export function ModelDimensionHistoryFields({ draft, onChange }: Props) {
	const profile = dimensionProfileForSave(draft) || { hierarchies: [], scdPolicy: { type: "NONE" as const } };
	const fields = draft.fields.filter((field) => field.name.trim());
	const fieldNames = new Set(fields.map((field) => field.name));
	const setPolicy = (policy: ModelSpecScdPolicy) =>
		onChange({ scdType: policy.type, dimensionProfile: { ...profile, scdPolicy: policy } });
	const setHierarchies = (hierarchies: ModelSpecDimensionHierarchy[]) =>
		onChange({ dimensionProfile: { ...profile, hierarchies } });
	const patchHierarchy = (index: number, patch: Partial<ModelSpecDimensionHierarchy>) =>
		setHierarchies(profile.hierarchies.map((hierarchy, i) => (i === index ? { ...hierarchy, ...patch } : hierarchy)));
	const options = (current: string) => (
		<>
			<option value="">请选择字段</option>
			{fields.map((field) => (
				<option key={field.name} value={field.name}>
					{field.displayName || field.name} · {field.name}
				</option>
			))}
			{current && !fieldNames.has(current) ? <option value={current}>已失效字段 · {current}</option> : null}
		</>
	);
	return (
		<section className="dmx-editor-panel">
			<h3>历史保留与层级</h3>
			<div className="dmx-workbench-editor__basic-grid">
				<label>
					<span>历史保留策略</span>
					<select
						aria-label="历史保留策略"
						value={draft.scdType}
						onChange={(event) => {
							const type = event.target.value as ModelSpecScdPolicy["type"];
							setPolicy(type === "TYPE2" ? { ...profile.scdPolicy, type } : { type });
						}}
					>
						<option value="NONE">不保留历史</option>
						<option value="TYPE1">覆盖更新</option>
						<option value="TYPE2">保留历史版本</option>
					</select>
				</label>
				{draft.scdType === "TYPE2" ? (
					<>
						{(
							[
								["effectiveFromField", "生效开始字段"],
								["effectiveToField", "生效结束字段"],
								["currentFlagField", "当前版本标志字段"],
							] as const
						).map(([key, label]) => {
							const current = profile.scdPolicy[key] || "";
							return (
								<label key={key}>
									<span>{label}</span>
									<select
										aria-label={label}
										value={current}
										onChange={(event) => setPolicy({ ...profile.scdPolicy, [key]: event.target.value || null })}
									>
										{options(current)}
									</select>
									{current && !fieldNames.has(current) ? (
										<small role="alert">字段已删除或重命名，请重新选择。</small>
									) : null}
								</label>
							);
						})}
						<p className="dmx-workbench-editor__wide-field">
							可保存历史字段配置。当前全量或增量构建不提供历史版本维护，保存配置不代表已启用该能力。
						</p>
					</>
				) : null}
			</div>
			{profile.hierarchies.map((hierarchy, index) => (
				<div className="dmx-workbench-editor__basic-grid" key={index}>
					<label>
						<span>层级编码</span>
						<input
							aria-label={`层级编码 ${index + 1}`}
							value={hierarchy.code}
							onChange={(event) => patchHierarchy(index, { code: event.target.value })}
						/>
					</label>
					<label>
						<span>层级名称</span>
						<input
							aria-label={`层级名称 ${index + 1}`}
							value={hierarchy.name}
							onChange={(event) => patchHierarchy(index, { name: event.target.value })}
						/>
					</label>
					{hierarchy.levels.map((level, levelIndex) => (
						<label key={levelIndex}>
							<span>第 {levelIndex + 1} 级字段</span>
							<select
								aria-label={`层级 ${index + 1} 第 ${levelIndex + 1} 级字段`}
								value={level.fieldName}
								onChange={(event) =>
									patchHierarchy(index, {
										levels: hierarchy.levels.map((item, i) =>
											i === levelIndex ? { ...item, fieldName: event.target.value } : item,
										),
									})
								}
							>
								{options(level.fieldName)}
							</select>
							<button
								type="button"
								onClick={() =>
									patchHierarchy(index, {
										levels: hierarchy.levels
											.filter((_, i) => i !== levelIndex)
											.map((item, i) => ({ ...item, order: i + 1 })),
									})
								}
							>
								移除此级
							</button>
						</label>
					))}
					<button
						type="button"
						onClick={() =>
							patchHierarchy(index, {
								levels: [...hierarchy.levels, { fieldName: "", order: hierarchy.levels.length + 1 }],
							})
						}
					>
						添加层级字段
					</button>
					<button type="button" onClick={() => setHierarchies(profile.hierarchies.filter((_, i) => i !== index))}>
						移除层级
					</button>
				</div>
			))}
			<button
				type="button"
				onClick={() => setHierarchies([...profile.hierarchies, { code: "", name: "", levels: [] }])}
			>
				添加层级
			</button>
		</section>
	);
}
