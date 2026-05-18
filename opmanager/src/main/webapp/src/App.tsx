import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  applyConfigAction,
  createPlan,
  getRuntime,
  listContainers,
  listJobEvents,
  listJobs,
  listPackages,
  precheckConfig,
  registerPackagePath,
  uploadPackage
} from "./api";
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
  UploadResult
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
      const [runtimeResult, packagesResult, jobsResult, containersResult] = await Promise.all([getRuntime(), listPackages(), listJobs(), listContainers()]);
      setRuntime(runtimeResult);
      setPackages(packagesResult);
      setJobs(jobsResult);
      setContainers(containersResult);
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
        {activeTab === "overview" ? <Overview runtime={runtime} packages={packages} jobs={jobs} /> : null}
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

function Overview(props: { runtime: RuntimeStatus | null; packages: PackageRegistration[]; jobs: UpgradeJob[] }) {
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
    </section>
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
        <h2>登记升级包</h2>
        <form className="formRow" onSubmit={props.onRegisterPath}>
          <input value={props.serverPath} onChange={event => props.onServerPathChange(event.target.value)} placeholder="/var/lib/dts-opmanager/packages/dts-2.2.4-arm64" />
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
                {item.validation.packageId} / {item.validation.version}
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
          <span>生成升级计划前先处理 .env、compose 和 MDM 配置差异。</span>
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
              <th>包</th>
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
                {item.validation.packageId} / {item.validation.version}
              </option>
            ))}
          </select>
          <input value={props.precheck?.targetStackDir || ""} readOnly placeholder="目标 DTS 目录" />
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
  if (props.file.contentOmitted) {
    return <div className="emptyState">文件较大或不是文本文件，已省略内容预览。可写入升级包副本后在现场工具中进一步查看。</div>;
  }
  const rows = Math.max(props.file.localLines.length, props.file.packageLines.length);
  return (
    <div className="diffGrid">
      <div className="diffTitle">现场文件</div>
      <div className="diffTitle">升级包文件</div>
      {Array.from({ length: rows }).map((_, index) => {
        const local = props.file.localLines[index] ?? "";
        const packaged = props.file.packageLines[index] ?? "";
        const changed = local !== packaged;
        return (
          <div className={changed ? "diffRow changed" : "diffRow"} key={index}>
            <pre>
              <span>{index + 1}</span>
              {local || " "}
            </pre>
            <pre>
              <span>{index + 1}</span>
              {packaged || " "}
            </pre>
          </div>
        );
      })}
    </div>
  );
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
