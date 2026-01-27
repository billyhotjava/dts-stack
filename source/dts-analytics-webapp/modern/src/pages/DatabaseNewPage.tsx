import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import { analyticsApi } from "../api/analyticsApi";
import { PageContainer, PageHeader, Breadcrumb } from "../components/PageContainer/PageContainer";
import { ErrorNotice } from "../components/ErrorNotice";
import { Card, CardHeader, CardBody, CardFooter } from "../ui/Card/Card";
import { Button } from "../ui/Button/Button";
import { Input } from "../ui/Input/Input";
import { NativeSelect } from "../ui/Input/Select";
import { Badge } from "../ui/Badge/Badge";
import { getEffectiveLocale, t, type Locale } from "../i18n";
import "./page.css";

type Engine = "postgres" | "mysql" | "oracle" | "dm";

// Icons
const CheckIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<polyline points="20 6 9 17 4 12" />
	</svg>
);

const DatabaseIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<ellipse cx="12" cy="5" rx="9" ry="3" />
		<path d="M3 5v14a9 3 0 0 0 18 0V5" />
		<path d="M3 12a9 3 0 0 0 18 0" />
	</svg>
);

const PlusIcon = () => (
	<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
		<path d="M5 12h14" />
		<path d="M12 5v14" />
	</svg>
);

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

	const engineOptions = [
		{ value: "postgres", label: "PostgreSQL" },
		{ value: "mysql", label: "MySQL" },
		{ value: "oracle", label: "Oracle" },
		{ value: "dm", label: "达梦（DM）" },
	];

	return (
		<PageContainer>
			<PageHeader
				title={t(locale, "data.add")}
				subtitle={t(locale, "data.subtitle")}
				breadcrumbs={
					<Breadcrumb items={[
						{ label: t(locale, "data.title"), href: "/data" },
						{ label: t(locale, "data.add") }
					]} />
				}
			/>

			<Card>
				<CardHeader title="Database Connection" icon={<DatabaseIcon />} />
				<CardBody>
					{error ? <ErrorNotice locale={locale} error={error} /> : null}
					{okMessage && (
						<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-sm)", marginBottom: "var(--spacing-md)", padding: "var(--spacing-sm)", background: "var(--color-success-bg)", borderRadius: "var(--radius-sm)", color: "var(--color-success)" }}>
							<CheckIcon />
							{okMessage}
						</div>
					)}

					<div className="form-grid">
						<Input
							label="Name"
							value={name}
							onChange={(e) => setName(e.target.value)}
							placeholder="Analytics DB"
						/>

						<NativeSelect
							label="Engine"
							value={engine}
							onChange={(e) => setEngine(e.target.value as Engine)}
							options={engineOptions}
						/>

						<div style={{ gridColumn: "1 / -1" }}>
							<Input
								label="JDBC URL (optional)"
								value={jdbcUrl}
								onChange={(e) => setJdbcUrl(e.target.value)}
								placeholder="jdbc:postgresql://host:5432/db"
								helperText="If provided, overrides host/port/database settings"
							/>
						</div>

						<Input
							label="Host"
							value={host}
							onChange={(e) => setHost(e.target.value)}
							placeholder="127.0.0.1"
						/>

						<Input
							label="Port"
							value={port}
							onChange={(e) => setPort(e.target.value)}
							placeholder="5432"
						/>

						<Input
							label={engine === "oracle" ? "SID (optional)" : "Database"}
							value={dbName}
							onChange={(e) => setDbName(e.target.value)}
							placeholder="db"
						/>

						{engine === "oracle" && (
							<Input
								label="Service Name (optional)"
								value={serviceName}
								onChange={(e) => setServiceName(e.target.value)}
								placeholder="orclpdb1"
							/>
						)}

						<Input
							label="Username"
							value={username}
							onChange={(e) => setUsername(e.target.value)}
							placeholder="user"
						/>

						<Input
							label="Password"
							type="password"
							value={password}
							onChange={(e) => setPassword(e.target.value)}
							placeholder="••••••••"
						/>
					</div>
				</CardBody>
				<CardFooter align="between">
					<Link to="/data">
						<Button variant="tertiary">
							{t(locale, "common.open")} {t(locale, "data.title")}
						</Button>
					</Link>
					<div style={{ display: "flex", gap: "var(--spacing-sm)" }}>
						<Button
							variant="secondary"
							loading={busy === "validate"}
							disabled={busy !== null}
							onClick={validateConnection}
						>
							Validate
						</Button>
						<Button
							variant="primary"
							icon={<PlusIcon />}
							loading={busy === "create" || busy === "sync"}
							disabled={busy !== null}
							onClick={createAndSync}
						>
							{t(locale, "common.create")} + {t(locale, "data.sync")}
						</Button>
					</div>
				</CardFooter>
			</Card>

			<style>{`
				.form-grid {
					display: grid;
					grid-template-columns: repeat(2, 1fr);
					gap: var(--spacing-md);
				}

				@media (max-width: 768px) {
					.form-grid {
						grid-template-columns: 1fr;
					}
				}
			`}</style>
		</PageContainer>
	);
}
