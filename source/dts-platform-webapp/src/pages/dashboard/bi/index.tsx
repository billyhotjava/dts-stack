import { useCallback, useEffect, useMemo, useState } from "react";
import api from "@/api/apiClient";
import { Title, Text } from "@/ui/typography";

type BiScreen = {
	code?: string;
	name?: string;
	level?: string;
	url?: string;
	engine?: string;
};

type ApiEnvelope<T> = { status: any; message?: string; data: T };

function levelBadge(level: string | undefined) {
	const upper = String(level || "").toUpperCase();
	if (upper === "CONFIDENTIAL") return { label: "机密", cls: "bg-rose-50 text-rose-700 border-rose-200" };
	if (upper === "SECRET") return { label: "秘密", cls: "bg-amber-50 text-amber-700 border-amber-200" };
	if (upper === "INTERNAL") return { label: "内部", cls: "bg-blue-50 text-blue-700 border-blue-200" };
	return { label: "公开", cls: "bg-emerald-50 text-emerald-700 border-emerald-200" };
}

export default function BiScreensPage() {
	const [items, setItems] = useState<BiScreen[]>([]);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const resp = await api.get<ApiEnvelope<BiScreen[]>>({ url: "/dashboards" });
			const data: any = (resp as any)?.data ?? (resp as any);
			setItems(Array.isArray(data) ? data : []);
		} catch (e: any) {
			setError(String(e?.message || "加载失败"));
			setItems([]);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const hasItems = useMemo(() => Array.isArray(items) && items.length > 0, [items]);

	const open = useCallback((item: BiScreen) => {
		const url = String(item?.url || "").trim();
		if (!url) return;

		try {
			const payload = JSON.stringify({
				code: item.code,
				name: item.name,
				level: item.level,
				url: item.url,
				engine: item.engine || "hetu",
			});
			navigator.sendBeacon(
				"/api/dashboards/visit",
				new Blob([payload], { type: "application/json" }),
			);
		} catch {}

		window.location.href = url;
	}, []);

	return (
		<div className="space-y-5">
			<section className="bg-white border rounded-lg p-5 shadow-sm">
				<Title as="h2" className="text-2xl font-bold mb-2">
					驾驶舱目录
				</Title>
				<Text variant="body3" className="text-slate-600">
					按部门/角色/密级展示可访问的大屏链接；点击将记录审计日志。
				</Text>
			</section>

			<section className="bg-white border rounded-lg p-4 shadow-sm">
				<div className="flex items-center justify-between mb-3">
					<Text variant="body2" className="font-semibold">
						可访问大屏
					</Text>
					<button
						type="button"
						onClick={load}
						className="text-xs px-2 py-1 rounded-md border bg-slate-50 hover:bg-slate-100"
						disabled={loading}
					>
						{loading ? "加载中…" : "刷新"}
					</button>
				</div>

				{error ? (
					<div className="border rounded-md p-3 bg-rose-50 text-rose-700 text-sm">{error}</div>
				) : null}

				{!loading && !error && !hasItems ? (
					<div className="text-sm text-slate-600">
						暂无可访问大屏。请联系管理员在平台配置河图分享链接（或检查密级/部门/角色）。
					</div>
				) : null}

				<div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
					{items.map((item) => {
						const badge = levelBadge(item.level);
						return (
							<div key={item.code || item.url} className="border rounded-md p-3">
								<div className="flex items-start justify-between gap-2">
									<div className="min-w-0">
										<div className="text-sm font-medium text-slate-800 truncate">{item.name || item.code}</div>
										<div className="text-xs text-slate-500 truncate">{item.engine || "hetu"}</div>
									</div>
									<span className={`text-[11px] px-2 py-0.5 rounded-full border ${badge.cls}`}>
										{badge.label}
									</span>
								</div>

								<div className="mt-3 flex items-center justify-between">
									<a
										href={item.url}
										onClick={(e) => {
											e.preventDefault();
											open(item);
										}}
										className="inline-flex items-center justify-center px-3 py-2 rounded-md bg-blue-600 text-white text-sm"
									>
										打开
									</a>
									<span className="text-[11px] text-slate-500">审计：开启</span>
								</div>
							</div>
						);
					})}
				</div>
			</section>
		</div>
	);
}

