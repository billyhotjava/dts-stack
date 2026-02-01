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
  logPath?: string;
  createdAt?: string;
}

export interface IngestionExecutionLog {
  taskId?: number;
  executionId?: number;
  dagId?: string;
  dagRunId?: string;
  taskInstanceId?: string;
  tryNumber?: number;
  log?: string;
  message?: string;
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
  createdBy?: string;
  createdDate?: string;
}

export interface FileUploadResult {
  hostPath: string;
  containerPath: string;
  fileType: string;
  columns: Array<{ name: string; type: string }>;
  originalName: string;
}

export interface DefaultDestinationStatus {
  available: boolean;
  writerTypeReady: boolean;
  writerConfigReady: boolean;
  destinationName?: string;
  writerType?: string;
  message?: string;
}

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
    params?: { tryNumber?: number }
  ): Promise<IngestionExecutionLog> {
    return api.get({ url: `/ingestion/tasks/${taskId}/executions/${executionId}/logs`, params });
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
}

export const ingestionTaskAPI = new IngestionTaskAPI();
