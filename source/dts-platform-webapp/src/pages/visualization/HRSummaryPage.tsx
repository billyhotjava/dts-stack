import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getHrSummary } from "@/api/platformApi";

export default function HRSummaryPage() {
	const [data, setData] = useState<any>(null);
	useEffect(() => {
		(async () => setData(await getHrSummary()))();
	}, []);
	return (
		<div className="grid gap-4 md:grid-cols-2">
			<Card>
				<CardHeader>
					<CardTitle>在岗人数</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.headcount ?? "-"}</CardContent>
			</Card>
			<Card>
				<CardHeader>
					<CardTitle>离职率</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{(data?.attrition ?? 0) * 100}%</CardContent>
			</Card>
		</div>
	);
}
