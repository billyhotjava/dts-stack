export type CurrentUser = {
	id?: number | string;
	email?: string;
	first_name?: string;
	last_name?: string;
	common_name?: string;
};

export type CollectionListItem = {
	id: number | "root";
	name?: string;
	description?: string | null;
	archived?: boolean;
	location?: string | null;
	can_write?: boolean;
};

export type CollectionItem = {
	id: number;
	model: "dashboard" | "card";
	name?: string;
	description?: string | null;
	archived?: boolean;
	collection_id?: number | null;
	favorite?: boolean;
	created_at?: string;
	updated_at?: string;
};

export type DashboardListItem = {
	id: number;
	name?: string;
	description?: string | null;
	archived?: boolean;
	collection_id?: number | null;
	created_at?: string;
	updated_at?: string;
	favorite?: boolean;
};

export type DashboardDetail = DashboardListItem & {
	dashcards?: unknown[];
	parameters?: unknown[];
};

export type CardListItem = {
	id: number;
	name?: string;
	description?: string | null;
	archived?: boolean;
	collection_id?: number | null;
	display?: string;
	created_at?: string;
	updated_at?: string;
	favorite?: boolean;
};

export type CardDetail = CardListItem & {
	dataset_query?: unknown;
	visualization_settings?: unknown;
	result_metadata?: unknown;
};

async function fetchJson<T>(url: string): Promise<T> {
	const response = await fetch(url, {
		method: "GET",
		credentials: "include",
		headers: {
			accept: "application/json",
		},
	});
	if (!response.ok) {
		const text = await response.text().catch(() => "");
		throw new Error(`HTTP ${response.status} ${response.statusText}: ${text}`);
	}
	return (await response.json()) as T;
}

async function sendJson<T>(url: string, body: unknown): Promise<T> {
	const response = await fetch(url, {
		method: "POST",
		credentials: "include",
		headers: {
			accept: "application/json",
			"content-type": "application/json",
		},
		body: JSON.stringify(body ?? {}),
	});
	if (!response.ok) {
		const text = await response.text().catch(() => "");
		throw new Error(`HTTP ${response.status} ${response.statusText}: ${text}`);
	}
	return (await response.json()) as T;
}

export const analyticsApi = {
	getCurrentUser: () => fetchJson<CurrentUser>("/analytics/api/user/current"),
	getHealth: () => fetchJson<{ status?: string }>("/analytics/api/health"),
	listCollections: () => fetchJson<CollectionListItem[]>("/analytics/api/collection"),
	getCollectionItems: (id: string | number) =>
		fetchJson<CollectionItem[]>(`/analytics/api/collection/${encodeURIComponent(String(id))}/items`),
	listDashboards: () => fetchJson<DashboardListItem[]>("/analytics/api/dashboard"),
	getDashboard: (id: string | number) => fetchJson<DashboardDetail>(`/analytics/api/dashboard/${encodeURIComponent(String(id))}`),
	listCards: () => fetchJson<CardListItem[]>("/analytics/api/card"),
	getCard: (id: string | number) => fetchJson<CardDetail>(`/analytics/api/card/${encodeURIComponent(String(id))}`),
	queryCard: (id: string | number, body?: unknown) =>
		sendJson<unknown>(`/analytics/api/card/${encodeURIComponent(String(id))}/query`, body ?? {}),
};
