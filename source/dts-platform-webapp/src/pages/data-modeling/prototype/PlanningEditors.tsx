import { Archive, CheckCircle2, Plus, Save } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
	confirmDataMart,
	createDataMart,
	listDataMarts,
	retireDataMart,
	updateDataMart,
} from "@/api/dataMartApi";
import { createBusinessProcessApi } from "@/api/sprint64GovernanceApi";
import {
	confirmSubjectDomain,
	createSubjectDomain,
	retireSubjectDomain,
	updateSubjectDomain,
} from "@/api/subjectDomainApi";
import { createWarehouseLayer, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import type { DataMartStatus, DataMartView } from "@/features/modeling/contracts/dataMartContract";
import type { SubjectDomainStatus, SubjectDomainView } from "@/features/modeling/contracts/subjectDomainContract";
import { Button } from "./PrototypePrimitives";
import { listPlanningCatalogDomains, type PlanningCatalogDomain } from "./services/planningCatalogDomainService";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

const DATA_MART_STATUS_LABEL: Record<DataMartStatus, string> = {
	DRAFT: "草稿",
	CURRENT: "已确认",
	RETIRED: "已退役",
};

const SUBJECT_DOMAIN_STATUS_LABEL: Record<SubjectDomainStatus, string> = {
	DRAFT: "草稿",
	CURRENT: "已确认",
	RETIRED: "已退役",
};

const SYSTEM_LAYER_GROUP: Record<string, string> = {
	ODS_RAW: "贴源层",
	ODS_STANDARDIZED: "贴源层",
	STG: "贴源层",
	DWD: "公共层",
	DWS: "公共层",
	ADS: "应用层",
};

const SYSTEM_LAYER_MODEL_TYPES: Record<string, string> = {
	ODS_RAW: "仅规划展示",
	ODS_STANDARDIZED: "仅规划展示",
	STG: "仅规划展示",
	DWD: "维度 / 维度表 / 明细表",
	DWS: "汇总表",
	ADS: "应用表 / 维度表",
};

const asWarehouseLayer = (value: unknown): WarehouseLayerView | null =>
	value && typeof value === "object" && "systemLayerCode" in value ? (value as WarehouseLayerView) : null;

const asDataMart = (value: unknown): DataMartView | null =>
	value && typeof value === "object" && "checksum" in value ? (value as DataMartView) : null;

const asSubjectDomain = (value: unknown): SubjectDomainView | null =>
	value && typeof value === "object" && "martId" in value ? (value as SubjectDomainView) : null;

export function BusinessProcessForm({
	canMaintain,
	onDone,
}: {
	initial: unknown;
	canMaintain: boolean;
	onDone: (message: string) => Promise<void>;
}) {
	const [domains, setDomains] = useState<PlanningCatalogDomain[]>([]);
	const [domainId, setDomainId] = useState("");
	const [processId, setProcessId] = useState("");
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		let active = true;
		void listPlanningCatalogDomains()
			.then((items) => {
				if (active) {
					const dataDomains = items.filter((item) => Boolean(item.parentId));
					setDomains(dataDomains);
					setDomainId((current) => current || dataDomains[0]?.id || "");
				}
			})
			.catch((cause) => {
				if (active) setError(normalizeModelingRequestFailure(cause, "数据域读取失败。").message);
			});
		return () => {
			active = false;
		};
	}, []);

	const create = async () => {
		if (!domainId || !processId.trim() || !name.trim()) return setError("请补齐数据域、英文缩写和中文名称");
		setBusy(true);
		setError("");
		try {
			await createBusinessProcessApi(domainId, {
				processId: processId.trim(),
				name: name.trim(),
				description: description.trim() || undefined,
			});
			await onDone("业务过程已创建");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "业务过程创建失败。").message);
		} finally {
			setBusy(false);
		}
	};

	return (
		<div className="dmx-planning-editor">
			<div className="dmx-form-grid">
				<label>
					<span className="required">英文缩写</span>
					<input
						disabled={!canMaintain || busy}
						onChange={(event) => setProcessId(event.target.value)}
						value={processId}
					/>
				</label>
				<label>
					<span className="required">中文名称</span>
					<input disabled={!canMaintain || busy} onChange={(event) => setName(event.target.value)} value={name} />
				</label>
				<label>
					<span className="required">数据域</span>
					<select disabled={!canMaintain || busy} onChange={(event) => setDomainId(event.target.value)} value={domainId}>
						<option value="">请选择</option>
						{domains.map((item) => (
							<option key={item.id} value={item.id}>
								{item.name} · {item.code}
							</option>
						))}
					</select>
				</label>
				<label className="dmx-form-field--wide">
					<span>业务定义</span>
					<textarea
						disabled={!canMaintain || busy}
						onChange={(event) => setDescription(event.target.value)}
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
				<Button disabled={!canMaintain || busy} primary onClick={() => void create()}>
					<Plus size={15} />
					{busy ? "处理中…" : "新建业务过程"}
				</Button>
			</div>
		</div>
	);
}

