import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router";
import {
	getModelUpstreamAvailability,
	type ModelInputAvailability,
	type ModelUpstreamPin,
} from "@/api/modelInputInspectionApi";
import { isUpstreamModelImplementationPinned } from "@/features/modeling/contracts/modelImplementationContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { Button } from "./PrototypePrimitives";
import { reconcileModelInputIdentity } from "./services/modelInputIdentity";
import { adoptableUpstreamPins, withUpstreamPin } from "./services/upstreamPinAdoption";
import { implementationInputs, type ModelSpecDraft } from "./services/modelWorkbenchService";

export const inputReasonText = (reason?: string | null): string =>
	(
		({
			NOT_AVAILABLE: "来源不可读取或已不存在",
			IMPLEMENTATION_MISSING: "上游尚未提交加工配置",
			IMPLEMENTATION_INACTIVE: "上游加工不可用",
			IMPLEMENTATION_DESIGN_MISMATCH: "上游设计已更新，需要重新提交加工配置",
			DESIGN_REVISION_DRIFT: "上游设计版本已变化",
			DESIGN_CHECKSUM_DRIFT: "上游设计内容已变化",
			IMPLEMENTATION_REVISION_DRIFT: "上游加工版本已变化",
			IMPLEMENTATION_CHECKSUM_DRIFT: "上游加工内容已变化",
			DBT_ID_DRIFT: "上游加工标识已变化",
			CROSS_PLAN_UNPUBLISHED: "跨方案引用需要上游先发布",
			LAYER_NOT_ALLOWED: "不符合当前模型的引用范围",
			SELF_REFERENCE: "不能引用当前模型",
			DEPENDENCY_CYCLE: "该引用会形成循环",
			ARCHIVED: "上游版本已归档",
			CONTEXT_LIMIT_EXCEEDED: "依赖范围过大，请缩小查询范围",
			PIN_REQUIRED: "请明确选择上游加工版本",
			SOURCE_SCHEMA_UNAVAILABLE: "来源字段目录尚不可确认",
		}) as Record<string, string>
	)[reason || ""] || (reason ? "来源暂不可用，请刷新后检查" : "可引用");

