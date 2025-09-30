import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getFinanceSummary } from "@/api/platformApi";

export default function FinanceSummaryPage() {
	const [data, setData] = useState<any>(null);
	useEffect(() => {
		(async () => setData(await getFinanceSummary()))();
	}, []);
	return (
		<div className="grid gap-4 md:grid-cols-3">
			<Card>
				<CardHeader>
					<CardTitle>收入</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.revenue ?? "-"}</CardContent>
			</Card>
			<Card>
				<CardHeader>
					<CardTitle>成本</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.cost ?? "-"}</CardContent>
			</Card>
			<Card>
				<CardHeader>
					<CardTitle>利润</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.profit ?? "-"}</CardContent>
			</Card>
		</div>
	);
}
