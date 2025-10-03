import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { getHrSummary } from "@/api/platformApi";

type SkillCoverage = { name: string; coverage: number };

export default function HRSummaryPage() {
	const [data, setData] = useState<any>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const load = async () => {
		setLoading(true);
		setError(null);
		try {
			const resp = await getHrSummary();
			setData(resp ?? null);
		} catch (e) {
			setError("加载人力概览失败");
			setData(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const skills = useMemo(
		() => (Array.isArray(data?.skills) ? (data.skills as SkillCoverage[]) : []),
		[data],
	);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">人力资源概览</CardTitle>
						<CardDescription>
							{data?.generatedAt ? `数据时间 ${new Date(data.generatedAt).toLocaleString()}` : "组织规模与技能覆盖"}
						</CardDescription>
					</div>
					<Button variant="outline" size="sm" onClick={load} disabled={loading}>
						{loading ? "刷新中" : "刷新"}
					</Button>
				</CardHeader>
				<CardContent className="space-y-4">
					{error ? <div className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">{error}</div> : null}
					<div className="grid gap-3 md:grid-cols-3">
						<div className="rounded-md border p-3">
							<div className="text-xs text-muted-foreground">在岗人数</div>
							<div className="mt-1 text-2xl font-semibold">{data?.headcount ?? "-"}</div>
						</div>
						<div className="rounded-md border p-3">
							<div className="text-xs text-muted-foreground">离职率</div>
							<div className="mt-1 text-2xl font-semibold">{typeof data?.attrition === "number" ? `${(data.attrition * 100).toFixed(1)}%` : "-"}</div>
						</div>
						<div className="rounded-md border p-3">
							<div className="text-xs text-muted-foreground">招聘需求</div>
							<div className="mt-1 text-sm text-muted-foreground">
								开放 {data?.hiring?.opened ?? 0} / 已填补 {data?.hiring?.filled ?? 0} · 关键岗 {data?.hiring?.critical ?? 0}
							</div>
						</div>
					</div>
					<div className="space-y-2">
						<div className="text-sm font-medium">技能覆盖</div>
						{skills.length ? (
							<div className="flex flex-wrap gap-2">
								{skills.map((skill) => (
									<Badge key={skill.name} variant="secondary">
										{skill.name} · {(skill.coverage * 100).toFixed(0)}%
									</Badge>
								))}
							</div>
						) : (
							<div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
								暂无技能数据。
							</div>
						)}
					</div>
				</CardContent>
			</Card>
		</div>
	);
}
