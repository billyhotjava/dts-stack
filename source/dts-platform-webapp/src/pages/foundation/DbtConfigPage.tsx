import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { listInfraDataSources } from "@/api/services/infraService";
import { getDbtConfig, updateDbtConfig } from "@/api/platformApi";

type InfraDataSource = {
	id: string;
	name: string;
	type?: string | null;
	jdbcUrl?: string | null;
};

export default function DbtConfigPage() {
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [sources, setSources] = useState<InfraDataSource[]>([]);
	const [config, setConfig] = useState<any | null>(null);
	const [profileStatus, setProfileStatus] = useState<any | null>(null);

	const loadConfig = useCallback(async () => {
		setLoading(true);
		try {
			const [cfg, list] = await Promise.all([
				(getDbtConfig() as any).catch(() => null),
				listInfraDataSources().catch(() => [] as any),
			]);
			setConfig(
				cfg?.config || {
					projectDir: "/opt/dts/dbt",
					profilesDir: "/opt/dts/dbt-profiles",
					profileName: "dts",
					targetName: "dev",
					targetDataSourceId: null,
					database: "",
					schema: "",
					vars: {},
				},
			);
			setProfileStatus(cfg?.profileStatus || null);
			setSources(Array.isArray(list) ? list : []);
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "加载 dbt 配置失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadConfig();
	}, [loadConfig]);

	const targetOptions = useMemo(() => {
		return sources.map((s) => ({
			value: s.id,
			label: `${s.name}${s.type ? ` · ${s.type}` : ""}`,
		}));
	}, [sources]);

	const save = useCallback(async () => {
		if (!config) return;
		setSaving(true);
		try {
			const payload = {
				projectDir: config.projectDir || "/opt/dts/dbt",
				profilesDir: config.profilesDir || "/opt/dts/dbt-profiles",
				profileName: config.profileName || "dts",
				targetName: config.targetName || "dev",
				targetDataSourceId: config.targetDataSourceId || null,
				database: config.database || null,
				schema: config.schema || null,
				vars: config.vars || {},
			};
			const resp = (await updateDbtConfig(payload)) as any;
			setConfig(resp?.config || payload);
			setProfileStatus(resp?.profileStatus || null);
			toast.success("dbt 配置已保存");
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	}, [config]);

	const onVarsChange = (value: string) => {
		if (!config) return;
		const text = value.trim();
		if (!text) {
			setConfig({ ...config, vars: {} });
			return;
		}
		try {
			const parsed = JSON.parse(text);
			if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
				setConfig({ ...config, vars: parsed });
			}
		} catch {
			// ignore parse errors here; will show as raw text
			setConfig({ ...config, varsText: value });
		}
	};

	if (loading) {
		return <div className="text-sm text-muted-foreground">加载中…</div>;
	}

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader>
					<CardTitle>dbt 执行配置</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>dbt 项目目录</Label>
							<Input
								value={config?.projectDir || ""}
								onChange={(e) => setConfig({ ...(config || {}), projectDir: e.target.value })}
								placeholder="/opt/dts/dbt"
							/>
						</div>
						<div className="space-y-2">
							<Label>profiles.yml 目录</Label>
							<Input
								value={config?.profilesDir || ""}
								onChange={(e) => setConfig({ ...(config || {}), profilesDir: e.target.value })}
								placeholder="/opt/dts/dbt-profiles"
							/>
						</div>
						<div className="space-y-2">
							<Label>profile 名称</Label>
							<Input
								value={config?.profileName || ""}
								onChange={(e) => setConfig({ ...(config || {}), profileName: e.target.value })}
								placeholder="dts"
							/>
						</div>
						<div className="space-y-2">
							<Label>target 名称</Label>
							<Input
								value={config?.targetName || ""}
								onChange={(e) => setConfig({ ...(config || {}), targetName: e.target.value })}
								placeholder="dev"
							/>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>目标数仓</Label>
							<Select
								value={config?.targetDataSourceId || "__NONE__"}
								onValueChange={(v) =>
									setConfig({ ...(config || {}), targetDataSourceId: v === "__NONE__" ? null : v })
								}
							>
								<SelectTrigger>
									<SelectValue placeholder="选择目标数据源" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="__NONE__">未选择</SelectItem>
									{targetOptions.map((opt) => (
										<SelectItem key={opt.value} value={opt.value}>
											{opt.label}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="space-y-2">
							<Label>默认数据库（可选）</Label>
							<Input
								value={config?.database || ""}
								onChange={(e) => setConfig({ ...(config || {}), database: e.target.value })}
								placeholder="warehouse"
							/>
						</div>
						<div className="space-y-2">
							<Label>默认 schema（可选）</Label>
							<Input
								value={config?.schema || ""}
								onChange={(e) => setConfig({ ...(config || {}), schema: e.target.value })}
								placeholder="ods"
							/>
						</div>
						<div className="space-y-2 md:col-span-2">
							<Label>dbt vars（JSON，可选）</Label>
							<Textarea
								value={
									config?.varsText ??
									(config?.vars && Object.keys(config.vars).length ? JSON.stringify(config.vars, null, 2) : "")
								}
								onChange={(e) => onVarsChange(e.target.value)}
								placeholder='{"run_mode":"full"}'
							/>
						</div>
					</div>
					<div className="flex items-center justify-between">
						<div className="text-xs text-muted-foreground">
							{profileStatus?.message || "保存后会尝试写入 profiles.yml"}
						</div>
						<Button onClick={save} disabled={saving}>
							{saving ? "保存中…" : "保存配置"}
						</Button>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
