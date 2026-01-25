import { axiosInstance } from './base';

export interface IngestionTaskDTO {
  id?: number;
  name: string;
  description?: string;
  sourceType: string;
  sourceConfig: Record<string, any>;
  destinationType?: string;
  destinationConfig?: Record<string, any>;
  syncMode: string;
  syncSchedule?: string;
  tableMapping?: Array<{ source: string; target: string }>;
  addaxJobPath?: string;
  addaxConfig?: Record<string, any>;
  airflowEnabled?: boolean;
  airflowDagId?: string;
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

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

/**
 * 数据入湖任务API
 */
class IngestionTaskAPI {
  /**
   * 创建入湖任务
   */
  async createTask(data: IngestionTaskDTO): Promise<IngestionTaskDTO> {
    const response = await axiosInstance.post('/api/ingestion/tasks', data);
    return response.data;
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
    const response = await axiosInstance.get('/api/ingestion/tasks', { params });
    return response.data;
  }

  /**
   * 获取任务详情
   */
  async getTask(id: number): Promise<IngestionTaskDTO> {
    const response = await axiosInstance.get(`/api/ingestion/tasks/${id}`);
    return response.data;
  }

  /**
   * 更新任务
   */
  async updateTask(id: number, data: IngestionTaskDTO): Promise<IngestionTaskDTO> {
    const response = await axiosInstance.put(`/api/ingestion/tasks/${id}`, data);
    return response.data;
  }

  /**
   * 删除任务（软删除）
   */
  async deleteTask(id: number): Promise<void> {
    await axiosInstance.delete(`/api/ingestion/tasks/${id}`);
  }

  /**
   * 执行任务
   */
  async executeTask(id: number): Promise<IngestionExecutionDTO> {
    const response = await axiosInstance.post(`/api/ingestion/tasks/${id}/execute`);
    return response.data;
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
    const response = await axiosInstance.get(`/api/ingestion/tasks/${taskId}/executions`, { params });
    return response.data;
  }

  /**
   * 获取最新执行记录
   */
  async getLatestExecution(taskId: number): Promise<IngestionExecutionDTO | null> {
    try {
      const response = await axiosInstance.get(`/api/ingestion/tasks/${taskId}/executions/latest`);
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }
}

export const ingestionTaskAPI = new IngestionTaskAPI();