export function WarehouseLayerForm({
	canMaintain,
	onDone,
}: {
	initial: unknown;
	canMaintain: boolean;
	onDone: (message: string) => Promise<void>;
}) {
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [systemLayerCode, setSystemLayerCode] = useState("DWD");
	const [description, setDescription] = useState("");
	const [namingPrefix, setNamingPrefix] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");

	const create = async () => {
		const normalizedCode = code.trim().toLocaleUpperCase();
		const normalizedName = name.trim();
		const normalizedPrefix = namingPrefix.trim().toLocaleLowerCase();
		if (!/^[A-Z][A-Z0-9_]{1,63}$/.test(normalizedCode))
			return setError("分层编码只能包含大写字母、数字和下划线，且不能以数字开头");
		if (!normalizedName) return setError("分层名称不能为空");
		if (normalizedPrefix && !/^[a-z][a-z0-9_]{0,63}$/.test(normalizedPrefix)) {
			return setError("命名前缀只能包含小写字母、数字和下划线，且不能以数字开头");
		}
		setBusy(true);
		setError("");
		try {
			await createWarehouseLayer({
				code: normalizedCode,
				name: normalizedName,
				systemLayerCode: systemLayerCode as WarehouseLayerView["systemLayerCode"],
				description: description.trim() || undefined,
				namingPrefix: normalizedPrefix || undefined,
			});
			await onDone("数仓分层已创建");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "数仓分层创建失败。").message);
		} finally {
			setBusy(false);
		}
	};

	return (
		<div className="dmx-planning-editor">
			<div className="dmx-form-grid">
				<label>
					<span className="required">分层编码</span>
					<input
						disabled={!canMaintain || busy}
						onChange={(event) => setCode(event.target.value)}
						placeholder="例如：FIN_DETAIL"
						value={code}
					/>
				</label>
				<label>
					<span className="required">分层名称</span>
					<input
						disabled={!canMaintain || busy}
						onChange={(event) => setName(event.target.value)}
						placeholder="例如：财务明细层"
						value={name}
					/>
				</label>
				<label>
					<span className="required">所属系统类型</span>
					<select
						disabled={!canMaintain || busy}
						onChange={(event) => setSystemLayerCode(event.target.value)}
						value={systemLayerCode}
					>
						<option value="ODS_RAW">原始接入层 · ODS_RAW</option>
						<option value="ODS_STANDARDIZED">标准化接入层 · ODS_STANDARDIZED</option>
						<option value="STG">技术过渡层 · STG</option>
						<option value="DWD">明细事实 / 维度层 · DWD</option>
						<option value="DWS">汇总服务层 · DWS</option>
						<option value="ADS">应用服务层 · ADS</option>
					</select>
				</label>
				<label>
					<span>分层归属</span>
					<input disabled value={SYSTEM_LAYER_GROUP[systemLayerCode] || "—"} />
				</label>
				<label>
					<span>模型类型</span>
					<input disabled value={SYSTEM_LAYER_MODEL_TYPES[systemLayerCode] || "—"} />
				</label>
				<label>
					<span>命名前缀</span>
					<input
						disabled={!canMaintain || busy}
						onChange={(event) => setNamingPrefix(event.target.value)}
						placeholder="例如：fin_dwd_"
						value={namingPrefix}
					/>
				</label>
				<label className="dmx-form-field--wide">
					<span>加工责任</span>
					<textarea
						disabled={!canMaintain || busy}
						onChange={(event) => setDescription(event.target.value)}
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
				<Button disabled={!canMaintain || busy} primary onClick={() => void create()}>
					<Plus size={15} />
					{busy ? "处理中…" : "新建数仓分层"}
				</Button>
			</div>
		</div>
	);
}

