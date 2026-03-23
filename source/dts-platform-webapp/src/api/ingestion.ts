import api from './apiClient';

export interface IngestionTaskDTO {
  id?: number;
  name: string;
  description?: string;
  sourceType: string;
  sourceConfig: Record<string, any>;
  sourceDataSourceId?: string;
  destinationType?: string;
  destinationConfig?: Record<string, any>;
  syncMode: string;
  syncSchedule?: string;
  syncPrefix?: string;
  syncConfig?: Record<string, any>;
  tableMapping?: Array<{ source: string; target: string }>;
  addaxJobPath?: string;
  addaxConfig?: Record<string, any>;
  airflowEnabled?: boolean;
  airflowDagId?: string;
  dbtModelSelector?: string;
  dbtDagSelector?: string;
  status?: string;
  lastExecutedAt?: string;
  lastExecutionStatus?: string;
  createdBy?: string;
  createdDate?: string;
  lastModifiedBy?: string;
  lastModifiedDate?: string;
}

export interface IngestionExecutionDTO {
  id: number;
  taskId: number;
  taskName?: string;
  executionId?: string;
  status: string;
  startTime?: string;
  endTime?: string;
  rowsRead?: number;
  rowsWritten?: number;
  errorMessage?: string;
  failureCategory?: string;
  failureAdvice?: string;
  logPath?: string;
  replaceMode?: string;
  triggerMode?: "MANUAL" | "FAILED_ONLY" | "FULL_RERUN" | string;
  droppedTables?: string;
  queueWaitSeconds?: number;
  createdAt?: string;
}

export interface IngestionExecutionLog {
  taskId?: number;
  executionId?: number;
  dagId?: string;
  dagRunId?: string;
  taskInstanceId?: string;
  tryNumber?: number;
  scope?: "single" | "all" | string;
  keyword?: string;
  taskStates?: Record<string, string>;
  failureCategory?: string;
  failureAdvice?: string;
  log?: string;
  message?: string;
}

export interface IngestionExecutionObservabilityFailureTopItem {
  category: string;
  count: number;
}

export interface IngestionExecutionObservabilityTrendItem {
  day: string;
  total: number;
  success: number;
  failed: number;
  timeout: number;
}

export interface IngestionExecutionObservabilityDTO {
  taskId?: number;
  sourceType?: string;
  sourceDataSourceId?: string;
  windowStart?: string;
  windowEnd?: string;
  windowDays?: number;
  timeoutMinutes?: number;
  total: number;
  success: number;
  failed: number;
  running: number;
  terminal: number;
  timeout: number;
  successRate?: number;
  timeoutRate?: number;
  avgDurationSeconds?: number;
  mttrSeconds?: number;
  failureTop: IngestionExecutionObservabilityFailureTopItem[];
  trend: IngestionExecutionObservabilityTrendItem[];
}

export interface IngestionIncrementalStateDTO {
  id: number;
  taskId: number;
  sourceTable: string;
  lastSuccessWatermark?: string;
  lastRunId?: string;
  updatedAt?: string;
  createdAt?: string;
}

export interface IngestionIncrementalAuditSummaryDTO {
  total: number;
  advanced: number;
  unchanged: number;
  advancedRate: number;
}

export interface IngestionIncrementalAuditDTO {
  id: number;
  taskId: number;
  executionId?: number;
  executionRunId?: string;
  sourceTable: string;
  incrementalColumn?: string;
  beforeWatermark?: string;
  afterWatermark?: string;
  advanced?: boolean;
  createdAt?: string;
}

export interface AsyncExecutionSubmitResult {
  taskId: number;
  taskName?: string;
  status: string;
  async?: boolean;
  message?: string;
  pollIntervalMs?: number;
}

export interface ColumnInfo {
  name: string;
  jdbcType?: number;
  typeName?: string;
  columnSize?: number;
  decimalDigits?: number;
}

export interface TableInfo {
  schema?: string;
  name: string;
  type?: string;
  columns?: ColumnInfo[];
}

export interface TableDiscoveryFilter {
  schema?: string;
  tablePattern?: string;
  limit?: number;
  includeColumns?: boolean;
}

