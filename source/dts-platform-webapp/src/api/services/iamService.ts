import apiClient from "../apiClient";

export type UserClassificationItem = {
	id: number;
	username: string;
	displayName: string;
	orgPath: string[];
	roles: string[];
	projects: string[];
	securityLevel: string; // 公开/内部/秘密/机密
	updatedAt?: string;
};

export type DatasetClassificationItem = {
	id: string;
	name: string;
	domain: string;
	owner: string;
	classification: string; // 公开/内部/秘密/机密
};

export type SyncFailureItem = {
	id: string;
	type: "USER" | "DATASET";
	target: string; // username or dataset name
	reason: string;
};

export type SyncStatus = {
	lastSyncAt: string;
	deltaCount: number;
	failures: SyncFailureItem[];
};

const BASE = "/iam/classification";

function searchUsers(keyword: string) {
	return apiClient.get<UserClassificationItem[]>({
		url: `${BASE}/users/search`,
		params: { keyword },
	});
}

function refreshUser(id: number) {
	return apiClient.post<UserClassificationItem>({
		url: `${BASE}/users/${id}/refresh`,
	});
}

function getDatasets() {
	return apiClient.get<DatasetClassificationItem[]>({
		url: `${BASE}/datasets`,
	});
}

function getSyncStatus() {
	return apiClient.get<SyncStatus>({
		url: `${BASE}/sync/status`,
	});
}

function runSync() {
	return apiClient.post<SyncStatus>({
		url: `${BASE}/sync/execute`,
	});
}

function retryFailure(id: string) {
	return apiClient.post<SyncStatus>({
		url: `${BASE}/sync/retry/${id}`,
	});
}

export default {
	searchUsers,
	refreshUser,
	getDatasets,
	getSyncStatus,
	runSync,
	retryFailure,
};
