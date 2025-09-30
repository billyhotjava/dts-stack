import api from "@/api/apiClient";

// Catalog
export const getCatalogSummary = () => api.get({ url: "/catalog/summary" });
export const listDomains = (page = 0, size = 10, keyword = "") =>
	api.get({ url: "/catalog/domains", params: { page, size, keyword } });
export const createDomain = (data: any) => api.post({ url: "/catalog/domains", data });
export const updateDomain = (id: string, data: any) => api.put({ url: `/catalog/domains/${id}`, data });
export const deleteDomain = (id: string) => api.delete({ url: `/catalog/domains/${id}` });

export const listDatasets = (params: any = {}) => api.get({ url: "/catalog/datasets", params });
export const getDataset = (id: string) => api.get({ url: `/catalog/datasets/${id}` });
export const createDataset = (data: any) => api.post({ url: "/catalog/datasets", data });
export const updateDataset = (id: string, data: any) => api.put({ url: `/catalog/datasets/${id}`, data });
export const deleteDataset = (id: string) => api.delete({ url: `/catalog/datasets/${id}` });

export const getDomainTree = () => api.get({ url: "/catalog/domains/tree" });
export const moveDomain = (id: string, data: { newParentId?: string | null }) =>
	api.post({ url: `/catalog/domains/${id}/move`, data });

export const getAccessPolicy = (datasetId: string) =>
	api.get({ url: "/catalog/access-policies", params: { datasetId } });
export const upsertAccessPolicy = (datasetId: string, data: any) =>
	api.put({ url: `/catalog/access-policies/${datasetId}`, data });
export const previewSecurityViews = (datasetId: string) =>
    api.get({ url: `/catalog/security-views/${datasetId}/preview` });

// Asset extras (tasks)
export const syncDatasetSchema = (datasetId: string, data?: any) =>
    api.post({ url: `/datasets/${datasetId}/sync-schema`, data });
export const previewDataset = (datasetId: string, rows = 50) =>
    api.get({ url: `/datasets/${datasetId}/preview`, params: { rows } });

export const listMaskingRules = () => api.get({ url: "/catalog/masking-rules" });
export const createMaskingRule = (data: any) => api.post({ url: "/catalog/masking-rules", data });
export const updateMaskingRule = (id: string, data: any) => api.put({ url: `/catalog/masking-rules/${id}`, data });
export const deleteMaskingRule = (id: string) => api.delete({ url: `/catalog/masking-rules/${id}` });
export const previewMasking = (data: any) => api.post({ url: "/catalog/masking-rules/preview", data });

export const getClassificationMapping = () => api.get({ url: "/catalog/classification-mapping" });
export const replaceClassificationMapping = (data: any[]) => api.put({ url: "/catalog/classification-mapping", data });
export const importClassificationMapping = (data: any[]) =>
	api.post({ url: "/catalog/classification-mapping/import", data });
export const exportClassificationMapping = () => api.get({ url: "/catalog/classification-mapping/export" });

// Policy extras (tasks)
export const getEffectivePolicy = (datasetId: string) => api.get({ url: "/policy/effective", params: { datasetId } });
export const applyPolicy = (datasetId: string, data?: any) => api.post({ url: `/policy/${datasetId}/apply`, data });
export const rebuildSecureView = (id: string) => api.post({ url: `/secure-views/${id}/rebuild` });
export const listSecurityViews = (datasetId: string) => api.get({ url: `/catalog/security-views/${datasetId}` });

// Modeling
export const listStandards = () => api.get({ url: "/modeling/standards" });
export const createStandard = (data: any) => api.post({ url: "/modeling/standards", data });
export const updateStandard = (id: string, data: any) => api.put({ url: `/modeling/standards/${id}`, data });
export const deleteStandard = (id: string) => api.delete({ url: `/modeling/standards/${id}` });

// Governance
export const listRules = () => api.get({ url: "/governance/rules" });
export const createRule = (data: any) => api.post({ url: "/governance/rules", data });
export const updateRule = (id: string, data: any) => api.put({ url: `/governance/rules/${id}`, data });
export const deleteRule = (id: string) => api.delete({ url: `/governance/rules/${id}` });
export const toggleRule = (id: string) => api.post({ url: `/governance/rules/${id}/toggle` });

export const listComplianceChecks = () => api.get({ url: "/governance/compliance-checks" });
export const getComplianceCheck = (id: string) => api.get({ url: `/governance/compliance-checks/${id}` });
export const runCompliance = () => api.post({ url: "/governance/compliance-checks/run" });

