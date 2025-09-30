import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getCockpitMetrics } from "@/api/platformApi";

export default function CockpitPage() {
	const [data, setData] = useState<any>(null);
	useEffect(() => {
		(async () => setData(await getCockpitMetrics()))();
	}, []);
	const kpis = (data?.kpi ?? []) as { name: string; value: number }[];
	const trend = (data?.trend ?? []) as number[];
	return (
		<div className="grid gap-4 md:grid-cols-2">
			{kpis.map((k, i) => (
				<Card key={i}>
					<CardHeader>
						<CardTitle className="text-sm">{k.name}</CardTitle>
					</CardHeader>
					<CardContent className="text-2xl font-bold">{k.value.toLocaleString()}</CardContent>
				</Card>
			))}
			<Card className="md:col-span-2">
				<CardHeader>
					<CardTitle className="text-sm">趋势</CardTitle>
				</CardHeader>
				<CardContent className="flex gap-2">
					{trend.map((v, i) => (
						<div key={i} className="h-24 w-6 bg-primary/20" style={{ height: `${Math.max(8, v)}px` }} />
					))}
				</CardContent>
			</Card>
		</div>
	);
}
