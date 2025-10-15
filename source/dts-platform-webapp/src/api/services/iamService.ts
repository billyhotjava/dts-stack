import apiClient from "../apiClient";
import { listDatasets as listCatalogDatasets } from "@/api/platformApi";
import { classificationToLabelZh } from "@/utils/classification";

export type UserClassificationItem = {
	id: string;
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
	lastSyncAt?: string | null;
	deltaCount: number;
	failures: SyncFailureItem[];
};

// Legacy admin-like endpoints已从 platform 移除。
// 为避免前端弹错，这里彻底改为纯前端“只读占位”实现，
// 不再对 /iam/classification/** 发生任何网络请求。
const BASE = "/iam/classification"; // reserved for future; not used now

async function searchUsers(_keyword: string) {
    return [];
}

async function refreshUser(id: string) {
    // 返回最小占位，避免网络请求与错误提示
    return {
        id,
        username: id,
        displayName: id,
        orgPath: [],
        roles: [],
        projects: [],
        securityLevel: "内部",
        updatedAt: new Date().toISOString(),
    } as UserClassificationItem;
}

async function getDatasets() {
    // Fallback to catalog datasets and adapt fields
    try {
        const resp: any = await listCatalogDatasets({ page: 0, size: 500 });
        const list: any[] = Array.isArray(resp?.content) ? resp.content : [];
        const toCn = (l?: string): string => classificationToLabelZh(l);
        return list.map((it) => ({
            id: String(it.id),
            name: String(it.name || ""),
            domain: String(it.domainName || it.domainId || ""),
            owner: String(it.owner || ""),
            classification: toCn(it.dataLevel || it.classification),
        })) as DatasetClassificationItem[];
    } catch {
        try {
            return await apiClient.get<DatasetClassificationItem[]>({ url: `${BASE}/datasets` });
        } catch {
            return [];
        }
    }
}

async function getSyncStatus() {
    // 只读占位
    return { lastSyncAt: null, deltaCount: 0, failures: [] } as SyncStatus;
}

async function runSync() {
    return { lastSyncAt: new Date().toISOString(), deltaCount: 0, failures: [] } as SyncStatus;
}

async function retryFailure(_id: string) {
    return { lastSyncAt: new Date().toISOString(), deltaCount: 0, failures: [] } as SyncStatus;
}

export default {
	searchUsers,
	refreshUser,
	getDatasets,
	getSyncStatus,
	runSync,
	retryFailure,
};
