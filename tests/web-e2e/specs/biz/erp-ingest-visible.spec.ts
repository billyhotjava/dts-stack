import { expect, test, storageStatePathFor } from '../../fixtures/reset.fixture';
import { DatasourcePage } from '../../pages/DatasourcePage';
import { IngestionPage } from '../../pages/IngestionPage';
import { installPlatformErpIngestionMocks } from '../../support/platform-erp-ingestion-mock';

test.describe('erp ingest visible journey', () => {
  test.use({ storageState: storageStatePathFor('platform') });

  test('should create erp datasource, trigger metadata sync and show erp tables', async ({
    page,
    authUrls,
    dataFactory,
    checkpoints,
  }) => {
    await installPlatformErpIngestionMocks(page);
    await checkpoints.mark('erp-ingest:start');
    await dataFactory.resetSuite();

    const datasourcePage = new DatasourcePage(page);
    await datasourcePage.goto(authUrls.expert);
    await datasourcePage.createJdbcDatasource({
      name: 'ERP Demo DM',
      typeLabel: '达梦 DM',
      jdbcUrl: 'jdbc:dm://10.20.0.4:5236',
      username: 'ERPDEMO',
      password: 'Devops123@',
    });
    await datasourcePage.expectRowVisible('ERP Demo DM');
    await checkpoints.mark('erp-ingest:datasource-ready');

    const ingestionPage = new IngestionPage(page);
    await ingestionPage.goto(authUrls.expert);
    await ingestionPage.selectPipeline('ERP Demo DM');
    await ingestionPage.triggerSync();
    await ingestionPage.expectDiscoveredTable('ERPDMO.CUSTOMER');
    await ingestionPage.expectColumnVisible('CUSTOMER_ID');
    await checkpoints.mark('erp-ingest:metadata-visible');
  });
});
