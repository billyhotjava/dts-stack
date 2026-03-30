// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useEffect, useMemo, useState } from "react";
import { analyticsApi, type ExploreSessionItem } from "../api/analyticsApi";
import { PageContainer, PageHeader } from "../components/PageContainer/PageContainer";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import { Input, Spin, Button, Card, Tag } from "antd";
type LoadState<T> =
	| { state: "loading" }
	| { state: "loaded"; value: T }
	| { state: "error"; error: unknown };

type SessionReplay = {
	sessionId?: number | string;
	stepIndex?: number;
	step?: Record<string, unknown>;
	replayStatus?: string;
	message?: string;
};

function parseTagsCsv(raw: string): string[] {
	return raw
		.split(",")
		.map((item) => item.trim())
		.filter((item) => item.length > 0);
}

function parseJsonObject(raw: string): Record<string, unknown> | null {
	const trimmed = raw.trim();
	if (!trimmed) return {};
	try {
		const value = JSON.parse(trimmed);
		if (value && typeof value === "object" && !Array.isArray(value)) {
			return value as Record<string, unknown>;
		}
		return null;
	} catch {
		return null;
	}
}

function formatTime(raw?: string): string {
	if (!raw) return "-";
	const time = new Date(raw);
	if (Number.isNaN(time.getTime())) return raw;
	return time.toLocaleString();
}

function toIdString(value?: string | number): string {
	return value == null ? "" : String(value);
}

