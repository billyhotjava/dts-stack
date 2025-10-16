import api from "@/api/apiClient";

// Catalog
export const getCatalogSummary = () => api.get({ url: "/catalog/summary" });
export const getCatalogConfig = () => api.get({ url: "/catalog/config" });
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
export const listDatasetGrants = (datasetId: string) => api.get({ url: `/catalog/datasets/${datasetId}/grants` });
export const createDatasetGrant = (datasetId: string, data: any) =>
	api.post({ url: `/catalog/datasets/${datasetId}/grants`, data });
export const deleteDatasetGrant = (datasetId: string, grantId: string) =>
	api.delete({ url: `/catalog/datasets/${datasetId}/grants/${grantId}` });

export const getDomainTree = () => api.get({ url: "/catalog/domains/tree" });
export const moveDomain = (id: string, data: { newParentId?: string | null }) =>
	api.post({ url: `/catalog/domains/${id}/move`, data });

// Asset extras (tasks)
export const syncDatasetSchema = (datasetId: string, data?: any) =>
    api.post({ url: `/datasets/${datasetId}/sync-schema`, data });
export const previewDataset = (datasetId: string, rows = 50) =>
    api.get({ url: `/datasets/${datasetId}/preview`, params: { rows } });
export const getDatasetJob = (jobId: string) => api.get({ url: `/dataset-jobs/${jobId}` });
export const listDatasetJobs = (datasetId: string) => api.get({ url: `/datasets/${datasetId}/jobs` });

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

// Modeling
export const listStandards = (params: any = {}) => api.get({ url: "/modeling/standards", params });
export const getStandard = (id: string) => api.get({ url: `/modeling/standards/${id}` });
export const createStandard = (data: any) => api.post({ url: "/modeling/standards", data });
export const updateStandard = (id: string, data: any) => api.put({ url: `/modeling/standards/${id}`, data });
export const deleteStandard = (id: string) => api.delete({ url: `/modeling/standards/${id}` });
export const listStandardVersions = (id: string) => api.get({ url: `/modeling/standards/${id}/versions` });
export const listStandardAttachments = (id: string) => api.get({ url: `/modeling/standards/${id}/attachments` });
export const uploadStandardAttachment = (id: string, formData: FormData) =>
    api.post({
        url: `/modeling/standards/${id}/attachments`,
        data: formData,
    });
export const deleteStandardAttachment = (standardId: string, attachmentId: string) =>
	api.delete({ url: `/modeling/standards/${standardId}/attachments/${attachmentId}` });
export const getStandardSettings = () => api.get({ url: "/modeling/standards/settings" });
export const updateStandardSettings = (data: any) => api.put({ url: "/modeling/standards/settings", data });
export const getStandardHealth = () => api.get({ url: "/modeling/standards/health" });

// Governance
export const listQualityRules = () => api.get({ url: "/governance/quality/rules" });
export const createQualityRule = (data: any) => api.post({ url: "/governance/quality/rules", data });
export const updateQualityRule = (id: string, data: any) => api.put({ url: `/governance/quality/rules/${id}`, data });
export const deleteQualityRule = (id: string) => api.delete({ url: `/governance/quality/rules/${id}` });
export const toggleQualityRule = (id: string, enabled: boolean) =>
	api.post({ url: `/governance/quality/rules/${id}/toggle`, data: { enabled } });

export const triggerQualityRun = (data: any) => api.post({ url: "/governance/quality/runs", data });
export const listQualityRuns = (params: any = {}) => api.get({ url: "/governance/quality/runs", params });
export const getQualityRun = (id: string) => api.get({ url: `/governance/quality/runs/${id}` });

export const createComplianceBatch = (data: any) => api.post({ url: "/governance/compliance/batches", data });
export const listComplianceBatches = (params: any = {}) => api.get({ url: "/governance/compliance/batches", params });
export const getComplianceBatch = (id: string) => api.get({ url: `/governance/compliance/batches/${id}` });
export const updateComplianceItem = (id: string, data: any) => api.put({ url: `/governance/compliance/items/${id}`, data });
export const deleteComplianceBatch = (id: string) => api.delete({ url: `/governance/compliance/batches/${id}` });

export const listIssues = () => api.get({ url: "/governance/issues" });
export const createIssue = (data: any) => api.post({ url: "/governance/issues", data });
export const updateIssue = (id: string, data: any) => api.put({ url: `/governance/issues/${id}`, data });
export const closeIssue = (id: string, resolution?: string) =>
	api.post({ url: `/governance/issues/${id}/close`, data: { resolution } });
export const appendIssueAction = (id: string, data: any) => api.post({ url: `/governance/issues/${id}/actions`, data });

// Data quality (compatibility helpers)
export const triggerQuality = (datasetId: string, ruleId?: string) =>
	api.post({ url: "/data-quality-runs/trigger", params: { datasetId, ruleId } });
export const latestQuality = (datasetId: string) => api.get({ url: "/data-quality-runs/latest", params: { datasetId } });

// Explore
export const previewQuery = (data: any) => api.post({ url: "/explore/query/preview", data });
export interface SavedQueryCreatePayload {
  name: string;
  sqlText: string;
  datasetId?: string | null;
}

export interface SavedQueryUpdatePayload {
  name?: string;
  sqlText?: string;
  datasetId?: string | null;
}

export const listSavedQueries = () => api.get({ url: "/explore/saved-queries" });
export const createSavedQuery = (data: SavedQueryCreatePayload) => api.post({ url: "/explore/saved-queries", data });
export const deleteSavedQuery = (id: string) => api.delete({ url: `/explore/saved-queries/${id}` });
export const runSavedQuery = (id: string) => api.post({ url: `/explore/saved-queries/${id}/run` });
export const getSavedQuery = (id: string) => api.get({ url: `/explore/saved-queries/${id}` });
export const updateSavedQuery = (id: string, data: SavedQueryUpdatePayload) =>
  api.put({ url: `/explore/saved-queries/${id}`, data });

// Explore (new APIs)
export const executeExplore = (data: any) => api.post({ url: "/explore/execute", data });
export const explainExplore = (data: any) => api.post({ url: "/explore/explain", data });
export const saveExploreResult = (executionId: string, data?: any) =>
  api.post({ url: `/explore/save-result/${executionId}`, data });
export const previewResultSet = (resultSetId: string) => api.get({ url: `/explore/result-preview/${resultSetId}` });
export const deleteResultSet = (id: string) => api.delete({ url: `/explore/result-sets/${id}` });

// Explore (CRUD for generated entities)
export const listSqlConnections = () => api.get({ url: "/sql-connections" });
export const listQueryWorkspaces = () => api.get({ url: "/query-workspaces" });
export const listQueryExecutions = () => api.get({ url: "/explore/query-executions" });
export const listResultSets = () => api.get({ url: "/explore/result-sets" });
export const cleanupExpiredResultSets = () => api.post({ url: "/explore/result-sets/cleanup" });

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
