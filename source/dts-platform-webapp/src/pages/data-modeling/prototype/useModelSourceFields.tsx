import { useEffect, useState } from "react";
import { getModelInputFields, type ModelInputFieldSource } from "@/api/modelInputInspectionApi";
import { inputReasonText } from "./ModelUpstreamSelector";
import {
	implementationInputs,
	type ModelSpecDraft,
	type ModelWorkbenchContext,
} from "./services/modelWorkbenchService";

export function useModelSourceFields(draft: ModelSpecDraft, context: ModelWorkbenchContext) {
	const input = implementationInputs(draft, { models: context.models });
	const [reload, setReload] = useState(0);
	const key = JSON.stringify({
		reload,
		request:
			draft.base && input
				? {
						ownerModelSpecId: draft.base.id,
						ownerRevision: draft.base.revision,
						ownerChecksum: draft.base.checksum,
						...input,
					}
				: null,
	});
	const [state, setState] = useState<{
		key: string;
		sources: ModelInputFieldSource[];
		error: string;
		loading: boolean;
	}>({ key: "", sources: [], error: "", loading: false });
	useEffect(() => {
		let cancelled = false;
		const { request } = JSON.parse(key) as { request: Parameters<typeof getModelInputFields>[0] | null };
		if (!request || request.inputMode === "GENERATED") return;
		const controller = new AbortController();
		setState({ key, sources: [], error: "", loading: true });
		void getModelInputFields(request, controller.signal)
			.then((result) => {
				if (!cancelled)
					setState({
						key,
						sources: result.sources,
						error: result.issues.map((issue) => inputReasonText(issue.reason)).join("；"),
						loading: false,
					});
			})
			.catch(() => {
				if (!cancelled)
					setState({ key, sources: [], error: "字段目录读取失败，已保留编辑内容，请重试", loading: false });
			});
		return () => {
			cancelled = true;
			controller.abort();
		};
	}, [key]);
	return {
		...(state.key === key ? state : { key, sources: [], error: "", loading: false }),
		refresh: () => setReload((value) => value + 1),
	};
}

type Props = {
	label: string;
	value: string;
	onChange: (value: string) => void;
	sources: ModelInputFieldSource[];
	labels: string[];
	disabled?: boolean;
};
export function ModelSourceFieldInput({ label, value, onChange, sources, labels, disabled }: Props) {
	const options = sources.flatMap((source) =>
		source.fields.map((field) => ({
			value: `${source.alias}.${field.name}`,
			label: `${labels[source.index] || source.alias} / ${field.name} · ${field.dataType}`,
		})),
	);
	const id = `source-fields-${label.replace(/[^\p{L}\p{N}]/gu, "_")}`;
	const known = options.some(
		(option) => option.value === value || (sources.length === 1 && option.value === `src_0.${value}`),
	);
	const missing =
		Boolean(value) &&
		!disabled &&
		sources.length > 0 &&
		sources.every((source) => source.schemaState === "RESOLVED") &&
		!known;
	return (
		<div>
			<input
				aria-label={label}
				aria-invalid={missing}
				value={value}
				disabled={disabled}
				list={id}
				placeholder="选择来源字段，也可输入修改"
				onChange={(event) => onChange(event.target.value)}
			/>
			<datalist id={id}>
				{options.map((option) => (
					<option key={option.value} value={option.value} label={option.label} />
				))}
			</datalist>
			{missing && (
				<small role="alert">
					{value.startsWith("src_2147483647.") ? "原来源已移除，请重新选择字段" : "字段不在当前来源目录中，请检查选择"}
				</small>
			)}
		</div>
	);
}
