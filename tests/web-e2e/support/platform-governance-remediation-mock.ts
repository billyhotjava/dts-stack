import type { Page } from '@playwright/test';
import { installPlatformWorkbenchMocks } from './platform-workbench-mock';

export async function installPlatformGovernanceRemediationMocks(page: Page): Promise<void> {
  await installPlatformWorkbenchMocks(page, {
    overview: {
      myAssets: 18,
      todayNewAssets: 0,
    },
    todos: [
      {
        type: 'QUALITY',
        title: '客户主数据质量复核',
        status: '待处理',
        createdAt: '2026-03-09T08:40:00Z',
        taskId: 'TASK-GOV-001',
        datasetId: 'dataset-customer-master',
        message: '客户名称缺失率超阈值，需要复核并生成处置记录。',
      },
      {
        type: 'SCHEMA_DRIFT',
        title: 'ERP 销售订单字段漂移提醒',
        status: '已发现',
        createdAt: '2026-03-09T07:55:00Z',
        datasetId: 'dataset-sales-orders',
        message: 'discount_rate 字段类型变更，请评估规则影响。',
      },
    ],
    favorites: [
      {
        id: 'fav-governance-quality',
        title: '质量规则台账',
        targetType: 'TASK',
        targetId: 'quality-governance-board',
        link: '/dashboard/governance/quality-rules',
        sortOrder: 1,
        enabled: true,
      },
      {
        id: 'fav-governance-alert',
        title: '治理异常工单',
        targetType: 'TASK',
        targetId: 'governance-alerts',
        link: '/dashboard/workbench/workflow-center',
        sortOrder: 2,
        enabled: true,
      },
    ],
  });
}
