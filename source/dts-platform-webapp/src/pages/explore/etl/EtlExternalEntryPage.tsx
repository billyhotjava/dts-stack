import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { checkExternalLink, getExternalLink, upsertExternalLink } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Switch } from "@/ui/switch";

type ExternalLink = {
	entryKey: string;
	name?: string | null;
	url?: string | null;
	description?: string | null;
	enabled?: boolean | null;
};

type LinkCheckResult = {
	entryKey: string;
	url?: string | null;
	reachable?: boolean;
	status?: string | null;
	httpStatus?: number | null;
	durationMs?: number | null;
	error?: string | null;
};

export default function EtlExternalEntryPage(props: { entryKey: string; title: string; description?: string }) {
	const { entryKey, title, description } = props;
	const userInfo = useUserInfo() as any;
	const roles: string[] = useMemo(() => (Array.isArray(userInfo?.roles) ? userInfo.roles.map((r: any) => String(r ?? "").toUpperCase()) : []), [userInfo]);
	const canEdit = useMemo(
		() =>
			roles.includes("ROLE_OP_ADMIN") ||
			roles.includes("ROLE_ADMIN") ||
			roles.includes("ROLE_INST_DATA_OWNER") ||
			roles.includes("ROLE_INST_DATA_DEV") ||
			roles.includes("ROLE_DEPT_DATA_OWNER") ||
			roles.includes("ROLE_DEPT_DATA_DEV"),
		[roles],
	);

	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [link, setLink] = useState<ExternalLink | null>(null);
	const [checking, setChecking] = useState(false);
	const [checkResult, setCheckResult] = useState<LinkCheckResult | null>(null);

	const [name, setName] = useState("");
	const [url, setUrl] = useState("");
	const [enabled, setEnabled] = useState(true);
	const [notes, setNotes] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const resp: any = await getExternalLink(entryKey);
			const l: ExternalLink | null = resp
				? {
						entryKey: String(resp?.entryKey ?? entryKey),
						name: resp?.name ?? null,
						url: resp?.url ?? null,
						description: resp?.description ?? null,
						enabled: resp?.enabled ?? true,
					}
				: null;
			setLink(l);
			setName(String(l?.name ?? title));
			setUrl(String(l?.url ?? ""));
			setEnabled(Boolean(l?.enabled ?? true));
			setNotes(String(l?.description ?? ""));
			setCheckResult(null);
		} catch (e: any) {
			toast.error(e?.message || "加载入口配置失败");
		} finally {
			setLoading(false);
		}
	}, [entryKey, title]);

	useEffect(() => {
		void load();
	}, [load]);

	const openUrl = () => {
		const target = (link?.url || url || "").trim();
		if (!target) {
			toast.error("未配置入口URL");
			return;
		}
		window.open(target, "_blank", "noopener,noreferrer");
	};

	const check = useCallback(async () => {
		const target = (link?.url || url || "").trim();
		if (!target) {
			toast.error("未配置入口URL");
			return;
		}
		setChecking(true);
		try {
			const resp: any = await checkExternalLink(entryKey);
			setCheckResult({
				entryKey: String(resp?.entryKey ?? entryKey),
				url: resp?.url ?? null,
				reachable: Boolean(resp?.reachable ?? false),
				status: resp?.status ?? null,
				httpStatus: typeof resp?.httpStatus === "number" ? resp.httpStatus : null,
				durationMs: typeof resp?.durationMs === "number" ? resp.durationMs : null,
				error: resp?.error ?? null,
			});
		} catch (e: any) {
			toast.error(e?.message || "检查失败");
		} finally {
			setChecking(false);
		}
	}, [entryKey, link?.url, url]);

	const save = useCallback(async () => {
		if (!canEdit) return;
		if (!url.trim()) {
			toast.error("请输入URL");
			return;
		}
		setSaving(true);
		try {
			const payload: any = {
				name: name.trim() || title,
				url: url.trim(),
				enabled,
				description: notes.trim() || null,
			};
			await upsertExternalLink(entryKey, payload);
			toast.success("已保存");
			await load();
		} catch (e: any) {
			toast.error(e?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	}, [canEdit, url, name, enabled, notes, entryKey, title, load]);

	const effectiveUrl = (link?.url || url || "").trim();
	const effectiveEnabled = Boolean(link?.enabled ?? enabled);
	const statusBadge = checkResult ? (
		checkResult.reachable ? (
			<Badge variant="secondary">可访问</Badge>
		) : (
			<Badge variant="outline">不可访问</Badge>
		)
	) : null;

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader>
					<CardTitle>{title}</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="text-sm text-muted-foreground">
						{description || "当前ETL/数据开发由外部平台（星环）承担，本系统只提供统一入口与审计兜底。"}
					</div>
					<div className="flex flex-col gap-2 md:flex-row md:items-center">
						<Button variant="secondary" disabled={loading || !effectiveUrl || !effectiveEnabled} onClick={openUrl}>
							打开外部平台
						</Button>
						<Button variant="outline" disabled={loading || checking || !effectiveUrl} onClick={() => void check()}>
							连通性检查
						</Button>
						<div className="text-xs text-muted-foreground break-all">{effectiveUrl || "未配置入口URL"}</div>
					</div>
					{checkResult ? (
						<div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
							{statusBadge}
							{typeof checkResult.httpStatus === "number" ? <span>HTTP {checkResult.httpStatus}</span> : null}
							{typeof checkResult.durationMs === "number" ? <span>{checkResult.durationMs}ms</span> : null}
							{checkResult.error ? <span className="break-all">错误：{checkResult.error}</span> : null}
						</div>
					) : null}
				</CardContent>
			</Card>

			{canEdit ? (
				<Card>
					<CardHeader>
						<CardTitle className="text-base">入口配置</CardTitle>
					</CardHeader>
					<CardContent className="space-y-3">
						<div className="space-y-2">
							<Label>名称</Label>
							<Input value={name} onChange={(e) => setName(e.target.value)} />
						</div>
						<div className="space-y-2">
							<Label>URL *</Label>
							<Input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="例如：https://tdh.xxx.com/" />
						</div>
						<div className="space-y-2">
							<Label>备注</Label>
							<Input value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="可选" />
						</div>
						<div className="flex items-center justify-between">
							<div className="space-y-1">
								<Label>启用</Label>
								<div className="text-xs text-muted-foreground">停用后仅保留配置，不对外开放入口</div>
							</div>
							<Switch checked={enabled} onCheckedChange={(checked) => setEnabled(checked)} />
						</div>
						<div className="flex gap-2">
							<Button variant="secondary" disabled={loading} onClick={() => void load()}>
								重新加载
							</Button>
							<Button disabled={saving} onClick={() => void save()}>
								保存
							</Button>
						</div>
					</CardContent>
				</Card>
			) : null}
		</div>
	);
}
