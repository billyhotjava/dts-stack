import { Archive, Plus, RotateCw, Save, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createDataMart, retireDataMart, updateDataMart } from "@/api/dataMartApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import {
	createBusinessProcessApi,
	deleteBusinessProcessApi,
	type Sprint64BusinessProcess,
} from "@/api/sprint64GovernanceApi";
import { saveWarehousePlanPolicy, type WarehousePlanPolicyInput } from "@/api/warehousePlanApi";
import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";
import { useCatalogMaintainerAccess } from "@/hooks/useModuleManageAccess";
import { useUserInfo } from "@/store/userStore";
import type { DataModelingRoute } from "../types";
import { Button, PageHeader, RequestState, Status, Toast, useTransientMessage } from "./PrototypePrimitives";
import { type DataMartDomainOption, loadDataMartDomainOptions } from "./services/dataMartDomainOptions";
import {
	loadPlanningProjection,
	normalizeModelingRequestFailure,
	type PlanningProjection,
} from "./services/planningProjectionService";

const emptyPolicy: WarehousePlanPolicyInput = {
	layerScheme: null,
	namingPolicy: null,
	historyPolicy: null,
	defaultTimeZone: "Asia/Shanghai",
	conceptualDesignAllowed: true,
	standardCoverage: "KEY_AND_MEASURE",
	qualityGate: "BLOCKING",
};

const ownerIdOf = (userInfo: unknown) => {
	if (!userInfo || typeof userInfo !== "object") return "";
	const value = (userInfo as Record<string, unknown>).id;
	return value == null ? "" : String(value).trim();
};

export function PlanningPage({ route }: { route: DataModelingRoute }) {
	const canMaintain = useCatalogMaintainerAccess();
	const userInfo = useUserInfo();
	const requestEpoch = useRef(0);
	const [projection, setProjection] = useState<PlanningProjection | null>(null);
	const [loading, setLoading] = useState(true);
	const [failure, setFailure] = useState<{ kind: "permission" | "request"; message: string } | null>(null);
	const [query, setQuery] = useState("");
	const [selectedPlanId, setSelectedPlanId] = useState("");
	const previousView = useRef(route.view);
	const { message, show } = useTransientMessage();
	const load = useCallback(async () => {
		const epoch = ++requestEpoch.current;
		setLoading(true);
		setFailure(null);
		try {
			const next = await loadPlanningProjection(route.view, selectedPlanId || undefined);
			if (requestEpoch.current !== epoch) return;
			setProjection(next);
			if (route.view === "system" && !selectedPlanId && next.selectedPlan) setSelectedPlanId(next.selectedPlan.id);
		} catch (error) {
			if (requestEpoch.current !== epoch) return;
			setProjection(null);
			setFailure(normalizeModelingRequestFailure(error, `${route.title}读取失败，请稍后重新加载。`));
		} finally {
			if (requestEpoch.current === epoch) setLoading(false);
		}
	}, [route.title, route.view, selectedPlanId]);
	useEffect(() => {
		void load();
		return () => {
			requestEpoch.current += 1;
		};
	}, [load]);
	useEffect(() => {
		if (previousView.current !== route.view) {
			setQuery("");
			setSelectedPlanId("");
			previousView.current = route.view;
		}
	}, [route.view]);

	const visibleRows = useMemo(() => {
		const normalized = query.trim().toLocaleLowerCase();
		if (!normalized) return projection?.rows || [];
		return (projection?.rows || []).filter((row) =>
			row.cells.some((cell) => cell.toLocaleLowerCase().includes(normalized)),
		);
	}, [projection?.rows, query]);

	return (
		<main className="dmx-page dmx-catalog-page">
			<PageHeader
				actions={
					<Button disabled={loading} onClick={() => void load()}>
						<RotateCw size={15} />
						刷新
					</Button>
				}
				description={route.description}
				title={route.title}
				trail="数据建模 / 数仓规划"
			/>
			{loading ? (
				<RequestState description={`正在读取${route.title}权威数据。`} kind="loading" title="正在加载" />
			) : failure ? (
				<RequestState
					description={failure.message}
					kind={failure.kind === "permission" ? "permission" : "error"}
					onRetry={failure.kind === "request" ? () => void load() : undefined}
					title={failure.kind === "permission" ? "无权访问" : "读取失败"}
				/>
			) : projection ? (
				<section className="dmx-catalog-panel">
					{route.view === "processes" ? (
						<BusinessProcessEditor
							canMaintain={canMaintain}
							onChanged={async (result) => {
								show(result);
								await load();
							}}
							rows={projection.rows}
						/>
					) : route.view === "marts" ? (
						<DataMartEditor
							canMaintain={canMaintain}
							onChanged={async (result) => {
								show(result);
								await load();
							}}
							ownerId={ownerIdOf(userInfo)}
							rows={projection.rows}
						/>
					) : route.view === "system" ? (
						<PolicyEditor
							canMaintain={canMaintain}
							onChanged={async (result) => {
								show(result);
								await load();
							}}
							onPlanChange={setSelectedPlanId}
							projection={projection}
							selectedPlanId={selectedPlanId}
						/>
					) : projection.readOnlyReason ? (
						<div className="dmx-planning-unavailable">
							<Button disabled>
								<Plus size={14} />
								新建{route.title}
							</Button>
							<div className="dmx-capability-note">{projection.readOnlyReason}</div>
						</div>
					) : null}
					{projection.headers.length ? (
						<>
							<div className="dmx-list-toolbar">
								<label>
									<Search size={15} />
									<input
										onChange={(event) => setQuery(event.target.value)}
										placeholder="搜索名称、编码或说明"
										value={query}
									/>
								</label>
								<span>共 {visibleRows.length} 条</span>
							</div>
							<div className="dmx-table-scroll">
								<table className="dmx-table dmx-table--catalog">
									<thead>
										<tr>
											{projection.headers.map((header) => (
												<th key={header}>{header}</th>
											))}
										</tr>
									</thead>
									<tbody>
										{visibleRows.map((row) => (
											<tr key={row.id}>
												{row.cells.map((cell, index) => (
													<td key={`${row.id}-${index}`}>
														{["已发布", "已确认", "启用"].includes(cell) ? (
															<Status tone="success">{cell}</Status>
														) : ["草稿", "候选"].includes(cell) ? (
															<Status tone="warning">{cell}</Status>
														) : (
															cell
														)}
													</td>
												))}
											</tr>
										))}
									</tbody>
								</table>
							</div>
							{!visibleRows.length ? (
								<RequestState description="当前 owner 未返回任何记录。" kind="empty" title={`暂无${route.title}`} />
							) : null}
						</>
					) : route.view !== "system" ? (
						<RequestState
							description={projection.readOnlyReason || "当前 owner 未返回可展示记录。"}
							kind="empty"
							title={`暂无${route.title}`}
						/>
					) : null}
				</section>
			) : null}
			<Toast message={message} />
		</main>
	);
}

