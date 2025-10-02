import api from "@/api/apiClient";
import type { HiveConnectionTestRequest, HiveConnectionTestResult } from "#/infra";

export const listInfraDataSources = () => api.get({ url: "/infra/data-sources" });

export const testHiveConnection = (data: HiveConnectionTestRequest) =>
	api.post<HiveConnectionTestResult>({ url: "/infra/data-sources/test-connection", data });
