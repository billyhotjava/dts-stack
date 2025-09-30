import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import type {
	ApiFieldDef,
	ApiServiceDetail,
	ApiServiceSummary,
	ApiServiceStatus,
	ApiHttpMethod,
} from "@/api/services/apiServicesService";
import { ResultStatus } from "#/enum";

const methods: ApiHttpMethod[] = ["GET", "POST", "PUT", "DELETE"];
const statuses: ApiServiceStatus[] = ["PUBLISHED", "OFFLINE"];

const summaries: ApiServiceSummary[] = Array.from({ length: 26 }).map((_, i) => {
	const method = faker.helpers.arrayElement(methods);
	const ds = faker.helpers.arrayElement([
		"ods_customers",
		"dwd_orders",
		"dws_gmv_daily",
		"dim_products",
		"ads_sales_report",
	]);
	const sparkline = Array.from({ length: 16 }).map(() => faker.number.int({ min: 0, max: 100 }));
	return {
		id: `api_${i + 1}`,
		name: `${ds.replace(/_/g, "-")}-${method.toLowerCase()}`,
		dataset: ds,
		method,
		path: `/data/api/${ds}`,
		classification: faker.helpers.arrayElement(["公开", "内部", "机密", "绝密"]),
		qps: faker.number.int({ min: 10, max: 200 }),
		qpsLimit: faker.number.int({ min: 100, max: 1000 }),
		dailyLimit: faker.number.int({ min: 5000, max: 50000 }),
		status: faker.helpers.arrayElement(statuses),
		recentCalls: sparkline.reduce((a, b) => a + b, 0),
		sparkline,
	} satisfies ApiServiceSummary;
});

function buildFields(): ApiFieldDef[] {
	const pool = [
		{ name: "customer_id", type: "STRING", masked: false },
		{ name: "customer_name", type: "STRING", masked: true },
		{ name: "org_id", type: "INT", masked: false },
		{ name: "project_id", type: "STRING", masked: false },
		{ name: "order_id", type: "STRING", masked: false },
		{ name: "amount", type: "DECIMAL", masked: false },
		{ name: "mobile", type: "STRING", masked: true },
		{ name: "created_at", type: "TIMESTAMP", masked: false },
	];
	return faker.helpers.arrayElements(pool, { min: 3, max: 6 });
}

export const apiHandlers = [
	http.get("/api/services/apis", ({ request }) => {
		const url = new URL(request.url);
		const keyword = (url.searchParams.get("keyword") || "").toLowerCase();
		const method = url.searchParams.get("method");
		const status = url.searchParams.get("status");
		const list = summaries.filter((s) => {
			const kw = keyword ? (s.name + s.dataset + s.path).toLowerCase().includes(keyword) : true;
			const mm = method ? s.method === method : true;
			const ss = status && status !== "all" ? s.status === status : true;
			return kw && mm && ss;
		});
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: list });
	}),

	http.get("/api/services/apis/:id", ({ params }) => {
		const id = String(params.id);
		const head = summaries.find((s) => s.id === id) || summaries[0];
		const detail: ApiServiceDetail = {
			...head,
			input: buildFields(),
			output: buildFields(),
			policy: {
				minLevel: faker.helpers.arrayElement(["内部", "机密", "绝密"]),
				maskedColumns: ["phone", "id_no"],
				rowFilter: "org_id IN (:org_scope) AND level <= :user_level",
			},
			quotas: {
				qpsLimit: head.qpsLimit,
				dailyLimit: head.dailyLimit,
				dailyRemaining: faker.number.int({ min: 0, max: head.dailyLimit }),
			},
			audit: {
				last24hCalls: faker.number.int({ min: 0, max: 10000 }),
				maskedHits: faker.number.int({ min: 0, max: 5000 }),
				denies: faker.number.int({ min: 0, max: 50 }),
			},
		};
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: detail });
	}),

	http.post("/api/services/apis/:id/try", async () => {
		const cols = ["customer_id", "customer_name", "phone", "id_no", "amount", "org_id", "level", "created_at"];
		const maskedCols = ["phone", "id_no"];
		const rows = Array.from({ length: 20 }).map((_, i) => ({
			customer_id: `CUS-${1000 + i}`,
			customer_name: faker.person.fullName(),
			phone: "139****00" + String(i).padStart(2, "0"),
			id_no: "51************" + String(1000 + i).slice(-4),
			amount: faker.number.float({ min: 10, max: 9999, precision: 0.01 }),
			org_id: faker.number.int({ min: 10, max: 20 }),
			level: faker.helpers.arrayElement(["公开", "内部", "机密", "绝密"]),
			created_at: faker.date.recent({ days: 30 }).toISOString(),
		}));
		const filteredRowCount = faker.number.int({ min: 0, max: 5 });
		const policyHits = [
			"✓ 密级检查通过",
			"✓ 行过滤（org_id = [12,13]）",
			"✓ 列掩码：phone → mask_phone",
			"❶ 列 id_no 被隐藏（未授权）",
		];
		return HttpResponse.json({
			status: ResultStatus.SUCCESS,
			data: { columns: cols, maskedColumns: maskedCols, rows, filteredRowCount, policyHits },
		});
	}),

	http.get("/api/services/apis/:id/metrics", () => {
		const head = summaries[0];
		const now = Date.now();
		const series = Array.from({ length: 24 }).map((_, i) => ({
			timestamp: now - (23 - i) * 60 * 60 * 1000,
			calls: faker.number.int({ min: 0, max: 500 }),
			qps: faker.number.int({ min: 0, max: head.qpsLimit }),
		}));
		const levelDistribution = [
			{ label: "公开", value: faker.number.int({ min: 10, max: 50 }) },
			{ label: "内部", value: faker.number.int({ min: 10, max: 50 }) },
			{ label: "机密", value: faker.number.int({ min: 10, max: 50 }) },
			{ label: "绝密", value: faker.number.int({ min: 5, max: 30 }) },
		];
		const recentCalls = Array.from({ length: 10 }).map(() => ({
			user: faker.helpers.arrayElement(["alice", "bob", "charlie", "dba", "curator"]),
			level: faker.helpers.arrayElement(["公开", "内部", "机密", "绝密"]),
			rowCount: faker.number.int({ min: 1, max: 200 }),
			policy: faker.helpers.arrayElement(["mask:phone", "mask:id_no", "row-filter", "deny"]),
		}));
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { series, levelDistribution, recentCalls } });
	}),
];