// Data quality (tasks)
export const triggerQuality = (datasetId: string) => api.post({ url: "/data-quality-runs/trigger", params: { datasetId } });
export const latestQuality = (datasetId: string) => api.get({ url: "/data-quality-runs/latest", params: { datasetId } });

// Explore
export const previewQuery = (data: any) => api.post({ url: "/explore/query/preview", data });
export const listSavedQueries = () => api.get({ url: "/explore/saved-queries" });
export const createSavedQuery = (data: any) => api.post({ url: "/explore/saved-queries", data });
export const deleteSavedQuery = (id: string) => api.delete({ url: `/explore/saved-queries/${id}` });
export const runSavedQuery = (id: string) => api.post({ url: `/explore/saved-queries/${id}/run` });
export const getSavedQuery = (id: string) => api.get({ url: `/explore/saved-queries/${id}` });

// Explore (new APIs)
export const executeExplore = (data: any) => api.post({ url: "/explore/execute", data });
export const explainExplore = (data: any) => api.post({ url: "/explore/explain", data });
export const saveExploreResult = (executionId: string, data?: any) =>
  api.post({ url: `/explore/save-result/${executionId}`, data });
export const previewResultSet = (resultSetId: string, rows = 100) =>
  api.get({ url: `/explore/result-preview/${resultSetId}`, params: { rows } });
export const deleteResultSet = (id: string) => api.delete({ url: `/explore/result-sets/${id}` });

// Explore (CRUD for generated entities)
export const listSqlConnections = () => api.get({ url: "/sql-connections" });
export const listQueryWorkspaces = () => api.get({ url: "/query-workspaces" });
export const listQueryExecutions = () => api.get({ url: "/query-executions" });
export const listResultSets = () => api.get({ url: "/result-sets" });
export const cleanupExpiredResultSets = () => api.post({ url: "/result-sets/cleanup" });

// Catalog tables & columns
export const listTablesByDataset = (datasetId: string, keyword?: string) =>
  api.get({ url: "/catalog/tables", params: { datasetId, keyword } });
export const listColumnsByTable = (tableId: string, keyword?: string) =>
  api.get({ url: "/catalog/columns", params: { tableId, keyword } });

// Visualization
export const getCockpitMetrics = () => api.get({ url: "/vis/cockpit/metrics" });
export const getProjectsSummary = () => api.get({ url: "/vis/projects/summary" });
export const getFinanceSummary = () => api.get({ url: "/vis/finance/summary" });
export const getSupplySummary = () => api.get({ url: "/vis/supply/summary" });
export const getHrSummary = () => api.get({ url: "/vis/hr/summary" });

// Services
export const listMyTokens = () => api.get({ url: "/tokens/me" });
export const createToken = () => api.post({ url: "/tokens" });
export const deleteToken = (id: string) => api.delete({ url: `/tokens/${id}` });

// IAM
export const listClassifications = () => api.get({ url: "/iam/classifications" });
export const createClassification = (data: any) => api.post({ url: "/iam/classifications", data });
export const updateClassification = (id: string, data: any) => api.put({ url: `/iam/classifications/${id}`, data });
export const deleteClassification = (id: string) => api.delete({ url: `/iam/classifications/${id}` });

export const listPermissions = () => api.get({ url: "/iam/permissions" });
export const createPermission = (data: any) => api.post({ url: "/iam/permissions", data });
export const updatePermission = (id: string, data: any) => api.put({ url: `/iam/permissions/${id}`, data });
export const deletePermission = (id: string) => api.delete({ url: `/iam/permissions/${id}` });

export const listRequests = () => api.get({ url: "/iam/requests" });
export const createRequest = (data: any) => api.post({ url: "/iam/requests", data });
export const approveRequest = (id: string) => api.post({ url: `/iam/requests/${id}/approve` });
export const rejectRequest = (id: string) => api.post({ url: `/iam/requests/${id}/reject` });
export const simulateIam = (data: any) => api.post({ url: "/iam/simulate", data });

// API Gateway (tasks)
export const apiTest = (id: string, data?: any) => api.post({ url: `/apis/${id}/test`, data });
export const apiPublish = (id: string, data?: any) => api.post({ url: `/apis/${id}/publish`, data });
export const apiExecute = (id: string, data?: any) => api.post({ url: `/apis/${id}/execute`, data });

// Dashboards & Dev registry (tasks)
export const listDashboards = () => api.get({ url: "/dashboards" });
export const submitEtlJob = (jobId: string) => api.post({ url: `/etl-jobs/${jobId}/submit` });
export const getJobRunStatus = (runId: string) => api.get({ url: `/job-runs/${runId}/status` });
