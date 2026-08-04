import { Archive, Plus, Save } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Button } from "./PrototypePrimitives";
import {
	createPlanningCatalogDomain,
	deletePlanningCatalogDomain,
	listPlanningCatalogDomains,
	type PlanningCatalogDomain,
	updatePlanningCatalogDomain,
} from "./services/planningCatalogDomainService";
import { normalizeModelingRequestFailure, type PlanningProjection } from "./services/planningProjectionService";

type Changed = (message: string) => Promise<void>;

const asPlanningDomain = (value: unknown): PlanningCatalogDomain | null => {
	if (!value || typeof value !== "object") return null;
	const candidate = value as Partial<PlanningCatalogDomain>;
	return candidate.id && candidate.code && candidate.name ? (candidate as PlanningCatalogDomain) : null;
};

export function CatalogDomainEditor({
	view,
	rows,
	canMaintain,
	onChanged,
}: {
	view: "business-categories" | "domains";
	rows: PlanningProjection["rows"];
	canMaintain: boolean;
	onChanged: Changed;
}) {
	const isCategory = view === "business-categories";
	const label = isCategory ? "业务分类" : "数据域";
	const items = useMemo(
		() =>
			rows.map((row) => asPlanningDomain(row.source)).filter((item): item is PlanningCatalogDomain => Boolean(item)),
		[rows],
	);
	const [categories, setCategories] = useState<PlanningCatalogDomain[]>([]);
	const [editing, setEditing] = useState<PlanningCatalogDomain | null>(null);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [owner, setOwner] = useState("");
	const [description, setDescription] = useState("");
	const [parentId, setParentId] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		if (isCategory) return;
		let active = true;
		void listPlanningCatalogDomains()
			.then((domains) => {
				if (!active) return;
				const roots = domains.filter((domain) => !domain.parentId);
				setCategories(roots);
				setParentId((current) => current || roots[0]?.id || "");
			})
			.catch((cause) => {
				if (active) setError(normalizeModelingRequestFailure(cause, "业务分类读取失败。").message);
			});
		return () => {
			active = false;
		};
	}, [isCategory]);

	const reset = () => {
		setEditing(null);
		setCode("");
		setName("");
		setOwner("");
		setDescription("");
		setParentId(isCategory ? "" : categories[0]?.id || "");
		setError("");
	};

	const select = (item: PlanningCatalogDomain | null) => {
		if (!item) return reset();
		setEditing(item);
		setCode(item.code);
		setName(item.name);
		setOwner(item.owner);
		setDescription(item.description);
		setParentId(item.parentId || "");
		setError("");
	};

	const save = async () => {
		if (!canMaintain) return setError("当前菜单未授权维护操作");
		if (!code.trim() || !name.trim()) return setError(`请补齐${label}编码和名称`);
		if (!isCategory && !parentId) return setError("请选择所属业务分类");
		setBusy(true);
		setError("");
		try {
			const wasEditing = Boolean(editing);
			const input = {
				code: code.trim(),
				name: name.trim(),
				owner: owner.trim() || undefined,
				description: description.trim() || undefined,
				parentId: isCategory ? null : parentId,
			};
			if (editing) await updatePlanningCatalogDomain(editing.id, input);
			else await createPlanningCatalogDomain(input);
			reset();
			await onChanged(`${label}已${wasEditing ? "更新" : "创建"}`);
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, `${label}保存失败。`).message);
		} finally {
			setBusy(false);
		}
	};

	const remove = async () => {
		if (!canMaintain) return setError("当前菜单未授权维护操作");
		if (!editing || !window.confirm(`确认删除${label}“${editing.name}”？`)) return;
		setBusy(true);
		setError("");
		try {
			await deletePlanningCatalogDomain(editing.id);
			reset();
			await onChanged(`${label}已删除`);
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, `${label}删除失败，请先确认没有下级或关联对象。`).message);
		} finally {
			setBusy(false);
		}
	};

	return (
		<div className="dmx-planning-editor">
			<div className="dmx-planning-editor__heading">
				<strong>{editing ? `编辑${label}` : `新建${label}`}</strong>
				<select
					aria-label={`选择已有${label}`}
					onChange={(event) => select(items.find((item) => item.id === event.target.value) || null)}
					value={editing?.id || ""}
				>
					<option value="">新建{label}</option>
					{items.map((item) => (
						<option key={item.id} value={item.id}>
							{item.name} · {item.code}
						</option>
					))}
				</select>
			</div>
			<div className="dmx-form-grid">
				<label>
					<span className="required">{label}编码</span>
					<input
						disabled={Boolean(editing) || !canMaintain || busy}
						onChange={(e) => setCode(e.target.value)}
						value={code}
					/>
				</label>
				<label>
					<span className="required">{label}名称</span>
					<input disabled={!canMaintain || busy} onChange={(e) => setName(e.target.value)} value={name} />
				</label>
				{!isCategory ? (
					<label>
						<span className="required">所属业务分类</span>
						<select disabled={!canMaintain || busy} onChange={(e) => setParentId(e.target.value)} value={parentId}>
							<option value="">请选择</option>
							{categories.map((category) => (
								<option key={category.id} value={category.id}>
									{category.name} · {category.code}
								</option>
							))}
						</select>
					</label>
				) : null}
				<label>
					<span>负责人</span>
					<input disabled={!canMaintain || busy} onChange={(e) => setOwner(e.target.value)} value={owner} />
				</label>
				<label className="dmx-form-field--wide">
					<span>说明</span>
					<textarea
						disabled={!canMaintain || busy}
						onChange={(e) => setDescription(e.target.value)}
						value={description}
					/>
				</label>
			</div>
			{error ? (
				<div className="dmx-inline-error" role="alert">
					{error}
				</div>
			) : null}
			<div className="dmx-catalog-actions">
				<Button disabled={!canMaintain || busy} primary onClick={() => void save()}>
					{editing ? <Save size={15} /> : <Plus size={15} />}
					{busy ? "处理中…" : editing ? `保存${label}` : `新建${label}`}
				</Button>
				{editing ? (
					<>
						<Button disabled={busy} onClick={reset}>
							取消编辑
						</Button>
						<Button danger disabled={!canMaintain || busy} onClick={() => void remove()}>
							<Archive size={15} /> 删除
						</Button>
					</>
				) : null}
			</div>
		</div>
	);
}
