import { expect, namedStorageStatePath, test, writeNamedStorageStateSync } from '../../fixtures/auth.fixture';
import { PlatformAiAssistantPage } from '../../pages/PlatformAiAssistantPage';
import { resolveAuthUrls } from '../../support/storage-state';
import { installPlatformAuthRbacHitlMocks } from '../../support/platform-auth-rbac-hitl-mock';

const viewerStatePath = namedStorageStatePath('platform-viewer-rbac');
const opadminStatePath = namedStorageStatePath('platform-opadmin-rbac');
const authUrls = resolveAuthUrls();

writeNamedStorageStateSync(
  'platform-viewer-rbac',
  'platform',
  {
    username: 'viewer',
    accessToken: 'e2e-platform-viewer-token',
    refreshToken: 'e2e-platform-viewer-token-refresh',
    userInfo: {
      fullName: '普通用户',
      roles: ['ROLE_USER'],
      permissions: ['portal.view'],
      enabled: true,
    },
  },
  authUrls,
);

writeNamedStorageStateSync(
  'platform-opadmin-rbac',
  'platform',
  {
    username: 'opadmin',
    accessToken: 'e2e-platform-opadmin-token',
    refreshToken: 'e2e-platform-opadmin-token-refresh',
    userInfo: {
      fullName: '业务运维管理员',
      roles: ['ROLE_USER', 'ROLE_OP_ADMIN'],
      permissions: ['portal.view', 'portal.manage', 'catalog.manage', 'governance.manage', 'iam.manage'],
      enabled: true,
    },
  },
  authUrls,
);

test.describe('auth rbac and hitl business loop', () => {
  test.describe('viewer role', () => {
    test.use({ storageState: viewerStatePath });

    test('should hide privileged nav and AI entry for viewer', async ({ page, authUrls: urls }) => {
      await installPlatformAuthRbacHitlMocks(page);

      await page.goto(new URL('dashboard/workbench', urls.expert).toString(), { waitUntil: 'domcontentloaded' });

      await expect(page.getByText('任务成功率')).toBeVisible();
      await expect(page.getByText('合规完成率')).toHaveCount(0);
      await expect(page.getByText('资产目录')).toHaveCount(0);
      await expect(page.getByTestId('platform-ai-chat-open')).toHaveCount(0);
    });
  });

  test.describe('opadmin role', () => {
    test.use({ storageState: opadminStatePath });

    test('should show privileged nav and finish approval loop for opadmin', async ({ page, authUrls: urls }) => {
      await installPlatformAuthRbacHitlMocks(page);

      const assistant = new PlatformAiAssistantPage(page);
      await assistant.goto(urls.expert);

      await expect(page.getByText('合规完成率')).toBeVisible();
      await expect(page.getByText('任务成功率')).toHaveCount(0);
      await expect(page.getByText('资产目录')).toBeVisible();

      await assistant.open();
      await assistant.startNewSession();
      await assistant.sendPrompt('请帮我处理销售异常补数');
      await assistant.expectApprovalCard();
      await assistant.approve();
      await assistant.expectApprovalClosed();
      await assistant.expectAssistantMessage('已进入执行队列');
      await assistant.expectToolTraceResult('run_sql', 'RUNNING');
    });
  });
});