type Props = { draft: ModelSpecDraft; candidates: ModelSpecView[]; onChange: (draft: ModelSpecDraft) => void };
export function ModelUpstreamSelector({ draft, candidates, onChange }: Props) {
	const [query, setQuery] = useState("");
	const [filter, setFilter] = useState("all");
	const [page, setPage] = useState(0);
	const [refresh, setRefresh] = useState(0);
	const [state, setState] = useState<{
		identity: string;
		items: Record<string, ModelInputAvailability>;
		error: string;
		loading: boolean;
	}>({ identity: "", items: {}, error: "", loading: false });
	const [pendingUpdate, setPendingUpdate] = useState<string | null>(null);
	const requestSequence = useRef(0);
	const resolved = implementationInputs(draft, { models: candidates });
	const pins = (resolved?.inputs || []).filter(
		(input): input is ModelUpstreamPin => "modelSpecId" in input && isUpstreamModelImplementationPinned(input),
	);
	const identity = `${draft.base?.id}:${draft.base?.revision}:${draft.base?.checksum}`;
	const requestKey = JSON.stringify({
		identity,
		ids: candidates.map((model) => model.id),
		pins,
		refresh,
		owner: draft.base
			? { ownerModelSpecId: draft.base.id, ownerRevision: draft.base.revision, ownerChecksum: draft.base.checksum }
			: null,
	});
	const items = state.identity === identity ? state.items : {};
	useEffect(() => {
		let timer: ReturnType<typeof setTimeout> | undefined;
		const update = () => {
			clearTimeout(timer);
			timer = setTimeout(() => setRefresh((value) => value + 1), 150);
		};
		const visible = () => {
			if (document.visibilityState === "visible") update();
		};
		window.addEventListener("focus", update);
		document.addEventListener("visibilitychange", visible);
		return () => {
			clearTimeout(timer);
			window.removeEventListener("focus", update);
			document.removeEventListener("visibilitychange", visible);
		};
	}, []);
	useEffect(() => {
		const seq = ++requestSequence.current;
		let cancelled = false;
		const {
			identity,
			ids: candidateIds,
			pins,
			owner,
		} = JSON.parse(requestKey) as {
			identity: string;
			ids: string[];
			pins: ModelUpstreamPin[];
			owner: { ownerModelSpecId: string; ownerRevision: number; ownerChecksum: string } | null;
		};
		if (!owner) return;
		const controller = new AbortController();
		setState((previous) => ({
			identity,
			items: previous.identity === identity ? previous.items : {},
			error: "",
			loading: true,
		}));
		const read = async () => {
			try {
				if (pins.length > 200) throw new Error("已选来源超过 200 项，请先缩小来源范围");
				const selectedIds = new Set(pins.map((pin) => pin.modelSpecId));
				const ids = candidateIds.filter((id) => !selectedIds.has(id));
				const batchSize = 200 - pins.length;
				if (batchSize === 0 && ids.length) throw new Error("已选来源已达到单次检查上限，请先减少来源");
				const collected: Record<string, ModelInputAvailability> = {};
				for (let offset = 0; offset < Math.max(1, ids.length); offset += Math.max(1, batchSize)) {
					if (cancelled) return;
					const result = await getModelUpstreamAvailability(
						{
							...owner,
							modelSpecIds: ids.slice(offset, offset + batchSize),
							selectedInputs: pins,
						},
						controller.signal,
					);
					for (const item of result.items) collected[item.modelSpecId] = item;
				}
				if (!cancelled && seq === requestSequence.current)
					setState({ identity, items: collected, error: "", loading: false });
			} catch (error) {
				if (!cancelled && seq === requestSequence.current)
					setState((previous) => ({
						...previous,
						loading: false,
						error: error instanceof Error ? error.message : "可用性读取失败，保存时将重新校验",
					}));
			}
		};
		void read();
		return () => {
			cancelled = true;
			controller.abort();
		};
	}, [requestKey]);
	// A selected upstream without an implementation pin cannot expose its fields; adopt the matching current pin once.
	const adoptedKey = useRef("");
	useEffect(() => {
		if (state.identity !== identity || state.loading) return;
		const adoptable = adoptableUpstreamPins(draft, items);
		if (!adoptable.length) return;
		const key = `${identity}:${JSON.stringify(adoptable)}`;
		if (adoptedKey.current === key) return;
		adoptedKey.current = key;
		onChange(reconcileModelInputIdentity(draft, adoptable.reduce(withUpstreamPin, draft)));
	}, [draft, identity, items, onChange, state.identity, state.loading]);
	const rows = useMemo(() => {
		const result = [...candidates];
		for (const selected of draft.dependsOn)
			if (!result.some((model) => model.id === selected.modelSpecId)) {
				result.push({
					id: selected.modelSpecId,
					name: "已选来源（当前不可读取）",
					revision: selected.revision,
				} as ModelSpecView);
			}
		return result.filter((model) => {
			const readable = items[model.id]?.blockReason !== "NOT_AVAILABLE";
			const label = readable ? model.name : "来源不可读取";
			return (
				label.toLowerCase().includes(query.toLowerCase()) &&
				(filter !== "selected" || draft.dependsOn.some((ref) => ref.modelSpecId === model.id)) &&
				(filter !== "available" || items[model.id]?.selectable)
			);
		});
	}, [candidates, draft.dependsOn, items, query, filter]);
	const change = (model: ModelSpecView, checked: boolean, update = false) => {
		const next = { ...draft };
		const previous = draft.authoringImplementationInputs || draft.implementationBase?.inputs || [];
		const pin = items[model.id]?.currentPin;
		if (!checked) {
			next.dependsOn = draft.dependsOn.filter((ref) => ref.modelSpecId !== model.id);
			next.authoringImplementationInputs = previous.filter(
				(input) => !("modelSpecId" in input) || input.modelSpecId !== model.id,
			);
		} else if (pin) {
			onChange(reconcileModelInputIdentity(draft, withUpstreamPin(draft, pin)));
			if (update) setPendingUpdate(null);
			return;
		} else {
			const selected = { modelSpecId: model.id, revision: model.revision, checksum: model.checksum };
			if (!selected.checksum) return;
			next.dependsOn = draft.dependsOn.some((ref) => ref.modelSpecId === model.id)
				? draft.dependsOn.map((ref) =>
						ref.modelSpecId === model.id ? { modelSpecId: model.id, revision: selected.revision } : ref,
					)
				: [...draft.dependsOn, { modelSpecId: model.id, revision: selected.revision }];
			next.authoringImplementationInputs = previous.some(
				(input) => "modelSpecId" in input && input.modelSpecId === model.id,
			)
				? previous.map((input) => ("modelSpecId" in input && input.modelSpecId === model.id ? selected : input))
				: [...previous, selected];
		}
		onChange(reconcileModelInputIdentity(draft, next));
		if (update) setPendingUpdate(null);
	};
	const currentPage = Math.min(page, Math.max(0, Math.ceil(rows.length / 10) - 1));
	return (
		<div className="dmx-workbench-editor__wide-field dmx-implementation-binding-list dmx-upstream-selector">
			<strong>上游模型</strong>
			<div className="dmx-upstream-selector__toolbar">
				<input
					aria-label="搜索上游模型"
					placeholder="搜索模型名称"
					value={query}
					onChange={(event) => {
						setQuery(event.target.value);
						setPage(0);
					}}
				/>
				<select
					aria-label="筛选上游模型"
					value={filter}
					onChange={(event) => {
						setFilter(event.target.value);
						setPage(0);
					}}
				>
					<option value="all">全部</option>
					<option value="available">可引用</option>
					<option value="selected">已选</option>
				</select>
				<Button onClick={() => setRefresh((value) => value + 1)}>刷新上游状态</Button>
			</div>
			{state.loading && <output>正在检查上游版本…</output>}
			{state.error && <small role="alert">{state.error}。已保留当前选择，可刷新后重试。</small>}
			{rows.slice(currentPage * 10, currentPage * 10 + 10).map((model) => {
				const item = items[model.id];
				const selected = draft.dependsOn.some((ref) => ref.modelSpecId === model.id);
				const readable = item?.blockReason !== "NOT_AVAILABLE" && Boolean(model.checksum);
				return (
					<div key={model.id} className="dmx-upstream-selector__row">
						<label className="dmx-upstream-selector__choice">
							<input
								type="checkbox"
								aria-label={`选择上游 ${readable ? model.name : "不可读取来源"}`}
								checked={selected}
								disabled={!selected && (item ? !item.selectable : state.loading || !state.error || !readable)}
								onChange={(event) => change(model, event.target.checked)}
							/>
							<span>{readable ? model.name : "来源不可读取"}</span>
							<small>
								{item
									? inputReasonText(selected ? item.referenceReason || item.blockReason : item.blockReason)
									: "尚未确认可用性"}
								{item?.currentPin
									? ` · 设计 r${item.currentPin.revision} / 加工 i${item.currentPin.implementationRevision}`
									: ""}
							</small>
						</label>
						{selected && item?.selectedPin && (
							<small>
								当前引用：设计 r{item.selectedPin.revision} / 加工 i{item.selectedPin.implementationRevision}
							</small>
						)}
						{selected && item?.selectable && (item.referenceReason || !item.selectedPin) && (
							<Button onClick={() => setPendingUpdate(model.id)}>
								{item.selectedPin ? "更新引用" : "选择加工版本"}
							</Button>
						)}
						{readable && (
							<Link
								to={`/data-modeling/dimensions/workbench?modelSpecId=${encodeURIComponent(model.id)}`}
								target="_blank"
								rel="noreferrer"
							>
								打开上游完善
							</Link>
						)}
						{pendingUpdate === model.id && (
							<div role="alert" className="dmx-upstream-selector__confirm">
								更新后将重新检查字段映射，其他编辑内容保留。
								<Button onClick={() => change(model, true, true)}>确认更新引用</Button>
								<Button onClick={() => setPendingUpdate(null)}>取消</Button>
							</div>
						)}
					</div>
				);
			})}
			{!rows.length && <small>没有符合当前筛选条件的上游模型。</small>}
			{rows.length > 10 && (
				<div className="dmx-upstream-selector__toolbar">
					<Button disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>
						上一页
					</Button>
					<span>
						第 {currentPage + 1} 页 / 共 {Math.ceil(rows.length / 10)} 页
					</span>
					<Button disabled={(currentPage + 1) * 10 >= rows.length} onClick={() => setPage(currentPage + 1)}>
						下一页
					</Button>
				</div>
			)}
		</div>
	);
}
