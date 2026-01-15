import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { analyticsApi } from "../api/analyticsApi";
import { ErrorNotice } from "../components/ErrorNotice";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type Engine = "postgres" | "mysql" | "oracle" | "dm";

export default function DatabaseNewPage() {
	const locale: Locale = useMemo(() => getEffectiveLocale(), []);
	const navigate = useNavigate();

	const [engine, setEngine] = useState<Engine>("postgres");
	const [name, setName] = useState("");
	const [host, setHost] = useState("");
	const [port, setPort] = useState("");
	const [dbName, setDbName] = useState("");
	const [serviceName, setServiceName] = useState("");
	const [jdbcUrl, setJdbcUrl] = useState("");
	const [username, setUsername] = useState("");
	const [password, setPassword] = useState("");

	const [busy, setBusy] = useState<null | "validate" | "create" | "sync">(null);
	const [okMessage, setOkMessage] = useState<string>("");
	const [error, setError] = useState<unknown>(null);

	function detailsObject(): Record<string, unknown> {
		const details: Record<string, unknown> = {};

		const u = jdbcUrl.trim();
		if (u) {
			details["jdbc-url"] = u;
		} else {
			if (host.trim()) details.host = host.trim();
			if (port.trim()) details.port = Number.parseInt(port.trim(), 10);

			if (engine === "oracle") {
				if (serviceName.trim()) details["service-name"] = serviceName.trim();
				if (dbName.trim()) details.sid = dbName.trim();
			} else {
				if (dbName.trim()) details.dbname = dbName.trim();
			}
		}

		if (username.trim()) details.user = username.trim();
		if (password) details.password = password;
		return details;
	}

	async function validateConnection() {
		setBusy("validate");
		setOkMessage("");
		setError(null);
		try {
			const details = detailsObject();
			if (engine === "dm" && !String(details["jdbc-url"] ?? "").trim()) {
				throw new Error("达梦（DM）暂要求显式填写 JDBC URL（details.jdbc-url）。");
			}
			await analyticsApi.validateDatabase({ engine, details });
			setOkMessage(t(locale, "data.validated"));
		} catch (e) {
			setError(e);
		} finally {
			setBusy(null);
		}
	}

	async function createAndSync() {
		setBusy("create");
		setOkMessage("");
		setError(null);
		try {
			const details = detailsObject();
			if (engine === "dm" && !String(details["jdbc-url"] ?? "").trim()) {
				throw new Error("达梦（DM）暂要求显式填写 JDBC URL（details.jdbc-url）。");
			}
			const response = await analyticsApi.createDatabase({
				name: name.trim() || `${engine}-db`,
				engine,
				details,
			});
			const createdId = (response as any)?.id;
			if (!createdId) {
				setOkMessage("Created.");
				return;
			}

			setBusy("sync");
			await analyticsApi.syncDatabaseSchema(createdId);
			setOkMessage(t(locale, "data.synced"));
			navigate(`/data/${encodeURIComponent(String(createdId))}`, { replace: true });
		} catch (e) {
			setError(e);
		} finally {
			setBusy(null);
		}
	}

	return (
		<div className="page">
			<h1 className="pageTitle">{t(locale, "data.add")}</h1>
			<div className="pageSub">{t(locale, "data.subtitle")}</div>

			<div style={{ height: 16 }} />

			<div className="card">
				{error ? <ErrorNotice locale={locale} error={error} /> : null}
				{okMessage ? <div className="muted">{okMessage}</div> : null}

				<div style={{ display: "grid", gridTemplateColumns: "160px 1fr", gap: 12, alignItems: "center" }}>
					<label className="muted">Name</label>
					<input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Analytics DB" />

					<label className="muted">Engine</label>
					<select className="input" value={engine} onChange={(e) => setEngine(e.target.value as Engine)}>
						<option value="postgres">PostgreSQL</option>
						<option value="mysql">MySQL</option>
						<option value="oracle">Oracle</option>
						<option value="dm">达梦（DM）</option>
					</select>

					<label className="muted">JDBC URL (optional)</label>
					<input
						className="input"
						value={jdbcUrl}
						onChange={(e) => setJdbcUrl(e.target.value)}
						placeholder="jdbc:postgresql://host:5432/db"
					/>

					<label className="muted">Host</label>
					<input className="input" value={host} onChange={(e) => setHost(e.target.value)} placeholder="127.0.0.1" />

					<label className="muted">Port</label>
					<input className="input" value={port} onChange={(e) => setPort(e.target.value)} placeholder="5432" />

					<label className="muted">{engine === "oracle" ? "SID (optional)" : "Database"}</label>
					<input className="input" value={dbName} onChange={(e) => setDbName(e.target.value)} placeholder="db" />

					{engine === "oracle" ? (
						<>
							<label className="muted">Service name (optional)</label>
							<input
								className="input"
								value={serviceName}
								onChange={(e) => setServiceName(e.target.value)}
								placeholder="orclpdb1"
							/>
						</>
					) : null}

					<label className="muted">Username</label>
					<input className="input" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="user" />

					<label className="muted">Password</label>
					<input
						className="input"
						value={password}
						type="password"
						onChange={(e) => setPassword(e.target.value)}
						placeholder="••••••••"
					/>
				</div>

				<div style={{ height: 16 }} />

				<div className="row" style={{ gap: 8, flexWrap: "wrap" }}>
					<button className="btn" type="button" disabled={busy !== null} onClick={validateConnection}>
						{busy === "validate" ? t(locale, "loading") : "Validate"}
					</button>
					<button className="btn" type="button" disabled={busy !== null} onClick={createAndSync}>
						{busy === "create" || busy === "sync" ? t(locale, "loading") : `${t(locale, "common.create")} + ${t(locale, "data.sync")}`}
					</button>
					<Link className="btn" to="/data">
						{t(locale, "common.open")} {t(locale, "data.title")}
					</Link>
				</div>
			</div>
		</div>
	);
}
