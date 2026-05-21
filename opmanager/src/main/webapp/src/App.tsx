import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import {
  applyConfigAction,
  createPlan,
  getRuntime,
  getWorkspaceStatus,
  listContainers,
  listJobEvents,
  listJobs,
  listPackages,
  loadWorkspaceImages,
  precheckConfig,
  recreateWorkspaceContainers,
  registerPackagePath,
  uploadPackage
} from "./api";
import { buildSideBySideDiff, collectDiffHunks, type DiffHunk, type SideBySideDiffRow } from "./diff";
import type {
  ConfigApplyAction,
  ConfigApplyResult,
  ConfigFileReview,
  ConfigPrecheckResponse,
  DockerContainersResponse,
  JobEvent,
  PackageRegistration,
  RuntimeStatus,
  UpgradeJob,
  UploadResult,
  WorkspaceOperationResult,
  WorkspaceStatus
} from "./types";

type TabKey = "overview" | "packages" | "config" | "jobs" | "containers";

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: "overview", label: "概览" },
  { key: "packages", label: "升级包" },
  { key: "config", label: "配置预检" },
  { key: "jobs", label: "计划任务" },
  { key: "containers", label: "容器" }
];

export function App() {
  const [activeTab, setActiveTab] = useState<TabKey>("overview");
  const [runtime, setRuntime] = useState<RuntimeStatus | null>(null);
  const [packages, setPackages] = useState<PackageRegistration[]>([]);
  const [jobs, setJobs] = useState<UpgradeJob[]>([]);
  const [events, setEvents] = useState<Record<string, JobEvent[]>>({});
  const [containers, setContainers] = useState<DockerContainersResponse | null>(null);
  const [workspace, setWorkspace] = useState<WorkspaceStatus | null>(null);
  const [workspaceResult, setWorkspaceResult] = useState<WorkspaceOperationResult | null>(null);
  const [serverPath, setServerPath] = useState("");
  const [selectedPackage, setSelectedPackage] = useState("");
  const [planNote, setPlanNote] = useState("dry-run plan created");
  const [configPackage, setConfigPackage] = useState("");
  const [configPrecheck, setConfigPrecheck] = useState<ConfigPrecheckResponse | null>(null);
  const [selectedConfigPath, setSelectedConfigPath] = useState("");
  const [configResult, setConfigResult] = useState<ConfigApplyResult | null>(null);
  const [uploadResult, setUploadResult] = useState<UploadResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const validPackages = useMemo(() => packages.filter(item => item.validation.valid), [packages]);

  useEffect(() => {
    refreshAll();
  }, []);

  useEffect(() => {
    if (!selectedPackage && validPackages.length > 0) {
      setSelectedPackage(validPackages[0].id);
    }
    if (!configPackage && validPackages.length > 0) {
      setConfigPackage(validPackages[0].id);
    }
  }, [configPackage, selectedPackage, validPackages]);

  async function refreshAll() {
    setError("");
    try {
      const [runtimeResult, packagesResult, jobsResult, containersResult, workspaceResult] = await Promise.all([getRuntime(), listPackages(), listJobs(), listContainers(), getWorkspaceStatus()]);
      setRuntime(runtimeResult);
      setPackages(packagesResult);
      setJobs(jobsResult);
      setContainers(containersResult);
      setWorkspace(workspaceResult);
    } catch (caught) {
      setError(toErrorMessage(caught));
    }
  }

  async function onRegisterPath(event: FormEvent) {
    event.preventDefault();
    if (!serverPath.trim()) return;
    setBusy(true);
    setError("");
    try {
      const registration = await registerPackagePath(serverPath.trim());
      setPackages(await listPackages());
      setSelectedPackage(registration.id);
      setConfigPackage(registration.id);
      setServerPath("");
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function onUpload(file: File | null) {
    if (!file) return;
    setBusy(true);
    setError("");
    try {
      setUploadResult(await uploadPackage(file));
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function onCreatePlan(event: FormEvent) {
    event.preventDefault();
    if (!selectedPackage) return;
    setBusy(true);
    setError("");
    try {
      const job = await createPlan(selectedPackage, planNote);
      const nextJobs = await listJobs();
      setJobs(nextJobs);
      setEvents({ ...events, [job.id]: await listJobEvents(job.id) });
      setActiveTab("jobs");
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function onConfigPrecheck(event?: FormEvent) {
    event?.preventDefault();
    if (!configPackage) return;
    setBusy(true);
    setError("");
    setConfigResult(null);
    try {
      const response = await precheckConfig(configPackage);
      setConfigPrecheck(response);
      const firstChanged = response.files.find(file => file.status === "MODIFIED" || file.status === "PACKAGE_ONLY");
      setSelectedConfigPath((firstChanged || response.files[0])?.path || "");
      setActiveTab("config");
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function onApplyConfigAction(file: ConfigFileReview, action: ConfigApplyAction) {
    if (!configPrecheck) return;
    setBusy(true);
    setError("");
    try {
      const result = await applyConfigAction(configPrecheck.packageRegistrationId, file.path, action);
      setConfigResult(result);
      const response = await precheckConfig(configPrecheck.packageRegistrationId);
      setConfigPrecheck(response);
      setSelectedConfigPath(file.path);
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function toggleEvents(jobId: string) {
    if (events[jobId]) {
      const next = { ...events };
      delete next[jobId];
      setEvents(next);
      return;
    }
    setError("");
    try {
      setEvents({ ...events, [jobId]: await listJobEvents(jobId) });
    } catch (caught) {
      setError(toErrorMessage(caught));
    }
  }

  async function onLoadWorkspaceImages() {
    setBusy(true);
    setError("");
    try {
      setWorkspaceResult(await loadWorkspaceImages());
      setWorkspace(await getWorkspaceStatus());
      setContainers(await listContainers());
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  async function onRecreateWorkspaceContainers() {
    setBusy(true);
    setError("");
    try {
      setWorkspaceResult(await recreateWorkspaceContainers());
      setContainers(await listContainers());
    } catch (caught) {
      setError(toErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="shell">
      <header className="topbar">
        <div>
          <h1>DTS OpManager</h1>
          <p>离线升级控制台</p>
        </div>
        <button className="secondary" type="button" onClick={refreshAll} disabled={busy}>
          刷新
        </button>
      </header>

      <nav className="tabs" aria-label="主导航">
        {tabs.map(tab => (
          <button key={tab.key} className={activeTab === tab.key ? "active" : ""} type="button" onClick={() => setActiveTab(tab.key)}>
            {tab.label}
          </button>
        ))}
      </nav>

      {error ? <div className="alert">{error}</div> : null}

      <main>
        {activeTab === "overview" ? (
          <Overview runtime={runtime} packages={packages} jobs={jobs} workspace={workspace} workspaceResult={workspaceResult} busy={busy} onLoadImages={onLoadWorkspaceImages} onRecreateContainers={onRecreateWorkspaceContainers} />
        ) : null}
        {activeTab === "packages" ? (
          <PackagesView
            packages={packages}
            validPackages={validPackages}
            serverPath={serverPath}
            selectedPackage={selectedPackage}
            planNote={planNote}
            busy={busy}
            uploadResult={uploadResult}
            onServerPathChange={setServerPath}
            onSelectedPackageChange={value => {
              setSelectedPackage(value);
              setConfigPackage(value);
            }}
            onPlanNoteChange={setPlanNote}
            onRegisterPath={onRegisterPath}
            onUpload={onUpload}
            onCreatePlan={onCreatePlan}
            onConfigPrecheck={onConfigPrecheck}
          />
        ) : null}
        {activeTab === "config" ? (
          <ConfigPrecheckView
            packages={validPackages}
            selectedPackage={configPackage}
            busy={busy}
            precheck={configPrecheck}
            selectedPath={selectedConfigPath}
            result={configResult}
            onSelectedPackageChange={setConfigPackage}
            onPrecheck={onConfigPrecheck}
            onSelectPath={setSelectedConfigPath}
            onApplyAction={onApplyConfigAction}
          />
        ) : null}
        {activeTab === "jobs" ? <JobsView jobs={jobs} events={events} onToggleEvents={toggleEvents} /> : null}
        {activeTab === "containers" ? <ContainersView runtime={runtime} containers={containers} /> : null}
      </main>
    </div>
  );
}

function Overview(props: {
  runtime: RuntimeStatus | null;
  packages: PackageRegistration[];
  jobs: UpgradeJob[];
  workspace: WorkspaceStatus | null;
  workspaceResult: WorkspaceOperationResult | null;
  busy: boolean;
  onLoadImages: () => void;
  onRecreateContainers: () => void;
}) {
  const runtime = props.runtime;
  return (
    <section className="grid two">
      <div className="panel">
        <h2>运行环境</h2>
        <dl className="facts">
          <Fact label="OS" value={runtime ? `${runtime.osName} / ${runtime.osArch}` : "-"} />
          <Fact label="Java" value={runtime?.javaVersion || "-"} />
          <Fact label="数据目录" value={runtime?.dataDir || "-"} />
          <Fact label="DTS 目录" value={runtime?.targetStackDir || "-"} />
          <Fact label="Docker" value={runtime?.dockerAvailable ? runtime.dockerVersion : "不可用"} state={runtime?.dockerAvailable ? "ok" : "bad"} />
          <Fact label="Compose" value={runtime?.composeAvailable ? runtime.composeVersion : "不可用"} state={runtime?.composeAvailable ? "ok" : "bad"} />
        </dl>
        {runtime?.portainerUrl ? (
          <a className="linkButton" href={runtime.portainerUrl} target="_blank" rel="noreferrer">
            打开 Portainer
          </a>
        ) : null}
      </div>
      <div className="panel">
        <h2>当前状态</h2>
        <div className="stats">
          <Stat label="升级包" value={String(props.packages.length)} />
          <Stat label="有效包" value={String(props.packages.filter(item => item.validation.valid).length)} />
          <Stat label="计划任务" value={String(props.jobs.length)} />
        </div>
      </div>
      <div className="panel wide">
        <h2>升级工作区</h2>
        <dl className="facts">
          <Fact label="根目录" value={props.workspace?.packageRoot || "-"} state={props.workspace?.packageRootExists ? "ok" : "bad"} />
          <Fact label="images" value={props.workspace?.imagesDir || "-"} state={props.workspace?.imagesDirExists ? "ok" : "bad"} />
          <Fact label="dts-stack" value={props.workspace?.stackDir || "-"} state={props.workspace?.stackDirExists ? "ok" : "bad"} />
          <Fact label="misc" value={props.workspace?.miscDir || "-"} state={props.workspace?.miscDirExists ? "ok" : "bad"} />
        </dl>
        <div className="runtimeLine">
          <span className="badge neutral">镜像 tar {props.workspace?.images.length || 0}</span>
          <button type="button" disabled={props.busy || !props.workspace?.images.length} onClick={props.onLoadImages}>
            加载镜像
          </button>
          <button className="secondary" type="button" disabled={props.busy || !runtime?.composeAvailable} onClick={props.onRecreateContainers}>
            重建容器
          </button>
        </div>
        {props.workspaceResult ? <OperationResult result={props.workspaceResult} /> : null}
      </div>
    </section>
  );
}

function OperationResult(props: { result: WorkspaceOperationResult }) {
  return (
    <div className={props.result.success ? "notice" : "alert inline"}>
      <strong>{props.result.message}</strong>
      {props.result.commands.map(command => (
        <div key={command.command.join(" ")} className="commandLine">
          <span className={command.success ? "ok" : "bad"}>{command.success ? "OK" : "FAIL"}</span>
          <code>{command.command.join(" ")}</code>
          {command.message ? <em>{command.message}</em> : null}
        </div>
      ))}
    </div>
  );
}

function PackagesView(props: {
  packages: PackageRegistration[];
  validPackages: PackageRegistration[];
  serverPath: string;
  selectedPackage: string;
  planNote: string;
  busy: boolean;
  uploadResult: UploadResult | null;
  onServerPathChange: (value: string) => void;
  onSelectedPackageChange: (value: string) => void;
  onPlanNoteChange: (value: string) => void;
  onRegisterPath: (event: FormEvent) => void;
  onUpload: (file: File | null) => void;
  onCreatePlan: (event: FormEvent) => void;
  onConfigPrecheck: (event?: FormEvent) => void;
}) {
  return (
    <section className="stack">
      <div className="panel">
        <h2>登记升级工作区</h2>
        <form className="formRow" onSubmit={props.onRegisterPath}>
          <input value={props.serverPath} onChange={event => props.onServerPathChange(event.target.value)} placeholder="/var/lib/dts-opmanager/packages" />
          <button type="submit" disabled={props.busy || !props.serverPath.trim()}>
            登记
          </button>
        </form>
        <div className="uploadLine">
          <input type="file" onChange={event => props.onUpload(event.target.files ? event.target.files[0] : null)} disabled={props.busy} />
          {props.uploadResult ? <span>{props.uploadResult.fileName} 已保存</span> : null}
        </div>
      </div>

      <div className="panel">
        <h2>生成计划</h2>
        <form className="formRow" onSubmit={props.onCreatePlan}>
          <select value={props.selectedPackage} onChange={event => props.onSelectedPackageChange(event.target.value)}>
            {props.validPackages.map(item => (
              <option key={item.id} value={item.id}>
                {item.validation.packageId || item.id} / {item.validation.version || "未声明版本"}
              </option>
            ))}
          </select>
          <input value={props.planNote} onChange={event => props.onPlanNoteChange(event.target.value)} />
          <button type="submit" disabled={props.busy || !props.selectedPackage}>
            计划
          </button>
        </form>
        <div className="uploadLine">
          <button className="secondary" type="button" disabled={props.busy || !props.selectedPackage} onClick={() => props.onConfigPrecheck()}>
            配置预检
          </button>
          <span>工作区固定使用 images、dts-stack、misc；配置预检对比 dts-stack 与现场目录。</span>
        </div>
      </div>

      <PackageTable packages={props.packages} />
    </section>
  );
}

function PackageTable(props: { packages: PackageRegistration[] }) {
  return (
    <div className="panel">
      <h2>升级包列表</h2>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>工作区</th>
              <th>版本</th>
              <th>架构</th>
              <th>状态</th>
              <th>路径</th>
            </tr>
          </thead>
          <tbody>
            {props.packages.map(item => (
              <tr key={item.id}>
                <td>{item.validation.packageId || item.id}</td>
                <td>{item.validation.version || "-"}</td>
                <td>{item.validation.targetArch || "-"}</td>
                <td>
                  <span className={item.validation.valid ? "badge ok" : "badge bad"}>{item.validation.valid ? "有效" : "异常"}</span>
                  {item.validation.messages.length ? <div className="messages">{item.validation.messages.join("；")}</div> : null}
                </td>
                <td className="mono">{item.sourcePath}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ConfigPrecheckView(props: {
  packages: PackageRegistration[];
  selectedPackage: string;
  busy: boolean;
  precheck: ConfigPrecheckResponse | null;
  selectedPath: string;
  result: ConfigApplyResult | null;
  onSelectedPackageChange: (value: string) => void;
  onPrecheck: (event?: FormEvent) => void;
  onSelectPath: (path: string) => void;
  onApplyAction: (file: ConfigFileReview, action: ConfigApplyAction) => void;
}) {
  const selectedFile = props.precheck?.files.find(file => file.path === props.selectedPath) || props.precheck?.files[0] || null;
  return (
    <section className="stack">
      <div className="panel">
        <h2>配置保护中心</h2>
        <form className="formRow" onSubmit={props.onPrecheck}>
          <select value={props.selectedPackage} onChange={event => props.onSelectedPackageChange(event.target.value)}>
            {props.packages.map(item => (
              <option key={item.id} value={item.id}>
                {item.validation.packageId || item.id} / {item.validation.version || "未声明版本"}
              </option>
            ))}
          </select>
          <input value={props.precheck?.targetStackDir || ""} readOnly placeholder="目标 DTS 目录" />
          <input value={props.precheck?.packageStackDir || ""} readOnly placeholder="工作区 dts-stack 目录" />
          <button type="submit" disabled={props.busy || !props.selectedPackage}>
            预检
          </button>
        </form>
        {props.result ? (
          <div className="notice">
            {actionLabel(props.result.action)}：{props.result.message}
            {props.result.backupPath ? <span>备份 {props.result.backupPath}</span> : null}
            {props.result.writtenPath ? <span>写入 {props.result.writtenPath}</span> : null}
          </div>
        ) : null}
      </div>

      {props.precheck ? (
        <>
          <div className="grid three">
            <Stat label="受保护文件" value={String(props.precheck.total)} />
            <Stat label="需处理差异" value={String(props.precheck.changed)} />
            <Stat label="高风险" value={String(props.precheck.highRisk)} />
          </div>
          <div className="configLayout">
            <div className="panel configList">
              <h2>文件清单</h2>
              {props.precheck.files.map(file => (
                <button key={file.path} className={props.selectedPath === file.path ? "fileItem active" : "fileItem"} type="button" onClick={() => props.onSelectPath(file.path)}>
                  <span className="mono">{file.path}</span>
                  <span>
                    <span className={`badge ${riskClass(file.risk)}`}>{riskLabel(file.risk)}</span>
                    <span className="badge neutral">{statusLabel(file.status)}</span>
                  </span>
                </button>
              ))}
            </div>
            <div className="panel configDetail">
              {selectedFile ? (
                <>
                  <div className="detailHeader">
                    <div>
                      <h2>{selectedFile.path}</h2>
                      <p>{selectedFile.message}</p>
                    </div>
                    <div className="actionBar">
                      {selectedFile.allowedActions.map(action => (
                        <button key={action} className={action === "KEEP_LOCAL" ? "secondary" : action === "USE_PACKAGE" ? "danger" : ""} type="button" disabled={props.busy} onClick={() => props.onApplyAction(selectedFile, action)}>
                          {actionLabel(action)}
                        </button>
                      ))}
                    </div>
                  </div>
                  <DiffViewer file={selectedFile} />
                </>
              ) : (
                <div className="emptyState">请选择升级包并执行配置预检。</div>
              )}
            </div>
          </div>
        </>
      ) : (
        <div className="panel emptyState">选择一个有效升级包后执行预检，系统会比较现场配置和升级包中的受保护文件。</div>
      )}
    </section>
  );
}

function DiffViewer(props: { file: ConfigFileReview }) {
  const [showOnlyChanges, setShowOnlyChanges] = useState(false);
  const [activeHunkIndex, setActiveHunkIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const diff = useMemo(() => buildSideBySideDiff(props.file.localLines, props.file.packageLines), [props.file.localLines, props.file.packageLines]);
  const hunks = useMemo(() => collectDiffHunks(diff.rows), [diff.rows]);
  const visibleRows = useMemo(() => (showOnlyChanges ? diff.rows.filter(row => row.kind !== "equal") : diff.rows), [diff.rows, showOnlyChanges]);
  const activeHunk = hunks[activeHunkIndex] || null;

  useEffect(() => {
    setShowOnlyChanges(false);
    setActiveHunkIndex(0);
  }, [props.file.path]);

  useEffect(() => {
    if (!activeHunk || !scrollRef.current) {
      return;
    }
    const row = scrollRef.current.querySelector<HTMLElement>(`[data-row-index="${activeHunk.start}"]`);
    row?.scrollIntoView({ block: "center" });
  }, [activeHunk, showOnlyChanges]);

  if (props.file.contentOmitted) {
    return <div className="emptyState">文件较大或不是文本文件，已省略内容预览。可写入升级包副本后在现场工具中进一步查看。</div>;
  }

  function moveHunk(direction: -1 | 1) {
    if (hunks.length === 0) {
      return;
    }
    setActiveHunkIndex((activeHunkIndex + direction + hunks.length) % hunks.length);
  }

  return (
    <div className="compareShell">
      <div className="compareToolbar">
        <div className="compareStats">
          <span className="badge neutral">总行 {diff.rows.length}</span>
          <span className={diff.changedRows ? "badge warn" : "badge ok"}>差异 {diff.changedRows}</span>
          <span className="badge neutral">差异块 {hunks.length}</span>
          {diff.strategy === "row-by-row" ? <span className="badge warn">大文件降级对比</span> : null}
        </div>
        <div className="compareActions">
          <button className="iconButton" type="button" disabled={!hunks.length} onClick={() => moveHunk(-1)} title="上一个差异">
            ↑
          </button>
          <span>{activeHunk ? `${activeHunk.label} / ${hunks.length}` : "无差异"}</span>
          <button className="iconButton" type="button" disabled={!hunks.length} onClick={() => moveHunk(1)} title="下一个差异">
            ↓
          </button>
          <button className="secondary" type="button" disabled={!hunks.length} onClick={() => setShowOnlyChanges(!showOnlyChanges)}>
            {showOnlyChanges ? "显示全部" : "只看差异"}
          </button>
        </div>
      </div>
      <div className="diffGrid" ref={scrollRef}>
        <div className="diffTitle">现场文件</div>
        <div className="diffTitle">升级包文件</div>
        {visibleRows.map(row => (
          <DiffRow key={row.key} row={row} activeHunk={activeHunk} />
        ))}
      </div>
    </div>
  );
}

function DiffRow(props: { row: SideBySideDiffRow; activeHunk: DiffHunk | null }) {
  const active = Boolean(props.activeHunk && props.row.index >= props.activeHunk.start && props.row.index <= props.activeHunk.end);
  return (
    <div className={`diffRow ${props.row.kind} ${active ? "active" : ""}`} data-row-index={props.row.index}>
      <pre className={props.row.leftState} data-row-index={props.row.index}>
        <span>{formatLineNumber(props.row.leftLineNumber)}</span>
        {props.row.leftText || " "}
      </pre>
      <pre className={props.row.rightState}>
        <span>{formatLineNumber(props.row.rightLineNumber)}</span>
        {props.row.rightText || " "}
      </pre>
    </div>
  );
}

function formatLineNumber(value: number | null) {
  return value === null ? "" : String(value);
}

function JobsView(props: { jobs: UpgradeJob[]; events: Record<string, JobEvent[]>; onToggleEvents: (jobId: string) => void }) {
  return (
    <section className="panel">
      <h2>计划任务</h2>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>任务</th>
              <th>包</th>
              <th>版本</th>
              <th>状态</th>
              <th>时间</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {props.jobs.map(job => (
              <tr key={job.id}>
                <td className="mono">{job.id}</td>
                <td>{job.packageId}</td>
                <td>{job.version}</td>
                <td>
                  <span className="badge neutral">{job.state}</span>
                </td>
                <td>{formatTime(job.createdAt)}</td>
                <td>
                  <button className="secondary compact" type="button" onClick={() => props.onToggleEvents(job.id)}>
                    事件
                  </button>
                  {props.events[job.id] ? (
                    <div className="eventBox">
                      {props.events[job.id].map(event => (
                        <div key={`${event.timestamp}-${event.message}`}>
                          <span>{formatTime(event.timestamp)}</span> {event.message}
                        </div>
                      ))}
                    </div>
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function ContainersView(props: { runtime: RuntimeStatus | null; containers: DockerContainersResponse | null }) {
  const response = props.containers;
  return (
    <section className="panel">
      <h2>容器</h2>
      <div className="runtimeLine">
        <span className={response?.available ? "badge ok" : "badge bad"}>{response?.available ? "Docker 可用" : "Docker 不可用"}</span>
        {response?.message ? <span>{response.message}</span> : null}
        {props.runtime?.portainerUrl ? (
          <a href={props.runtime.portainerUrl} target="_blank" rel="noreferrer">
            Portainer
          </a>
        ) : null}
      </div>
      <div className="tableWrap">
        <table>
          <thead>
            <tr>
              <th>名称</th>
              <th>镜像</th>
              <th>状态</th>
              <th>ID</th>
            </tr>
          </thead>
          <tbody>
            {(response?.containers || []).map(container => (
              <tr key={container.id}>
                <td>{container.name}</td>
                <td className="mono">{container.image}</td>
                <td>{container.status || container.state}</td>
                <td className="mono">{container.id}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function Fact(props: { label: string; value: string; state?: "ok" | "bad" }) {
  return (
    <>
      <dt>{props.label}</dt>
      <dd className={props.state ? props.state : ""}>{props.value}</dd>
    </>
  );
}

function Stat(props: { label: string; value: string }) {
  return (
    <div>
      <strong>{props.value}</strong>
      <span>{props.label}</span>
    </div>
  );
}

function actionLabel(action: ConfigApplyAction) {
  const labels: Record<ConfigApplyAction, string> = {
    KEEP_LOCAL: "保留现场",
    MERGE_ENV_ADD_KEYS: "追加缺失键",
    WRITE_PACKAGE_COPY: "写入副本",
    USE_PACKAGE: "采用升级包"
  };
  return labels[action];
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    UNCHANGED: "一致",
    MODIFIED: "有差异",
    LOCAL_ONLY: "仅现场",
    PACKAGE_ONLY: "仅升级包",
    MISSING: "缺失"
  };
  return labels[status] || status;
}

function riskLabel(risk: string) {
  const labels: Record<string, string> = {
    LOW: "低",
    MEDIUM: "中",
    HIGH: "高"
  };
  return labels[risk] || risk;
}

function riskClass(risk: string) {
  if (risk === "HIGH") return "bad";
  if (risk === "MEDIUM") return "warn";
  return "ok";
}

function formatTime(value: string) {
  if (!value) return "-";
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function toErrorMessage(caught: unknown) {
  return caught instanceof Error ? caught.message : "请求失败";
}
