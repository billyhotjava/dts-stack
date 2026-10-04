import type { Page } from '@playwright/test';
import { installPlatformWorkbenchMocks } from './platform-workbench-mock';

type Scenario = 'approve' | 'cancel';

type MockOptions = {
  scenario: Scenario;
};

export async function installPlatformAiModelingMocks(page: Page, options: MockOptions): Promise<void> {
  if (options.scenario === 'cancel') {
    await installPlatformWorkbenchMocks(page, {
      overview: {
        myAssets: 14,
        todayNewAssets: 1,
      },
      todos: [
        {
          type: 'QUALITY',
          title: '建模方案待确认',
          status: '待沟通',
          createdAt: '2026-03-09T10:20:00Z',
          taskId: 'TASK-MODEL-002',
          message: 'AI 已生成初稿，但尚未提交审批。',
        },
      ],
      favorites: [
        {
          id: 'fav-model-plan',
          title: '销售域建模草稿',
          targetType: 'MODEL',
          targetId: 'sales-plan-draft',
          link: '/dashboard/modeling/dbt-files',
          sortOrder: 1,
          enabled: true,
        },
      ],
    });
    return;
  }

  await installPlatformWorkbenchMocks(page, {
    overview: {
      myAssets: 16,
      todayNewAssets: 2,
    },
    todos: [
      {
        type: 'ACCESS_APPROVAL',
        title: 'AI 建模审批待处理',
        status: '待审批',
        createdAt: '2026-03-09T10:00:00Z',
        taskId: 'TASK-MODEL-001',
        message: '销售域 DWD/DWS 模型待确认发布。',
      },
      {
        type: 'QUALITY',
        title: '模型字段命名复核',
        status: '待处理',
        createdAt: '2026-03-09T09:30:00Z',
        taskId: 'TASK-MODEL-003',
        message: '请确认字段命名与指标口径一致。',
      },
    ],
    favorites: [
      {
        id: 'fav-model-plan',
        title: '销售域模型工作区',
        targetType: 'MODEL',
        targetId: 'sales-domain',
        link: '/dashboard/modeling/dbt-files',
        sortOrder: 1,
        enabled: true,
      },
    ],
  });
}
