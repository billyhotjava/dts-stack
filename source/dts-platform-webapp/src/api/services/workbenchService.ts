import apiClient from "../apiClient";

export type WorkbenchOverview = {
	generatedAt?: string;
	myAssets?: number;
	todayNewAssets?: number;
};

export type WorkbenchTodoItem = {
	type: string;
	title?: string;
	status?: string;
	createdAt?: string;
	taskId?: string;
	requestId?: string;
	datasetId?: string;
	message?: string;
	requester?: string;
};

export type WorkbenchFavorite = {
	id: string;
	title: string;
	targetType?: string | null;
	targetId?: string | null;
	link?: string | null;
	metadataJson?: string | null;
	sortOrder?: number | null;
	enabled?: boolean;
};

export type WorkbenchFavoriteUpsert = {
	title: string;
	targetType?: string;
	targetId?: string;
	link?: string;
	sortOrder?: number;
	enabled?: boolean;
	metadata?: Record<string, unknown> | null;
};

export default {
	overview: () => apiClient.get<WorkbenchOverview>({ url: "/workbench/overview" }),
	todos: () => apiClient.get<WorkbenchTodoItem[]>({ url: "/workbench/todos" }),
	favorites: () => apiClient.get<WorkbenchFavorite[]>({ url: "/workbench/favorites" }),
	createFavorite: (payload: WorkbenchFavoriteUpsert) =>
		apiClient.post<WorkbenchFavorite>({ url: "/workbench/favorites", data: payload }),
	updateFavorite: (id: string, payload: WorkbenchFavoriteUpsert) =>
		apiClient.put<WorkbenchFavorite>({ url: `/workbench/favorites/${id}`, data: payload }),
	deleteFavorite: (id: string) => apiClient.delete<boolean>({ url: `/workbench/favorites/${id}` }),
};