export interface TableDiscoveryRequest {
  source: {
    dataSourceId: string;
  };
  filter?: TableDiscoveryFilter;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface IngestionChangeLogDTO {
  id?: number;
  taskId: number;
  taskName?: string;
  objType?: string;
  changeType: string;
  summary: string;
  detail?: string;
  riskLevel?: string;
  status?: string;
  assignee?: string;
  approvalComment?: string;
  handledAt?: string;
  handledBy?: string;
  createdBy?: string;
  createdDate?: string;
}

export interface FileUploadResult {
  hostPath: string;
  containerPath: string;
  fileType: string;
  columns: Array<{ name: string; type: string; label?: string; length?: number; precision?: number; scale?: number }>;
  originalName: string;
  fileId?: string;
  batchCode?: string;
  sheetName?: string;
  sheetIndex?: number;
  csvPath?: string;
  csvContainerPath?: string;
  errorPath?: string;
  errorContainerPath?: string;
  delimiter?: string;
  preview?: string[][];
  rowCount?: number;
  errorCount?: number;
  sourceFileType?: string;
  sheets?: Array<{ index: number; name: string }>;
}

export interface DefaultDestinationStatus {
  available: boolean;
  writerTypeReady: boolean;
  writerConfigReady: boolean;
  destinationName?: string;
  writerType?: string;
  message?: string;
}

export interface IngestionConnectorCapabilityDTO {
  connectorType: string;
  capabilities: string[];
  connectorVersion?: string;
  constraints?: Record<string, any>;
  enabled?: boolean;
  updatedAt?: string;
}

export interface IngestionTaskTemplateDTO {
  id: string;
  name: string;
  description?: string;
  sourceCategory?: "database" | "file" | string;
  connectorType?: string;
  defaults?: Record<string, any>;
  requiredParams?: string[];
  warnings?: string[];
  version?: string;
}

export interface IngestionTemplateRenderDTO {
  id: string;
  name: string;
  version?: string;
  renderedDefaults?: Record<string, any>;
  requiredParams?: string[];
  warnings?: string[];
  errors?: string[];
  canApply?: boolean;
}

export interface IngestionGovernanceSourceLoadItem {
  sourceDataSourceId?: string;
  sourceType?: string;
  running: number;
  preparing: number;
}

export interface IngestionGovernanceProjectLoadItem {
  projectKey: string;
  running: number;
  preparing: number;
}

export interface IngestionGovernanceOverviewDTO {
  generatedAt?: string;
  running: number;
  preparing: number;
  queueLength: number;
  blockedByPolicy: number;
  avgExecutionSeconds?: number;
  avgQueueWaitSeconds?: number;
  maxQueueWaitSeconds?: number;
  sourceLoads: IngestionGovernanceSourceLoadItem[];
  projectLoads: IngestionGovernanceProjectLoadItem[];
}

export interface IngestionRealtimeStatusDTO {
  taskId: number;
  connectorType: string;
  status: string;
  topicName?: string;
  consumerGroup?: string;
  checkpointToken?: string;
  lagMs?: number;
  throughputRps?: number;
  backlogCount?: number;
  lastHeartbeat?: string;
  updatedAt?: string;
}

const DEFAULT_EXECUTION_POLL_INTERVAL_MS = (() => {
  const raw = Number((import.meta as any)?.env?.VITE_INGESTION_EXECUTION_POLL_MS ?? 3000);
  if (!Number.isFinite(raw)) return 3000;
  return Math.min(30000, Math.max(1000, Math.floor(raw)));
})();

export const resolveExecutionPollIntervalMs = (hint?: number): number => {
  const picked = Number(hint);
  if (!Number.isFinite(picked)) {
    return DEFAULT_EXECUTION_POLL_INTERVAL_MS;
  }
  return Math.min(30000, Math.max(1000, Math.floor(picked)));
};

/**
 * 数据入湖任务API
 */
class IngestionTaskAPI {
  /**
   * 创建入湖任务
   */
  async createTask(data: IngestionTaskDTO): Promise<IngestionTaskDTO> {
    return api.post({ url: '/ingestion/tasks', data });
  }

