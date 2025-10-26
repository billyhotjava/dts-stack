import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import type { InfraDataSource, UpsertInfraDataSourcePayload } from "@/api/services/infraService";
import { Icon } from "@/components/icon";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";

type Mode = "create" | "edit";

const TYPE_OPTIONS = ["TRINO", "POSTGRES", "MYSQL", "CLICKHOUSE", "ORACLE", "SQLSERVER", "API", "EXTERNAL"];
const CUSTOM_OPTION = "__CUSTOM__";

type KeyValuePair = { key: string; value: string };

export interface GenericDataSourceDialogProps {
	open: boolean;
	mode: Mode;
	submitting: boolean;
	onClose: () => void;
	onSubmit: (payload: UpsertInfraDataSourcePayload) => Promise<void> | void;
	source?: InfraDataSource | null;
}

const emptyRow: KeyValuePair = { key: "", value: "" };

export function GenericDataSourceDialog({
	open,
	mode,
	submitting,
	onClose,
	onSubmit,
	source,
}: GenericDataSourceDialogProps) {
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [jdbcUrl, setJdbcUrl] = useState("");
	const [username, setUsername] = useState("");
	const [password, setPassword] = useState("");
	const [propsRows, setPropsRows] = useState<KeyValuePair[]>([]);
	const [secretRows, setSecretRows] = useState<KeyValuePair[]>([]);
	const [typeChoice, setTypeChoice] = useState<string>("TRINO");
	const [customType, setCustomType] = useState("");

	const dialogTitle = mode === "create" ? "新增数据源" : "编辑数据源";

	useEffect(() => {
		if (!open) return;
		const type = (source?.type || "TRINO").toString().toUpperCase();
		const preset = TYPE_OPTIONS.includes(type) ? type : CUSTOM_OPTION;
		setTypeChoice(preset);
		setCustomType(preset === CUSTOM_OPTION ? source?.type || "" : "");
		setName(source?.name || "");
		setDescription(source?.description || "");
		setJdbcUrl(source?.jdbcUrl || "");
		setUsername(source?.username || "");
		setPassword("");
		setPropsRows(() => toRows(source?.props));
		setSecretRows([]);
	}, [open, source]);

	const resolvedType = useMemo(() => {
		if (typeChoice === CUSTOM_OPTION) {
			return customType.trim().toUpperCase();
		}
		return typeChoice;
	}, [typeChoice, customType]);

	const disableSubmit = submitting;

	const handleSubmit = async () => {
		if (!name.trim()) {
			toast.error("请填写数据源名称");
			return;
		}
		if (!resolvedType) {
			toast.error("请选择或填写数据源类型");
			return;
		}
		if (!jdbcUrl.trim()) {
			toast.error("请填写 JDBC 连接串");
			return;
		}
		const props = buildMap(propsRows);
		const secrets = buildMap(secretRows);
		if (password.trim()) {
			secrets.password = password.trim();
		}
		const payload: UpsertInfraDataSourcePayload = {
			name: name.trim(),
			type: resolvedType,
			jdbcUrl: jdbcUrl.trim(),
			username: username.trim() || undefined,
			description: description.trim() || undefined,
			props: Object.keys(props).length ? props : undefined,
			secrets: Object.keys(secrets).length ? secrets : undefined,
		};
		await onSubmit(payload);
	};

	const addPropsRow = () => setPropsRows((rows) => [...rows, { ...emptyRow }]);
	const removePropsRow = (index: number) => setPropsRows((rows) => rows.filter((_, i) => i !== index));

	const updatePropsRow = (index: number, patch: Partial<KeyValuePair>) =>
		setPropsRows((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));

	const addSecretRow = () => setSecretRows((rows) => [...rows, { ...emptyRow }]);
	const removeSecretRow = (index: number) => setSecretRows((rows) => rows.filter((_, i) => i !== index));

	const updateSecretRow = (index: number, patch: Partial<KeyValuePair>) =>
		setSecretRows((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));

	return (
		<Dialog
			open={open}
			onOpenChange={(next) => {
				if (submitting) return;
				if (!next) {
					onClose();
				}
			}}
		>
			<DialogContent className="max-w-2xl">
				<DialogHeader>
					<DialogTitle>{dialogTitle}</DialogTitle>
					<p className="text-sm text-muted-foreground">
						用于多数据源能力的普通 JDBC/REST 数据源。保存后即可在数据资产中选择对应来源。
					</p>
				</DialogHeader>

				<div className="space-y-4 py-2">
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>数据源名称</Label>
							<Input value={name} onChange={(event) => setName(event.target.value)} placeholder="如：Trino 集群 A" />
						</div>
						<div className="space-y-2">
							<Label>类型</Label>
							<Select value={typeChoice} onValueChange={setTypeChoice}>
								<SelectTrigger>
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{TYPE_OPTIONS.map((option) => (
										<SelectItem key={option} value={option}>
											{renderTypeLabel(option)}
										</SelectItem>
									))}
									<SelectItem value={CUSTOM_OPTION}>自定义</SelectItem>
								</SelectContent>
							</Select>
						</div>
						{typeChoice === CUSTOM_OPTION && (
							<div className="space-y-2 md:col-span-2">
								<Label>自定义类型</Label>
								<Input
									value={customType}
									onChange={(event) => setCustomType(event.target.value)}
									placeholder="请输入类型标识，如 ELASTICSEARCH"
								/>
							</div>
						)}
						<div className="md:col-span-2 space-y-2">
							<Label>JDBC / 连接地址</Label>
							<Input
								value={jdbcUrl}
								onChange={(event) => setJdbcUrl(event.target.value)}
								placeholder="jdbc:trino://host:port/catalog/schema"
							/>
						</div>
						<div className="space-y-2">
							<Label>登录账号（可选）</Label>
							<Input
								value={username}
								onChange={(event) => setUsername(event.target.value)}
								placeholder="如：trino_reader"
							/>
						</div>
						<div className="space-y-2">
							<Label>登录密码（提交时会加密保存）</Label>
							<Input
								type="password"
								value={password}
								onChange={(event) => setPassword(event.target.value)}
								placeholder={mode === "edit" && source?.hasSecrets ? "修改密码将覆盖原值" : "可选"}
							/>
							{mode === "edit" && source?.hasSecrets ? (
								<p className="text-xs text-muted-foreground">当前数据源已有密钥，留空将清除原有密码与相关密钥。</p>
							) : null}
						</div>
						<div className="md:col-span-2 space-y-2">
							<Label>说明（可选）</Label>
							<Textarea
								value={description}
								onChange={(event) => setDescription(event.target.value)}
								placeholder="用途、网络区域、负责人等补充信息"
								rows={3}
							/>
						</div>
					</div>

					<section className="space-y-2">
						<div className="flex items-center justify-between">
							<Label className="text-sm">连接参数（可选）</Label>
							<Button type="button" variant="ghost" size="sm" onClick={addPropsRow}>
								<Icon icon="solar:add-circle-bold" className="mr-1 h-4 w-4" />
								添加
							</Button>
						</div>
						<p className="text-xs text-muted-foreground">
							以键值对形式补充驱动参数，如 `ssl=true`、`catalog=hive`。值默认为字符串，如需 JSON 可手动填写 JSON
							字符串。
						</p>
						{propsRows.length === 0 ? (
							<div className="rounded border border-dashed border-muted-foreground/40 px-3 py-4 text-center text-xs text-muted-foreground">
								暂无连接参数
							</div>
						) : (
							<div className="space-y-2">
								{propsRows.map((row, index) => (
									<div key={index} className="grid gap-2 md:grid-cols-[1fr,1fr,auto]">
										<Input
											value={row.key}
											placeholder="参数名"
											onChange={(event) => updatePropsRow(index, { key: event.target.value })}
										/>
										<Input
											value={row.value}
											placeholder="参数值"
											onChange={(event) => updatePropsRow(index, { value: event.target.value })}
										/>
										<Button
											type="button"
											variant="ghost"
											size="icon"
											onClick={() => removePropsRow(index)}
											className="justify-self-end text-muted-foreground hover:text-destructive"
										>
											<Icon icon="solar:trash-bin-trash-bold" className="h-4 w-4" />
										</Button>
									</div>
								))}
							</div>
						)}
					</section>

					<section className="space-y-2">
						<div className="flex items-center justify-between">
							<Label className="text-sm">其他密钥（可选）</Label>
							<Button type="button" variant="ghost" size="sm" onClick={addSecretRow}>
								<Icon icon="solar:add-circle-bold" className="mr-1 h-4 w-4" />
								添加
							</Button>
						</div>
						<p className="text-xs text-muted-foreground">
							用于 API Token、Access Key 等额外敏感信息。提交后会交给后端加密存储。
						</p>
						{secretRows.length === 0 ? (
							<div className="rounded border border-dashed border-muted-foreground/40 px-3 py-4 text-center text-xs text-muted-foreground">
								暂无额外密钥
							</div>
						) : (
							<div className="space-y-2">
								{secretRows.map((row, index) => (
									<div key={index} className="grid gap-2 md:grid-cols-[1fr,1fr,auto]">
										<Input
											value={row.key}
											placeholder="密钥名称"
											onChange={(event) => updateSecretRow(index, { key: event.target.value })}
										/>
										<Input
											value={row.value}
											placeholder="密钥值"
											onChange={(event) => updateSecretRow(index, { value: event.target.value })}
											type="password"
										/>
										<Button
											type="button"
											variant="ghost"
											size="icon"
											onClick={() => removeSecretRow(index)}
											className="justify-self-end text-muted-foreground hover:text-destructive"
										>
											<Icon icon="solar:trash-bin-trash-bold" className="h-4 w-4" />
										</Button>
									</div>
								))}
							</div>
						)}
					</section>
				</div>

				<DialogFooter className="gap-2">
					<Button type="button" variant="ghost" onClick={onClose} disabled={disableSubmit}>
						取消
					</Button>
					<Button onClick={() => void handleSubmit()} disabled={disableSubmit}>
						{disableSubmit ? "保存中…" : "保存"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}

const renderTypeLabel = (type: string) => {
	switch (type) {
		case "TRINO":
			return "Trino";
		case "POSTGRES":
			return "PostgreSQL";
		case "MYSQL":
			return "MySQL";
		case "CLICKHOUSE":
			return "ClickHouse";
		case "ORACLE":
			return "Oracle";
		case "SQLSERVER":
			return "SQL Server";
		case "API":
			return "API 服务";
		case "EXTERNAL":
			return "外部数据";
		default:
			return type;
	}
};

const toRows = (input?: Record<string, any> | null): KeyValuePair[] => {
	if (!input) return [];
	return Object.entries(input)
		.filter(([key]) => key !== undefined && key !== null)
		.map(([key, value]) => ({
			key,
			value: value == null ? "" : serializeValue(value),
		}));
};

const buildMap = (rows: KeyValuePair[]) => {
	const map: Record<string, any> = {};
	rows.forEach(({ key, value }) => {
		const trimmedKey = key.trim();
		if (!trimmedKey) {
			return;
		}
		map[trimmedKey] = value;
	});
	return map;
};

const serializeValue = (value: any) => {
	if (typeof value === "string") return value;
	try {
		return JSON.stringify(value);
	} catch {
		return String(value ?? "");
	}
};