export function DataMartForm({
	initial,
	canMaintain,
	ownerId,
	onDone,
}: {
	initial: unknown;
	canMaintain: boolean;
	ownerId: string;
	onDone: (message: string) => Promise<void>;
}) {
	const editing = useMemo(() => asDataMart(initial), [initial]);
	const [categories, setCategories] = useState<Array<{ id: string; code: string; name: string }>>([]);
	const [code, setCode] = useState(editing?.code || "");
	const [name, setName] = useState(editing?.name || "");
	const [purpose, setPurpose] = useState(editing?.purpose || "");
	const [businessCategoryId, setBusinessCategoryId] = useState(editing?.businessCategoryIds[0] || "");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		let active = true;
		void listPlanningCatalogDomains()
			.then((items) => {
				if (active) {
					const roots = items.filter((item) => !item.parentId).map((item) => ({ id: item.id, code: item.code, name: item.name }));
					setCategories(roots);
					setBusinessCategoryId((current) => current || roots[0]?.id || "");
				}
			})
			.catch((cause) => {
				if (active) setError(normalizeModelingRequestFailure(cause, "业务分类读取失败。").message);
			});
		return () => {
			active = false;
		};
	}, []);

	const save = async () => {
		if (!canMaintain) return setError("当前账号无数据集市维护权限");
		if (!ownerId) return setError("当前登录身份缺少人员 ID，不能维护数据集市");
		if (!code.trim() || !name.trim() || !purpose.trim() || !businessCategoryId) {
			return setError("请补齐编码、名称、用途和业务分类");
		}
		setBusy(true);
		setError("");
		try {
			if (editing)
				await updateDataMart(editing, {
					name: name.trim(),
					purpose: purpose.trim(),
					ownerId: editing.ownerId || ownerId,
					businessCategoryIds: [businessCategoryId],
				});
			else
				await createDataMart({
					code: code.trim(),
					name: name.trim(),
					purpose: purpose.trim(),
					ownerId,
					businessCategoryIds: [businessCategoryId],
					idempotencyKey: crypto.randomUUID(),
				});
			await onDone(editing ? "数据集市已更新" : "数据集市已创建");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "数据集市保存失败。").message);
		} finally {
			setBusy(false);
		}
	};

	const confirmPublish = async () => {
		if (!canMaintain) return setError("当前账号无数据集市确认权限");
		if (!editing || editing.status !== "DRAFT") return;
		if (!window.confirm(`确认发布数据集市“${editing.name}”？发布后可纳入建设规划基线。`)) return;
		setBusy(true);
		setError("");
		try {
			await confirmDataMart(editing);
			await onDone("数据集市已确认发布");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "数据集市确认发布失败。").message);
		} finally {
			setBusy(false);
		}
	};

	const retire = async () => {
		if (!canMaintain) return setError("当前账号无数据集市退役权限");
		if (!editing || !window.confirm(`确认退役数据集市“${editing.name}”？`)) return;
		setBusy(true);
		setError("");
		try {
			await retireDataMart(editing);
			await onDone("数据集市已退役");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "数据集市退役失败。").message);
		} finally {
			setBusy(false);
		}
	};

	return (
		<div className="dmx-planning-editor">
			{editing ? <div className="dmx-capability-note">状态：{DATA_MART_STATUS_LABEL[editing.status]}</div> : null}
			<div className="dmx-form-grid">
				<label>
					<span className="required">英文缩写</span>
					<input
						disabled={Boolean(editing) || !canMaintain || busy}
						onChange={(event) => setCode(event.target.value)}
						value={code}
					/>
				</label>
				<label>
					<span className="required">中文名称</span>
					<input disabled={!canMaintain || busy} onChange={(event) => setName(event.target.value)} value={name} />
				</label>
				<label>
					<span className="required">业务分类</span>
					<select
						disabled={!canMaintain || busy}
						onChange={(event) => setBusinessCategoryId(event.target.value)}
						value={businessCategoryId}
					>
						<option value="">请选择</option>
						{categories.map((item) => (
							<option key={item.id} value={item.id}>
								{item.name} · {item.code}
							</option>
						))}
					</select>
				</label>
				<label className="dmx-form-field--wide">
					<span className="required">用途说明</span>
					<textarea
						disabled={!canMaintain || busy}
						onChange={(event) => setPurpose(event.target.value)}
						value={purpose}
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
					{busy ? "处理中…" : editing ? "保存数据集市" : "新建数据集市"}
				</Button>
				{editing && editing.status === "DRAFT" ? (
					<Button disabled={!canMaintain || busy} onClick={() => void confirmPublish()}>
						<CheckCircle2 size={15} /> 确认发布
					</Button>
				) : null}
				{editing && editing.status === "CURRENT" ? (
					<Button danger disabled={!canMaintain || busy} onClick={() => void retire()}>
						<Archive size={15} /> 退役
					</Button>
				) : null}
			</div>
		</div>
	);
}

