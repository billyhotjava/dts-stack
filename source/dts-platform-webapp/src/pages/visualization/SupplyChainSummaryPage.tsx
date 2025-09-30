import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getSupplySummary } from "@/api/platformApi";

export default function SupplyChainSummaryPage() {
	const [data, setData] = useState<any>(null);
	useEffect(() => {
		(async () => setData(await getSupplySummary()))();
	}, []);
	return (
		<div className="grid gap-4 md:grid-cols-2">
			<Card>
				<CardHeader>
					<CardTitle>到货率</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{(data?.onTimeRate ?? 0) * 100}%</CardContent>
			</Card>
			<Card>
				<CardHeader>
					<CardTitle>交付周期(天)</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.leadTimeDays ?? "-"}</CardContent>
			</Card>
		</div>
	);
}
