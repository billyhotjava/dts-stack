import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import type { DatasetClassificationItem, SyncStatus, UserClassificationItem } from "@/api/services/iamService";
import { ResultStatus } from "#/enum";

const API_BASE = "/api/iam/classification";

let users: UserClassificationItem[] = Array.from({ length: 18 }).map((_, index) => ({
	id: index + 1,
	username: faker.internet.userName(),
	displayName: faker.person.fullName(),
	orgPath: faker.helpers.arrayElements(["数据与智能中心", "数据平台组", "业务线A", "业务线B", "治理组"], {
		min: 1,
		max: 3,
	}),
	roles: faker.helpers.arrayElements(["SYSADMIN", "AUTHADMIN", "AUDITADMIN", "DATA_STEWARD", "DATA_ANALYST"], {
		min: 1,
		max: 2,
	}),
	projects: faker.helpers.arrayElements(["平台演进", "质量治理", "客户洞察", "报表集成"], { min: 1, max: 2 }),
	securityLevel: faker.helpers.arrayElement(["公开", "内部", "秘密", "机密"]),
	updatedAt: faker.date.recent({ days: 7 }).toISOString(),
}));

const datasets: DatasetClassificationItem[] = Array.from({ length: 24 }).map((_, index) => ({
	id: `ds_${index + 1}`,
	name:
		faker.helpers.arrayElement(["ods_customers", "dwd_orders", "dws_gmv_daily", "dim_products", "ads_sales_report"]) +
		`_${(index % 5) + 1}`,
	domain: faker.helpers.arrayElement(["客户域", "订单域", "商品域", "营销域", "财务域"]),
	owner: faker.person.fullName(),
	classification: faker.helpers.arrayElement(["公开", "内部", "秘密", "机密"]),
}));

let syncStatus: SyncStatus = {
	lastSyncAt: faker.date.recent({ days: 2 }).toISOString(),
	deltaCount: faker.number.int({ min: 0, max: 8 }),
	failures: Array.from({ length: faker.number.int({ min: 0, max: 3 }) }).map((_, i) => ({
		id: `f_${i + 1}`,
		type: faker.helpers.arrayElement(["USER", "DATASET"]),
		target: faker.helpers.arrayElement(["alice", "bob", "ods_customers", "dwd_orders", "ads_sales_report"]),
		reason: faker.helpers.arrayElement(["连接 dts-admin 超时", "catalog 服务不可达", "数据格式不兼容"]),
	})),
};

