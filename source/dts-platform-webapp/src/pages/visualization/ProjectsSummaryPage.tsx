import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { getProjectsSummary } from "@/api/platformApi";

export default function ProjectsSummaryPage() {
	const [data, setData] = useState<any>(null);
	useEffect(() => {
		(async () => setData(await getProjectsSummary()))();
	}, []);
	return (
		<div className="grid gap-4 md:grid-cols-3">
			<Card>
				<CardHeader>
					<CardTitle>项目总数</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.count ?? "-"}</CardContent>
			</Card>
			<Card>
				<CardHeader>
					<CardTitle>进行中</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.active ?? "-"}</CardContent>
			</Card>
			<Card>
				<CardHeader>
					<CardTitle>延期</CardTitle>
				</CardHeader>
				<CardContent className="text-2xl font-bold">{data?.delayed ?? "-"}</CardContent>
			</Card>
		</div>
	);
}