export function SubjectDomainForm({
	initial,
	canMaintain,
	onDone,
}: {
	initial: unknown;
	canMaintain: boolean;
	onDone: (message: string) => Promise<void>;
}) {
	const editing = useMemo(() => asSubjectDomain(initial), [initial]);
	const [marts, setMarts] = useState<Array<{ id: string; name: string; code: string }>>([]);
	const [code, setCode] = useState(editing?.code || "");
	const [name, setName] = useState(editing?.name || "");
	const [purpose, setPurpose] = useState(editing?.purpose || "");
	const [martId, setMartId] = useState(editing?.martId || "");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		let active = true;
		void listDataMarts({ limit: 100 })
			.then((items) => {
				if (active) {
					const options = items
						.filter((item) => item.status !== "RETIRED")
						.map((item) => ({ id: item.id, name: item.name, code: item.code }));
					setMarts(options);
					setMartId((current) => current || options[0]?.id || "");
				}
			})
			.catch((cause) => {
				if (active) setError(normalizeModelingRequestFailure(cause, "数据集市读取失败。").message);
			});
		return () => {
			active = false;
		};
	}, []);

	const save = async () => {
		if (!canMaintain) return setError("当前账号无主题域维护权限");
		if (!code.trim() || !name.trim() || !martId) return setError("请补齐编码、名称和数据集市");
		setBusy(true);
		setError("");
		try {
			if (editing)
				await updateSubjectDomain(editing, {
					name: name.trim(),
					purpose: purpose.trim() || undefined,
					martId,
				});
			else
				await createSubjectDomain({
					code: code.trim(),
					name: name.trim(),
					purpose: purpose.trim() || undefined,
					martId,
					idempotencyKey: crypto.randomUUID(),
				});
			await onDone(editing ? "主题域已更新" : "主题域已创建");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "主题域保存失败。").message);
		} finally {
			setBusy(false);
		}
	};

	const confirmPublish = async () => {
		if (!canMaintain) return setError("当前账号无主题域确认权限");
		if (!editing || editing.status !== "DRAFT") return;
		if (!window.confirm(`确认发布主题域“${editing.name}”？`)) return;
		setBusy(true);
		setError("");
		try {
			await confirmSubjectDomain(editing);
			await onDone("主题域已确认发布");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "主题域确认发布失败。").message);
		} finally {
			setBusy(false);
		}
	};

	const retire = async () => {
		if (!canMaintain) return setError("当前账号无主题域退役权限");
		if (!editing || !window.confirm(`确认退役主题域“${editing.name}”？`)) return;
		setBusy(true);
		setError("");
		try {
			await retireSubjectDomain(editing);
			await onDone("主题域已退役");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "主题域退役失败。").message);
		} finally {
			setBusy(false);
		}
	};

	return (
		<div className="dmx-planning-editor">
			{editing ? <div className="dmx-capability-note">状态：{SUBJECT_DOMAIN_STATUS_LABEL[editing.status]}</div> : null}
			<div className="dmx-form-grid">
				<label>
					<span className="required">主题域编码</span>
					<input
						disabled={Boolean(editing) || !canMaintain || busy}
						onChange={(event) => setCode(event.target.value)}
						placeholder="例如：BUDGET_COCKPIT"
						value={code}
					/>
				</label>
				<label>
					<span className="required">主题域名称</span>
					<input disabled={!canMaintain || busy} onChange={(event) => setName(event.target.value)} value={name} />
				</label>
				<label>
					<span className="required">数据集市</span>
					<select disabled={!canMaintain || busy} onChange={(event) => setMartId(event.target.value)} value={martId}>
						<option value="">请选择</option>
						{marts.map((item) => (
							<option key={item.id} value={item.id}>
								{item.name} · {item.code}
							</option>
						))}
					</select>
				</label>
				<label className="dmx-form-field--wide">
					<span>用途说明</span>
					<textarea
						disabled={!canMaintain || busy}
						onChange={(event) => setPurpose(event.target.value)}
						value={purpose}
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
					{busy ? "处理中…" : editing ? "保存主题域" : "新建主题域"}
				</Button>
				{editing && editing.status === "DRAFT" ? (
					<Button disabled={!canMaintain || busy} onClick={() => void confirmPublish()}>
						<CheckCircle2 size={15} /> 确认发布
					</Button>
				) : null}
				{editing && editing.status === "CURRENT" ? (
					<Button danger disabled={!canMaintain || busy} onClick={() => void retire()}>
						<Archive size={15} /> 退役
					</Button>
				) : null}
			</div>
		</div>
	);
}

export { asWarehouseLayer, asDataMart, asSubjectDomain };
