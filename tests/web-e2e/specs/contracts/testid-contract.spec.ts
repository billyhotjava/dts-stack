import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '../../fixtures/base.fixture';

type Contract = {
  file: string;
  ids: string[];
};

const repoRoot = path.resolve(process.cwd(), '..', '..');

const contracts: Contract[] = [
  {
    file: 'source/dts-platform-webapp/src/pages/workbench/index.tsx',
    ids: [
      'platform-workbench-page',
      'platform-workbench-refresh',
      'platform-workbench-new-favorite',
      'platform-workbench-todos',
      'platform-workbench-favorite-card-',
    ],
  },
  {
    file: 'source/dts-admin-webapp/src/admin/views/infra-settings.tsx',
    ids: [
      'admin-infra-settings-page',
      'admin-infra-settings-tabs',
      'admin-infra-switch-',
      'admin-infra-service-panel-',
      'admin-infra-service-save-',
      'admin-infra-service-test-',
      'admin-infra-service-test-result-',
    ],
  },
  {
    file: 'source/dts-analytics-webapp/modern/src/pages/DashboardsPage.tsx',
    ids: ['analytics-dashboards-page', 'analytics-dashboard-search', 'analytics-dashboard-card-'],
  },
  {
    file: 'source/dts-analytics-webapp/modern/src/pages/DashboardDetailPage.tsx',
    ids: ['analytics-dashboard-detail', 'analytics-dashboard-share'],
  },
];

test('core web apps should expose stable data-testid contracts', async () => {
  for (const contract of contracts) {
    const absolutePath = path.join(repoRoot, contract.file);
    const source = fs.readFileSync(absolutePath, 'utf-8');
    for (const id of contract.ids) {
      expect(
        source.includes(`data-testid="${id}"`) ||
          source.includes(`data-testid='${id}'`) ||
          source.includes(id),
      ).toBeTruthy();
    }
  }
});