  /**
   * 获取任务列表
   */
  async getTasks(params?: {
    status?: string;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PageResult<IngestionTaskDTO>> {
    const payload: any = await api.get({ url: '/ingestion/tasks/list', params });
    if (payload && typeof payload === "object" && "status" in payload && "data" in payload) {
      return payload.data as PageResult<IngestionTaskDTO>;
    }
    return payload as PageResult<IngestionTaskDTO>;
  }

  /**
   * 获取任务详情
   */
  async getTask(id: number): Promise<IngestionTaskDTO> {
    return api.get({ url: `/ingestion/tasks/${id}` });
  }

  /**
   * 获取默认数据湖写入器状态
   */
  async getDefaultDestinationStatus(): Promise<DefaultDestinationStatus> {
    return api.get({ url: "/ingestion/default-destination" });
  }

  /**
   * 更新任务
   */
  async updateTask(id: number, data: IngestionTaskDTO): Promise<IngestionTaskDTO> {
    return api.put({ url: `/ingestion/tasks/${id}`, data });
  }

  /**
   * 删除任务（软删除）
   */
  async deleteTask(id: number): Promise<void> {
    return api.delete({ url: `/ingestion/tasks/${id}` });
  }

  /**
   * 执行任务
   */
  async executeTask(id: number): Promise<IngestionExecutionDTO> {
    return api.post({ url: `/ingestion/tasks/${id}/execute` });
  }

  async executeTaskAsync(id: number): Promise<AsyncExecutionSubmitResult> {
    return api.post({ url: `/ingestion/tasks/${id}/execute/async` });
  }

  /**
   * 强制重建 DAG
   */
  async rebuildDag(id: number): Promise<IngestionTaskDTO> {
    return api.post({ url: `/ingestion/tasks/${id}/dag/rebuild` });
  }

  /**
   * 获取任务执行历史
   */
  async getExecutions(
    taskId: number,
    params?: {
      page?: number;
      size?: number;
      sort?: string;
      status?: string;
      failureCategory?: string;
    }
  ): Promise<PageResult<IngestionExecutionDTO>> {
    return api.get({ url: `/ingestion/tasks/${taskId}/executions`, params });
  }

  /**
   * 获取最新执行记录
   */
  async getLatestExecution(taskId: number): Promise<IngestionExecutionDTO | null> {
    try {
      return await api.get({ url: `/ingestion/tasks/${taskId}/executions/latest` });
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * 获取执行日志
   */
  async getExecutionLog(
    taskId: number,
    executionId: number,
    params?: { tryNumber?: number; keyword?: string; scope?: "single" | "all" }
  ): Promise<IngestionExecutionLog> {
    return api.get({ url: `/ingestion/tasks/${taskId}/executions/${executionId}/logs`, params });
  }

  async getExecutionsObservability(params?: {
    taskId?: number;
    sourceType?: string;
    sourceDataSourceId?: string;
    from?: string;
    to?: string;
    days?: number;
    timeoutMinutes?: number;
  }): Promise<IngestionExecutionObservabilityDTO> {
    return api.get({ url: "/ingestion/tasks/executions/observability", params });
  }

  async getGovernanceOverview(params?: { hours?: number }): Promise<IngestionGovernanceOverviewDTO> {
    return api.get({ url: "/ingestion/tasks/executions/governance-overview", params });
  }

  async retryExecution(
    taskId: number,
    executionId: number,
    params?: { mode?: "FAILED_ONLY" | "FULL_RERUN" }
  ): Promise<IngestionExecutionDTO> {
    return api.post({ url: `/ingestion/tasks/${taskId}/executions/${executionId}/retry`, params });
  }

  async retryExecutionAsync(
    taskId: number,
    executionId: number,
    params?: { mode?: "FAILED_ONLY" | "FULL_RERUN" }
  ): Promise<any> {
    return api.post({ url: `/ingestion/tasks/${taskId}/executions/${executionId}/retry/async`, params });
  }

  async getIncrementalStates(taskId: number): Promise<IngestionIncrementalStateDTO[]> {
    return api.get({ url: `/ingestion/tasks/${taskId}/incremental-states` });
  }

  async getIncrementalAudits(
    taskId: number,
    params?: { executionId?: number }
  ): Promise<IngestionIncrementalAuditDTO[]> {
    return api.get({ url: `/ingestion/tasks/${taskId}/incremental-audits`, params });
  }

  async getIncrementalAuditsPage(
    taskId: number,
    params?: {
      executionId?: number;
      executionIds?: number[];
      from?: string;
      to?: string;
      tableName?: string;
      status?: "advanced" | "unchanged";
      page?: number;
      size?: number;
      sort?: string;
    }
  ): Promise<PageResult<IngestionIncrementalAuditDTO>> {
    return api.get({ url: `/ingestion/tasks/${taskId}/incremental-audits/page`, params });
  }

  async getIncrementalAuditsSummary(
    taskId: number,
    params?: {
      executionId?: number;
      executionIds?: number[];
      from?: string;
      to?: string;
      tableName?: string;
      status?: "advanced" | "unchanged";
    }
  ): Promise<IngestionIncrementalAuditSummaryDTO> {
    return api.get({ url: `/ingestion/tasks/${taskId}/incremental-audits/summary`, params });
  }

  /**
   * 上传 Excel/CSV 文件
   */
  async uploadFile(file: File): Promise<FileUploadResult> {
    const formData = new FormData();
    formData.append("file", file);
    return api.post({
      url: "/ingestion/files/upload",
      data: formData,
      headers: { "Content-Type": "multipart/form-data" },
    });
  }

  async getConnectorCapabilities(): Promise<IngestionConnectorCapabilityDTO[]> {
    const payload: any = await api.get({ url: "/ingestion/connectors/capabilities" });
    if (Array.isArray(payload)) return payload as IngestionConnectorCapabilityDTO[];
    if (payload && typeof payload === "object" && Array.isArray((payload as any).data)) {
      return (payload as any).data as IngestionConnectorCapabilityDTO[];
    }
    return [];
  }

  async getTaskTemplates(): Promise<IngestionTaskTemplateDTO[]> {
    const payload: any = await api.get({ url: "/ingestion/templates" });
    if (Array.isArray(payload)) return payload as IngestionTaskTemplateDTO[];
    if (payload && typeof payload === "object" && Array.isArray((payload as any).data)) {
      return (payload as any).data as IngestionTaskTemplateDTO[];
    }
    return [];
  }

  async renderTaskTemplate(
    templateId: string,
    data?: { params?: Record<string, any>; strictRequired?: boolean }
  ): Promise<IngestionTemplateRenderDTO | null> {
    if (!templateId) return null;
    const payload: any = await api.post({
      url: `/ingestion/templates/${encodeURIComponent(templateId)}/render`,
      data,
    });
    if (!payload) return null;
    if (payload && typeof payload === "object" && "renderedDefaults" in payload) {
      return payload as IngestionTemplateRenderDTO;
    }
    if (payload && typeof payload === "object" && (payload as any).data) {
      return (payload as any).data as IngestionTemplateRenderDTO;
    }
    return null;
  }

  async getConnectorCapability(connectorType: string): Promise<IngestionConnectorCapabilityDTO | null> {
    try {
      const payload: any = await api.get({ url: `/ingestion/connectors/capabilities/${connectorType}` });
      if (!payload) return null;
      if (payload && typeof payload === "object" && "connectorType" in payload) {
        return payload as IngestionConnectorCapabilityDTO;
      }
      if (payload && typeof payload === "object" && (payload as any).data) {
        return (payload as any).data as IngestionConnectorCapabilityDTO;
      }
      return null;
    } catch (error: any) {
      if (error?.response?.status === 404) return null;
      throw error;
    }
  }

  async getRealtimeStatus(taskId: number): Promise<IngestionRealtimeStatusDTO | null> {
    try {
      const payload: any = await api.get({ url: `/ingestion/tasks/${taskId}/realtime-status` });
      if (!payload) return null;
      if (payload && typeof payload === "object" && "taskId" in payload) {
        return payload as IngestionRealtimeStatusDTO;
      }
      if (payload && typeof payload === "object" && (payload as any).data) {
        return (payload as any).data as IngestionRealtimeStatusDTO;
      }
      return null;
    } catch (error: any) {
      if (error?.response?.status === 404) return null;
      throw error;
    }
  }

  /**
   * 源端表发现
   */
  async discoverTables(data: TableDiscoveryRequest): Promise<TableInfo[]> {
    const payload: any = await api.post({ url: "/ingestion/metadata/tables", data });
    if (Array.isArray(payload)) {
      return payload as TableInfo[];
    }
    if (payload && typeof payload === "object") {
      const inner = (payload as any).data;
      const status = (payload as any).status;
      const message = (payload as any).message;
      if (status && status !== 200 && status !== "200") {
        throw new Error(message || "获取表清单失败");
      }
      if (Array.isArray(inner)) {
        return inner as TableInfo[];
      }
    }
    return [];
  }

  /**
   * 获取接入变更记录
   */
  async getChangeLogs(params?: {
    taskId?: number;
    objType?: string;
    changeType?: string;
    status?: string;
    assignee?: string;
    keyword?: string;
    page?: number;
    size?: number;
    sort?: string;
  }): Promise<PageResult<IngestionChangeLogDTO>> {
    return api.get({ url: "/ingestion/tasks/changes", params });
  }

  /**
   * 登记接入变更
   */
  async createChangeLog(data: IngestionChangeLogDTO): Promise<IngestionChangeLogDTO> {
    return api.post({ url: "/ingestion/tasks/changes", data });
  }

  async transitionChangeLog(
    id: number,
    data: { action: "SUBMIT" | "APPROVE" | "REJECT"; assignee?: string; approvalComment?: string }
  ): Promise<IngestionChangeLogDTO> {
    return api.post({ url: `/ingestion/tasks/changes/${id}/transition`, data });
  }
}

export const ingestionTaskAPI = new IngestionTaskAPI();
