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
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const asPlanningDomain = (value: unknown): PlanningCatalogDomain | null => {
	if (!value || typeof value !== "object") return null;
	const candidate = value as Partial<PlanningCatalogDomain>;
	return candidate.id && candidate.code && candidate.name ? (candidate as PlanningCatalogDomain) : null;
};

/**
 * Drawer form for 业务分类 (root catalog domains) and 数据域 (child catalog domains).
 * Editing is driven by the selected row; the domain picker only offers business-category roots.
 */
export function CatalogDomainForm({
	view,
	initial,
	canMaintain,
	onDone,
}: {
	view: "business-categories" | "domains";
	initial: unknown;
	canMaintain: boolean;
	onDone: (message: string) => Promise<void>;
}) {
	const isCategory = view === "business-categories";
	const label = isCategory ? "业务分类" : "数据域";
	const editing = useMemo(() => asPlanningDomain(initial), [initial]);
	const [categories, setCategories] = useState<PlanningCatalogDomain[]>([]);
	const [code, setCode] = useState(editing?.code || "");
	const [name, setName] = useState(editing?.name || "");
	const [owner, setOwner] = useState(editing?.owner || "");
	const [description, setDescription] = useState(editing?.description || "");
	const [parentId, setParentId] = useState(editing?.parentId || "");
	const [categoriesLoaded, setCategoriesLoaded] = useState(isCategory);
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		if (isCategory) return;
		let active = true;
		void (async () => {
			try {
				const domains = await listPlanningCatalogDomains();
				if (!active) return;
				const roots = domains.filter((domain) => !domain.parentId);
				setCategories(roots);
				setParentId((current) => current || (roots.length === 1 ? roots[0].id : ""));
			} catch (cause) {
				if (active) setError(normalizeModelingRequestFailure(cause, "业务分类读取失败。").message);
			} finally {
				if (active) setCategoriesLoaded(true);
			}
		})();
		return () => {
			active = false;
		};
	}, [isCategory]);

	const save = async () => {
		if (!canMaintain) return setError("当前菜单未授权维护操作");
		if (!code.trim() || !name.trim()) return setError(`请补齐${label}编码和名称`);
		if (!isCategory && !parentId) return setError("请选择所属业务分类");
		setBusy(true);
		setError("");
		try {
			const input = {
				code: code.trim(),
				name: name.trim(),
				owner: owner.trim() || undefined,
				description: description.trim() || undefined,
				parentId: isCategory ? null : parentId,
			};
			if (editing) await updatePlanningCatalogDomain(editing.id, input);
			else await createPlanningCatalogDomain(input);
			await onDone(`${label}已${editing ? "更新" : "创建"}`);
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
			await onDone(`${label}已删除`);
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, `${label}删除失败，请先确认没有下级或关联对象。`).message);
		} finally {
			setBusy(false);
		}
	};

	return (
		<div className="dmx-planning-editor">
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
				{!isCategory && categoriesLoaded ? (
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
			{!isCategory && !categoriesLoaded ? <p className="dmx-capability-note">正在读取业务分类…</p> : null}
			{!isCategory && categoriesLoaded && categories.length === 1 && parentId ? (
				<p className="dmx-capability-note">
					已预选唯一业务分类“{categories.find((category) => category.id === parentId)?.name || parentId}
					”，仍可按实际归属调整。
				</p>
			) : null}
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
					<Button danger disabled={!canMaintain || busy} onClick={() => void remove()}>
						<Archive size={15} /> 删除
					</Button>
				) : null}
			</div>
		</div>
	);
}
