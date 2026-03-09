import { expect, test, storageStatePathFor } from '../../fixtures/reset.fixture';

test.describe('@seed-smoke platform seed lifecycle', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('seeded ERP state should be visible in expert shell', async ({ page, authUrls, dataFactory, checkpoints }) => {
    await checkpoints.mark('seed:start');
    await dataFactory.resetSuite();
    await dataFactory.seedJourneys(['erp', 'analytics', 'governance']);
    await checkpoints.mark('seed:ready');

    await page.goto(authUrls.expert, { waitUntil: 'domcontentloaded' });

    await expect(page.getByTestId('seed-erp')).toContainText('ready');
    await expect(page.getByTestId('seed-analytics')).toContainText('ready');
    await expect(page.getByTestId('seed-governance')).toContainText('ready');
  });
});

test.describe('@seed-smoke analytics seed lifecycle', () => {
  test.use({ storageState: storageStatePathFor('analytics') });

  test('seeded analytics state should be visible in analytics shell', async ({ page, authUrls, dataFactory, checkpoints }) => {
    await checkpoints.mark('analytics:seed:start');
    await dataFactory.resetSuite();
    await dataFactory.seedJourneys(['analytics']);
    await checkpoints.mark('analytics:seed:ready');

    await page.goto(authUrls.analytics, { waitUntil: 'domcontentloaded' });

    await expect(page.getByTestId('seed-analytics')).toContainText('ready');
  });
});

test.describe('@seed-smoke admin seed lifecycle', () => {
  test.use({ storageState: storageStatePathFor('admin') });

  test('seeded governance state should be visible in admin shell', async ({ page, authUrls, dataFactory, checkpoints }) => {
    await checkpoints.mark('admin:seed:start');
    await dataFactory.resetSuite();
    await dataFactory.seedJourneys(['governance']);
    await checkpoints.mark('admin:seed:ready');

    await page.goto(authUrls.admin, { waitUntil: 'domcontentloaded' });

    await expect(page.getByTestId('seed-governance')).toContainText('ready');
  });
});
