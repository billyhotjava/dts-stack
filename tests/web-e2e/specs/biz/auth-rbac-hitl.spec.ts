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

    test('should limit viewer to read-only workbench shortcuts', async ({ page, authUrls: urls }) => {
      await installPlatformAuthRbacHitlMocks(page);

      const workbench = new PlatformAiAssistantPage(page);
      await workbench.goto(urls.expert);
      await workbench.expectTodo('仅可查看个人待办');
      await workbench.expectFavoriteVisible('个人工作台');
      await expect(page.getByText('高风险补数审批')).toHaveCount(0);
    });
  });

  test.describe('opadmin role', () => {
    test.use({ storageState: opadminStatePath });

    test('should expose approval shortcut for opadmin and open workflow center', async ({ page, authUrls: urls }) => {
      await installPlatformAuthRbacHitlMocks(page);

      const workbench = new PlatformAiAssistantPage(page);
      await workbench.goto(urls.expert);
      await workbench.expectTodo('高风险补数审批');
      await workbench.expectFavoriteVisible('审批工作台');

      await workbench.openFavorite('审批工作台');
      await expect(page).toHaveURL(/\/dashboard\/workbench\/workflow-center/);
    });
  });
});