function BusinessProcessEditor({
	rows,
	canMaintain,
	onChanged,
}: {
	rows: PlanningProjection["rows"];
	canMaintain: boolean;
	onChanged: (message: string) => Promise<void>;
}) {
	const [domains, setDomains] = useState<CatalogDomain[]>([]);
	const [domainId, setDomainId] = useState("");
	const [processId, setProcessId] = useState("");
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	useEffect(() => {
		let active = true;
		void catalogDomainService
			.list()
			.then((items) => {
				if (active) {
					setDomains(items);
					setDomainId((current) => current || items[0]?.code || "");
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
			setProcessId("");
			setName("");
			setDescription("");
			await onChanged("业务过程已创建");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "业务过程创建失败。").message);
		} finally {
			setBusy(false);
		}
	};
	const remove = async (process: Sprint64BusinessProcess) => {
		if (!window.confirm(`确认删除业务过程“${process.name}”？`)) return;
		setBusy(true);
		setError("");
		try {
			await deleteBusinessProcessApi(process.domainId, process.processId);
			await onChanged("业务过程已删除");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "业务过程删除失败。").message);
		} finally {
			setBusy(false);
		}
	};
	return (
		<div className="dmx-planning-editor">
			<div className="dmx-planning-editor__heading">
				<strong>新建业务过程</strong>
				<span>真实 owner：governance/sprint64</span>
			</div>
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
					<select
						disabled={!canMaintain || busy}
						onChange={(event) => setDomainId(event.target.value)}
						value={domainId}
					>
						<option value="">请选择</option>
						{domains.map((item) => (
							<option key={item.code} value={item.code}>
								{item.name}
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
				{!canMaintain ? <span className="dmx-capability-note">当前账号无规划维护权限。</span> : null}
				<select
					aria-label="删除业务过程"
					disabled={!canMaintain || busy}
					onChange={(event) => {
						const row = rows.find((item) => item.id === event.target.value);
						if (row?.source) void remove(row.source as Sprint64BusinessProcess);
					}}
					value=""
				>
					<option value="">选择要删除的业务过程…</option>
					{rows.map((row) => (
						<option key={row.id} value={row.id}>
							{row.cells[1]} · {row.cells[2]}
						</option>
					))}
				</select>
			</div>
		</div>
	);
}

function DataMartEditor({
	rows,
	canMaintain,
	ownerId,
	onChanged,
}: {
	rows: PlanningProjection["rows"];
	canMaintain: boolean;
	ownerId: string;
	onChanged: (message: string) => Promise<void>;
}) {
	const [domains, setDomains] = useState<DataMartDomainOption[]>([]);
	const [editing, setEditing] = useState<DataMartView | null>(null);
	const [code, setCode] = useState("");
	const [name, setName] = useState("");
	const [purpose, setPurpose] = useState("");
	const [domainId, setDomainId] = useState("");
	const [busy, setBusy] = useState(false);
	const [error, setError] = useState("");
	useEffect(() => {
		let active = true;
		void loadDataMartDomainOptions()
			.then((items) => {
				if (active) {
					setDomains(items);
					setDomainId((current) => current || items[0]?.id || "");
				}
			})
			.catch((cause) => {
				if (active) setError(normalizeModelingRequestFailure(cause, "数据域读取失败。").message);
			});
		return () => {
			active = false;
		};
	}, []);
	const reset = () => {
		setEditing(null);
		setCode("");
		setName("");
		setPurpose("");
		setDomainId(domains[0]?.id || "");
		setError("");
	};
	const select = (mart: DataMartView | null) => {
		if (!mart) return reset();
		setEditing(mart);
		setCode(mart.code);
		setName(mart.name);
		setPurpose(mart.purpose);
		setDomainId(mart.domainIds[0] || "");
		setError("");
	};
	const save = async () => {
		if (!canMaintain) return setError("当前账号无数据集市维护权限");
		if (!ownerId) return setError("当前登录身份缺少人员 ID，不能维护数据集市");
		if (!code.trim() || !name.trim() || !purpose.trim() || !domainId) return setError("请补齐编码、名称、用途和数据域");
		setBusy(true);
		setError("");
		try {
			if (editing)
				await updateDataMart(editing, {
					name: name.trim(),
					purpose: purpose.trim(),
					ownerId: editing.ownerId || ownerId,
					domainIds: [domainId],
				});
			else
				await createDataMart({
					code: code.trim(),
					name: name.trim(),
					purpose: purpose.trim(),
					ownerId,
					domainIds: [domainId],
					idempotencyKey: crypto.randomUUID(),
				});
			reset();
			await onChanged(editing ? "数据集市已更新" : "数据集市已创建");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "数据集市保存失败。").message);
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
			reset();
			await onChanged("数据集市已退役");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "数据集市退役失败。").message);
		} finally {
			setBusy(false);
		}
	};
	const marts = rows
		.map((row) => row.source)
		.filter((item): item is DataMartView => Boolean(item && "checksum" in item));
	return (
		<div className="dmx-planning-editor">
			<div className="dmx-planning-editor__heading">
				<strong>{editing ? "编辑数据集市" : "新建数据集市"}</strong>
				<select
					aria-label="选择已有数据集市"
					onChange={(event) => select(marts.find((item) => item.id === event.target.value) || null)}
					value={editing?.id || ""}
				>
					<option value="">新建数据集市</option>
					{marts.map((item) => (
						<option key={item.id} value={item.id}>
							{item.name}
						</option>
					))}
				</select>
			</div>
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
					<span className="required">数据域</span>
					<select
						disabled={!canMaintain || busy}
						onChange={(event) => setDomainId(event.target.value)}
						value={domainId}
					>
						<option value="">请选择</option>
						{domains.map((item) => (
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
				{editing ? (
					<>
						<Button disabled={busy} onClick={reset}>
							取消编辑
						</Button>
						<Button
							danger
							disabled={!canMaintain || busy || editing.status === "RETIRED"}
							onClick={() => void retire()}
						>
							<Archive size={15} />
							退役
						</Button>
					</>
				) : null}
			</div>
		</div>
	);
}

function PolicyEditor({
	projection,
	selectedPlanId,
	onPlanChange,
	onChanged,
	canMaintain,
}: {
	projection: PlanningProjection;
	selectedPlanId: string;
	onPlanChange: (planId: string) => void;
	onChanged: (message: string) => Promise<void>;
	canMaintain: boolean;
}) {
	const [draft, setDraft] = useState<WarehousePlanPolicyInput>(emptyPolicy);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");
	useEffect(() => {
		if (!projection.policy) return setDraft(emptyPolicy);
		const { readiness: _readiness, issues: _issues, ...value } = projection.policy.value;
		setDraft(value);
	}, [projection.policy]);
	const save = async () => {
		if (!canMaintain) return setError("当前账号无规划参数维护权限");
		if (!projection.selectedPlan || !projection.policy) return;
		setSaving(true);
		setError("");
		try {
			await saveWarehousePlanPolicy(projection.selectedPlan.id, projection.policy.version, draft);
			await onChanged("规划参数已保存");
		} catch (cause) {
			setError(normalizeModelingRequestFailure(cause, "规划参数保存失败，请重试。").message);
		} finally {
			setSaving(false);
		}
	};
	if (!projection.plans.length) {
		return <RequestState description="请先在建模空间创建建设计划。" kind="empty" title="暂无建设计划" />;
	}
	return (
		<div className="dmx-planning-editor">
			{!canMaintain ? <div className="dmx-capability-note">当前账号只有规划参数查看权限。</div> : null}
			<div className="dmx-planning-editor__heading">
				<strong>规划参数</strong>
				<select
					aria-label="建设计划"
					onChange={(event) => onPlanChange(event.target.value)}
					value={selectedPlanId || projection.selectedPlan?.id || ""}
				>
					{projection.plans.map((plan) => (
						<option key={plan.id} value={plan.id}>
							{plan.name}
						</option>
					))}
				</select>
			</div>
			<div className="dmx-form-grid">
				<PolicySelect
					disabled={!canMaintain || saving}
					label="分层策略"
					onChange={(value) =>
						setDraft((current) => ({ ...current, layerScheme: value as typeof current.layerScheme }))
					}
					options={["", "CLASSIC_ODS_DWD_DWS_ADS"]}
					value={draft.layerScheme || ""}
				/>
				<PolicySelect
					disabled={!canMaintain || saving}
					label="命名策略"
					onChange={(value) =>
						setDraft((current) => ({ ...current, namingPolicy: value as typeof current.namingPolicy }))
					}
					options={["", "CLASSIC_LOWER_SNAKE", "CLASSIC_UPPER_SNAKE"]}
					value={draft.namingPolicy || ""}
				/>
				<PolicySelect
					disabled={!canMaintain || saving}
					label="历史策略"
					onChange={(value) =>
						setDraft((current) => ({ ...current, historyPolicy: value as typeof current.historyPolicy }))
					}
					options={["", "PRESERVE_BUSINESS_HISTORY", "LATEST_STATE_ONLY"]}
					value={draft.historyPolicy || ""}
				/>
				<PolicySelect
					disabled={!canMaintain || saving}
					label="标准覆盖"
					onChange={(value) =>
						setDraft((current) => ({ ...current, standardCoverage: value as typeof current.standardCoverage }))
					}
					options={["NONE", "KEY_AND_MEASURE", "ALL_FIELDS"]}
					value={draft.standardCoverage}
				/>
				<PolicySelect
					disabled={!canMaintain || saving}
					label="质量门禁"
					onChange={(value) =>
						setDraft((current) => ({ ...current, qualityGate: value as typeof current.qualityGate }))
					}
					options={["ADVISORY", "BLOCKING"]}
					value={draft.qualityGate}
				/>
				<label>
					<span>默认时区</span>
					<input
						disabled={!canMaintain || saving}
						onChange={(event) => setDraft((current) => ({ ...current, defaultTimeZone: event.target.value }))}
						value={draft.defaultTimeZone || ""}
					/>
				</label>
			</div>
			{projection.policy?.value.issues.length ? (
				<div className="dmx-capability-note">
					{projection.policy.value.issues.map((issue) => issue.message).join("；")}
				</div>
			) : null}
			{error ? (
				<div className="dmx-inline-error" role="alert">
					{error}
				</div>
			) : null}
			<div className="dmx-catalog-actions">
				<Button disabled={!canMaintain || saving || !projection.policy} primary onClick={() => void save()}>
					<Save size={15} />
					{saving ? "保存中…" : "保存参数"}
				</Button>
			</div>
		</div>
	);
}

function PolicySelect({
	label,
	value,
	options,
	onChange,
	disabled,
}: {
	label: string;
	value: string;
	options: string[];
	onChange: (value: string) => void;
	disabled: boolean;
}) {
	return (
		<label>
			<span>{label}</span>
			<select disabled={disabled} onChange={(event) => onChange(event.target.value)} value={value}>
				{options.map((option) => (
					<option key={option || "empty"} value={option}>
						{option || "请选择"}
					</option>
				))}
			</select>
		</label>
	);
}