export default function ExploreSessionsPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);

	const [includeArchived, setIncludeArchived] = useState(false);
	const [sessions, setSessions] = useState<LoadState<ExploreSessionItem[]>>({ state: "loading" });
	const [selectedId, setSelectedId] = useState<string>("");
	const [selectedSession, setSelectedSession] = useState<LoadState<ExploreSessionItem> | null>(null);

	const [title, setTitle] = useState("");
	const [question, setQuestion] = useState("");
	const [projectKey, setProjectKey] = useState("");
	const [dept, setDept] = useState("");
	const [tags, setTags] = useState("");

	const [stepTitle, setStepTitle] = useState("");
	const [stepType, setStepType] = useState("action");
	const [stepParams, setStepParams] = useState("{}");
	const [replayIndex, setReplayIndex] = useState(0);
	const [replayResult, setReplayResult] = useState<SessionReplay | null>(null);

	const [saving, setSaving] = useState(false);
	const [actionError, setActionError] = useState<unknown>(null);
	const [actionMessage, setActionMessage] = useState<string>("");

	const loadSessions = async (keepSelection = true) => {
		try {
			setActionError(null);
			setSessions({ state: "loading" });
			const rows = await analyticsApi.listExploreSessions({
				includeArchived,
				limit: 200,
			});
			const safeRows = Array.isArray(rows) ? rows : [];
			setSessions({ state: "loaded", value: safeRows });
			if (!keepSelection) {
				setSelectedId("");
				setSelectedSession(null);
				return;
			}
			if (safeRows.length === 0) {
				setSelectedId("");
				setSelectedSession(null);
				return;
			}
			const matched = selectedId && safeRows.some((item) => toIdString(item.id) === selectedId);
			const nextId = matched ? selectedId : toIdString(safeRows[0]?.id);
			setSelectedId(nextId);
		} catch (e) {
			setSessions({ state: "error", error: e });
		}
	};

	const loadSelectedSession = async (id: string) => {
		if (!id) {
			setSelectedSession(null);
			return;
		}
		try {
			setSelectedSession({ state: "loading" });
			const value = await analyticsApi.getExploreSession(id);
			setSelectedSession({ state: "loaded", value });
		} catch (e) {
			setSelectedSession({ state: "error", error: e });
		}
	};

	useEffect(() => {
		void loadSessions(false);
	}, [includeArchived]);

	useEffect(() => {
		if (!selectedId) return;
		void loadSelectedSession(selectedId);
	}, [selectedId]);

	const currentSession = selectedSession?.state === "loaded" ? selectedSession.value : null;
	const sessionList = sessions.state === "loaded" ? sessions.value : [];

	const createSession = async () => {
		if (saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			const created = await analyticsApi.createExploreSession({
				title: title.trim() || "未命名分析会话",
				question: question.trim() || null,
				projectKey: projectKey.trim() || null,
				dept: dept.trim() || null,
				tags: parseTagsCsv(tags),
				steps: [],
			});
			setTitle("");
			setQuestion("");
			setProjectKey("");
			setDept("");
			setTags("");
			await loadSessions(false);
			const id = toIdString(created.id);
			if (id) {
				setSelectedId(id);
				await loadSelectedSession(id);
			}
			setActionMessage("分析会话已创建");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const updateConclusion = async () => {
		if (!currentSession?.id || saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			await analyticsApi.updateExploreSession(currentSession.id, {
				conclusion: currentSession.conclusion ?? "",
			});
			await loadSelectedSession(toIdString(currentSession.id));
			await loadSessions();
			setActionMessage("结论已更新");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const appendStep = async () => {
		if (!currentSession?.id || saving) return;
		const params = parseJsonObject(stepParams);
		if (params == null) {
			setActionError(new Error("步骤参数必须是 JSON 对象"));
			return;
		}
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			await analyticsApi.appendExploreSessionStep(currentSession.id, {
				title: stepTitle.trim() || "未命名步骤",
				type: stepType.trim() || "action",
				params,
			});
			setStepTitle("");
			setStepParams("{}");
			await loadSelectedSession(toIdString(currentSession.id));
			await loadSessions();
			setActionMessage("步骤已追加");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const replayStep = async () => {
		if (!currentSession?.id || saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			const replay = await analyticsApi.replayExploreSessionStep(currentSession.id, Math.max(0, replayIndex));
			setReplayResult(replay as SessionReplay);
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const archiveSession = async () => {
		if (!currentSession?.id || saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			await analyticsApi.archiveExploreSession(currentSession.id);
			setActionMessage("会话已归档");
			await loadSessions(false);
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const cloneSession = async () => {
		if (!currentSession?.id || saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			const cloned = await analyticsApi.cloneExploreSession(currentSession.id);
			await loadSessions(false);
			const id = toIdString(cloned.id);
			if (id) {
				setSelectedId(id);
				await loadSelectedSession(id);
			}
			setActionMessage("会话已复制");
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	const shareSession = async () => {
		if (!currentSession?.id || saving) return;
		setSaving(true);
		setActionError(null);
		setActionMessage("");
		try {
			const link = await analyticsApi.createExploreSessionPublicLink(currentSession.id);
			const uuid = String(link.uuid || "").trim();
			if (!uuid) {
				throw new Error("分享链接创建失败");
			}
			const url = `${window.location.origin}/bi/api/explore-session/public/${encodeURIComponent(uuid)}`;
			if (navigator.clipboard?.writeText) {
				await navigator.clipboard.writeText(url);
			}
			setActionMessage("分享链接已复制");
			await loadSelectedSession(toIdString(currentSession.id));
			await loadSessions();
		} catch (e) {
			setActionError(e);
		} finally {
			setSaving(false);
		}
	};

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "explore.title")}
				actions={
					<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-sm)" }}>
						<label style={{ display: "inline-flex", gap: "var(--spacing-xs)", alignItems: "center", fontSize: "var(--font-size-sm)" }}>
							<input
								type="checkbox"
								checked={includeArchived}
								onChange={(event) => setIncludeArchived(event.target.checked)}
							/>
							包含归档
						</label>
						<Button type="default" onClick={() => void loadSessions()}>
							{t(locale, "common.refresh")}
						</Button>
					</div>
				}
			/>

			{Boolean(actionError) ? <ErrorNotice locale={locale} error={actionError} /> : null}
			{actionMessage && (
				<div style={{ marginBottom: "var(--spacing-md)" }}>
					<Tag color="success">{actionMessage}</Tag>
				</div>
			)}

			<div className="grid grid-cols-2 gap-md" style={{ marginBottom: "var(--spacing-lg)" }}>
				<Card title="新建分析会话">
						<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>{t(locale, "common.name")}</label>
								<Input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="产线告警波动复盘" />
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>问题描述</label>
								<Input.TextArea value={question} onChange={(event) => setQuestion(event.target.value)} rows={3} />
							</div>
							<div className="grid grid-cols-2 gap-md">
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>项目标识</label>
									<Input value={projectKey} onChange={(event) => setProjectKey(event.target.value)} placeholder="project-a" />
								</div>
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>部门</label>
									<Input value={dept} onChange={(event) => setDept(event.target.value)} placeholder="制造一部" />
								</div>
							</div>
							<div>
								<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>标签（逗号分隔）</label>
								<Input value={tags} onChange={(event) => setTags(event.target.value)} placeholder="产线,告警,复盘" />
							</div>
						</div>
					<div style={{ display: "flex", justifyContent: "flex-end", paddingTop: "var(--spacing-md)", marginTop: "var(--spacing-md)", borderTop: "1px solid var(--color-border)" }}>
						<Button type="primary" onClick={createSession} loading={saving}>
							创建会话
						</Button>
					</div>
				</Card>

				<Card
					title="会话列表"
					extra={sessions.state === "loaded" ? <Tag>{sessions.value.length}</Tag> : null}
				>
						{sessions.state === "loading" && (
							<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
								<Spin />
							</div>
						)}
						{sessions.state === "error" && <ErrorNotice locale={locale} error={sessions.error} />}
						{sessions.state === "loaded" && sessions.value.length === 0 && (
							<div className="text-secondary">{t(locale, "common.empty")}</div>
						)}
						{sessions.state === "loaded" && sessions.value.length > 0 && (
							<table>
								<thead>
									<tr>
										<th>{t(locale, "common.name")}</th>
										<th>步骤</th>
										<th>更新时间</th>
									</tr>
								</thead>
								<tbody>
									{sessions.value.map((row, idx) => {
										const id = toIdString(row.id);
										const active = id === selectedId;
										return (
											<tr
												key={`${id || "row"}-${idx}`}
												onClick={() => setSelectedId(id)}
												style={{ cursor: "pointer", background: active ? "var(--color-bg-hover)" : undefined }}
											>
												<td>
													<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-xs)" }}>
														<span>{row.title || "未命名会话"}</span>
														{row.archived ? <Tag>archived</Tag> : null}
													</div>
												</td>
												<td>{row.stepCount ?? (Array.isArray(row.steps) ? row.steps.length : 0)}</td>
												<td>{formatTime(row.updatedAt)}</td>
											</tr>
										);
									})}
								</tbody>
							</table>
						)}
				</Card>
			</div>

			<Card
				title={<span>会话详情 <span style={{ color: "var(--color-text-secondary)", fontSize: "var(--font-size-sm)", fontWeight: "normal" }}>{currentSession ? `#${currentSession.id}` : "选择左侧会话查看详情"}</span></span>}
				extra={
						currentSession ? (
							<div style={{ display: "flex", gap: "var(--spacing-xs)", flexWrap: "wrap" }}>
								<Button type="text" onClick={cloneSession} loading={saving}>复制</Button>
								<Button type="text" onClick={shareSession} loading={saving}>分享</Button>
								<Button danger onClick={archiveSession} loading={saving}>归档</Button>
							</div>
						) : null
					}
			>
					{selectedSession?.state === "loading" && (
						<div className="loading-container" style={{ padding: "var(--spacing-lg)" }}>
							<Spin />
						</div>
					)}
					{selectedSession?.state === "error" && <ErrorNotice locale={locale} error={selectedSession.error} />}
					{currentSession && (
						<div className="flex flex-col gap-md">
							<div className="grid grid-cols-2 gap-md">
								<div>
									<div className="text-sm text-secondary">问题</div>
									<div>{currentSession.question || "-"}</div>
								</div>
								<div>
									<div className="text-sm text-secondary">标签</div>
									<div style={{ display: "flex", gap: "var(--spacing-xs)", flexWrap: "wrap" }}>
										{Array.isArray(currentSession.tags) && currentSession.tags.length > 0
											? currentSession.tags.map((tag, idx) => <Tag key={`${String(tag)}-${idx}`}>{String(tag)}</Tag>)
											: <span>-</span>}
									</div>
								</div>
							</div>

							<div className="grid grid-cols-2 gap-md">
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>结论</label>
									<Input.TextArea
										value={String(currentSession.conclusion ?? "")}
										onChange={(event) => {
											if (selectedSession?.state !== "loaded") return;
											setSelectedSession({
												state: "loaded",
												value: { ...selectedSession.value, conclusion: event.target.value },
											});
										}}
										rows={4}
									/>
								</div>
								<div className="flex flex-col gap-md" style={{ gap: "var(--spacing-sm)" }}>
									<div>
										<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>追加步骤标题</label>
										<Input value={stepTitle} onChange={(event) => setStepTitle(event.target.value)} placeholder="筛选华北区域并下钻" />
									</div>
									<div>
										<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>步骤类型</label>
										<Input value={stepType} onChange={(event) => setStepType(event.target.value)} placeholder="action" />
									</div>
									<div>
										<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>步骤参数(JSON)</label>
										<Input.TextArea value={stepParams} onChange={(event) => setStepParams(event.target.value)} rows={4} />
									</div>
								</div>
							</div>

							<div style={{ display: "flex", gap: "var(--spacing-sm)", alignItems: "center", flexWrap: "wrap" }}>
								<Button type="default" onClick={updateConclusion} loading={saving}>更新结论</Button>
								<Button type="primary" onClick={appendStep} loading={saving}>追加步骤</Button>
								<div>
									<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>重放步骤序号</label>
									<Input
										type="number"
										min={0}
										value={String(replayIndex)}
										onChange={(event) => setReplayIndex(Number.parseInt(event.target.value, 10) || 0)}
										style={{ width: 160 }}
									/>
								</div>
								<Button type="text" onClick={replayStep} loading={saving}>执行重放</Button>
							</div>

							<div>
								<div className="text-sm text-secondary" style={{ marginBottom: "var(--spacing-xs)" }}>步骤明细</div>
								<pre style={{
									margin: 0,
									padding: "var(--spacing-sm)",
									background: "var(--color-bg-tertiary)",
									borderRadius: "var(--radius-sm)",
									whiteSpace: "pre-wrap",
								}}>
									{JSON.stringify(currentSession.steps ?? [], null, 2)}
								</pre>
							</div>

							{replayResult && (
								<div>
									<div className="text-sm text-secondary" style={{ marginBottom: "var(--spacing-xs)" }}>重放结果</div>
									<pre style={{
										margin: 0,
										padding: "var(--spacing-sm)",
										background: "var(--color-bg-tertiary)",
										borderRadius: "var(--radius-sm)",
										whiteSpace: "pre-wrap",
									}}>
										{JSON.stringify(replayResult, null, 2)}
									</pre>
								</div>
							)}
						</div>
					)}
			</Card>
		</PageContainer>
	);
}
