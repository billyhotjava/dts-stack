import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import type { PublishedReport } from "@/api/services/reportsService";
import { ResultStatus } from "#/enum";

const tools = ["Tableau", "PowerBI", "FineBI", "Superset"] as const;

const reports: PublishedReport[] = Array.from({ length: 28 }).map((_, i) => {
	const tool = faker.helpers.arrayElement(tools);
	const slug = faker.helpers.slugify(faker.commerce.productName()).toLowerCase();
	const base =
		tool === "Superset"
			? "https://bi.example.com/superset"
			: tool === "Tableau"
				? "https://bi.example.com/tableau"
				: tool === "PowerBI"
					? "https://app.powerbi.com/view?r="
					: "https://bi.example.com/finebi";
	const url = tool === "PowerBI" ? `${base}${faker.string.alphanumeric(16)}` : `${base}/r/${slug}-${i + 1}`;
	return {
		id: `r_${i + 1}`,
		title: faker.commerce.productName() + " 报表",
		biTool: tool,
		owner: faker.person.fullName(),
		domain: faker.helpers.arrayElement(["客户域", "订单域", "商品域", "营销域", "财务域"]),
		tags: faker.helpers.arrayElements(["月度", "周报", "经营", "看板", "明细"], { min: 1, max: 3 }),
		updatedAt: faker.date.recent({ days: 14 }).toISOString(),
		url,
	} satisfies PublishedReport;
});

export const reportsHandlers = [
	http.get("/api/reports/published", ({ request }) => {
		const url = new URL(request.url);
		const keyword = (url.searchParams.get("keyword") || "").toLowerCase();
		const tool = url.searchParams.get("tool");
		const data = reports.filter((r) => {
			const kw = keyword
				? r.title.toLowerCase().includes(keyword) ||
					r.owner.toLowerCase().includes(keyword) ||
					(r.domain || "").toLowerCase().includes(keyword)
				: true;
			const tw = tool ? r.biTool === tool : true;
			return kw && tw;
		});
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data });
	}),
];