export const iamHandlers = [
	// 用户搜索（前缀/模糊匹配）
	http.get(`${API_BASE}/users/search`, ({ request }) => {
		const url = new URL(request.url);
		const keyword = (url.searchParams.get("keyword") || "").toLowerCase();
		const list = keyword
			? users.filter((u) => u.username.toLowerCase().includes(keyword) || u.displayName.toLowerCase().includes(keyword))
			: users.slice(0, 5);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: list });
	}),

	// 拉取最新（模拟刷新某用户密级等信息）
	http.post(`${API_BASE}/users/:id/refresh`, ({ params }) => {
		const id = Number(params.id);
		const idx = users.findIndex((u) => u.id === id);
		if (idx === -1) {
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "用户不存在" }, { status: 404 });
		}
		const next = {
			...users[idx],
			securityLevel: faker.helpers.arrayElement(["公开", "内部", "秘密", "机密"]),
			updatedAt: new Date().toISOString(),
		} satisfies UserClassificationItem;
		users[idx] = next;
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: next });
	}),

	// 数据集密级总览
	http.get(`${API_BASE}/datasets`, () => {
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: datasets });
	}),

	// 同步状态
	http.get(`${API_BASE}/sync/status`, () => {
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: syncStatus });
	}),

	// 立即同步（模拟增量变化和失败项变化）
	http.post(`${API_BASE}/sync/execute`, () => {
		syncStatus = {
			lastSyncAt: new Date().toISOString(),
			deltaCount: faker.number.int({ min: 0, max: 5 }),
			failures: Array.from({ length: faker.number.int({ min: 0, max: 2 }) }).map((_, i) => ({
				id: `f_${Date.now()}_${i}`,
				type: faker.helpers.arrayElement(["USER", "DATASET"]),
				target: faker.helpers.arrayElement(["charlie", "ods_customers", "dws_gmv_daily"]),
				reason: faker.helpers.arrayElement(["dts-admin 超时", "catalog 同步失败"]),
			})),
		};
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: syncStatus });
	}),

	// 失败项重试
	http.post(`${API_BASE}/sync/retry/:id`, ({ params }) => {
		const id = String(params.id);
		syncStatus = {
			...syncStatus,
			failures: syncStatus.failures.filter((f) => f.id !== id),
		};
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: syncStatus });
	}),

	// ---------- 授权视图与策略（对象维度/主体维度） ----------
	// 域/数据集树
	http.get(`/api/iam/policies/domains-with-datasets`, () => {
		const domains = ["客户域", "订单域", "商品域", "营销域"] as const;
		const result = domains.map((domain, di) => ({
			id: `dom_${di + 1}`,
			name: domain,
			datasets: Array.from({ length: faker.number.int({ min: 2, max: 4 }) }).map((_, i) => {
				const name = faker.helpers.arrayElement([
					"ods_customers",
					"dwd_orders",
					"dws_gmv_daily",
					"dim_products",
					"ads_sales_report",
				]);
				return {
					id: `${domain}_${i + 1}`,
					name: `${name}_${i + 1}`,
					fields: Array.from({ length: faker.number.int({ min: 6, max: 12 }) }).map(() =>
						faker.helpers.arrayElement([
							"customer_id",
							"customer_name",
							"org_id",
							"project_id",
							"order_id",
							"amount",
							"created_at",
							"channel",
							"region",
							"category",
						]),
					),
				};
			}),
		}));
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: result });
	}),

	// 对象维度：某数据集策略明细
	http.get(`/api/iam/policies/dataset/:id/policies`, () => {
		const objectPolicies = Array.from({ length: faker.number.int({ min: 2, max: 6 }) }).map(() => ({
			subjectType: faker.helpers.arrayElement(["USER", "ROLE", "ORG", "PROJECT"] as const),
			subjectId: faker.string.alphanumeric(6),
			subjectName: faker.helpers.arrayElement(["alice", "bob", "data_analyst", "数据平台组", "客户洞察"]),
			effect: faker.helpers.arrayElement(["ALLOW", "DENY"] as const),
			validFrom: faker.date.recent({ days: 30 }).toISOString(),
			validTo: faker.helpers.maybe(() => faker.date.soon({ days: 30 }).toISOString()),
			source: faker.helpers.arrayElement(["MANUAL", "INHERITED", "SYSTEM"] as const),
		}));

		const sampleFields = ["customer_id", "customer_name", "org_id", "project_id", "order_id", "amount", "created_at"];
		const fieldPolicies = Array.from({ length: faker.number.int({ min: 6, max: 12 }) }).map(() => ({
			field: faker.helpers.arrayElement(sampleFields),
			subjectType: faker.helpers.arrayElement(["USER", "ROLE", "ORG", "PROJECT"] as const),
			subjectName: faker.helpers.arrayElement(["alice", "data_analyst", "数据平台组"]),
			effect: faker.helpers.arrayElement(["ALLOW", "DENY"] as const),
		}));

		const rowConditions = Array.from({ length: faker.number.int({ min: 1, max: 3 }) }).map(() => ({
			subjectType: faker.helpers.arrayElement(["ROLE", "ORG", "PROJECT"] as const),
			subjectName: faker.helpers.arrayElement(["data_analyst", "数据治理组", "客户洞察"]),
			expression: faker.helpers.arrayElement([
				"org_id IN (${orgChildren}) AND project_id = :projectId",
				"region = :region AND amount < 100000",
			]),
			description: faker.helpers.maybe(() => "按组织树与项目隔离"),
		}));

		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { objectPolicies, fieldPolicies, rowConditions } });
	}),

	// 主体维度：主体可见对象/字段/行域
	http.get(`/api/iam/policies/subject/:type/:id/visible`, () => {
		const objects = Array.from({ length: faker.number.int({ min: 4, max: 10 }) }).map(() => ({
			datasetId: faker.string.alphanumeric(8),
			datasetName:
				faker.helpers.arrayElement(["ods_customers", "dwd_orders", "dws_gmv_daily", "dim_products"]) +
				`_${faker.number.int({ min: 1, max: 3 })}`,
			effect: "ALLOW" as const,
		}));
		const fields = objects.slice(0, 3).flatMap((obj) => [
			{ datasetName: obj.datasetName, field: "customer_id", effect: "ALLOW" },
			{ datasetName: obj.datasetName, field: "customer_name", effect: "DENY" },
			{ datasetName: obj.datasetName, field: "org_id", effect: "ALLOW" },
		]);
		const expressions = objects.slice(0, 2).map((obj) => ({
			datasetName: obj.datasetName,
			expression: "org_id IN (${orgChildren}) AND project_id = :projectId",
		}));
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { objects, fields, expressions } });
	}),

	// 主体搜索（用于批量授权向导）
	http.get(`/api/iam/policies/subjects`, ({ request }) => {
		const url = new URL(request.url);
		const type = url.searchParams.get("type") || "user";
		const keyword = (url.searchParams.get("keyword") || "").toLowerCase();
		const pool =
			type === "user"
				? ["alice", "bob", "charlie", "dba", "curator"]
				: type === "role"
					? ["DATA_ANALYST", "DATA_STEWARD", "SYSADMIN"]
					: type === "org"
						? ["数据平台组", "数据治理组", "业务数据中心"]
						: ["客户洞察", "平台演进", "质量治理"];
		const list = pool
			.filter((n) => n.toLowerCase().includes(keyword))
			.map((n, i) => ({ id: `${type}_${i + 1}`, name: n }));
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: list });
	}),

	// 预检冲突
	http.post(`/api/iam/policies/preview`, async () => {
		const conflicts = [
			{
				kind: "override",
				target: "对象级: dwd_orders_1",
				subject: "ROLE:DATA_ANALYST",
				old: "ALLOW (2024-01-01 ~ 2024-12-31)",
				next: "DENY (2024-09-01 ~ 2025-09-01)",
			},
			{
				kind: "conflict",
				target: "字段级: ods_customers_2.customer_name",
				subject: "ORG:数据平台组",
				old: "ALLOW",
				next: "DENY",
			},
		];
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { conflicts } });
	}),

	// 提交应用
	http.post(`/api/iam/policies/apply`, async () => {
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { ok: true, appliedAt: new Date().toISOString() } });
	}),
];
