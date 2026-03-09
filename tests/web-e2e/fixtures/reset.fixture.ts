import path from 'node:path';
import { test as authTest, expect, storageStatePathFor } from './auth.fixture';
import { CheckpointRecorder } from '../support/checkpoints';
import { DataFactoryClient } from '../support/data-factory';

type ResetFixtures = {
  dataFactory: DataFactoryClient;
  checkpoints: CheckpointRecorder;
};

const test = authTest.extend<ResetFixtures>({
  dataFactory: async ({}, use) => {
    await use(new DataFactoryClient());
  },
  checkpoints: async ({}, use, testInfo) => {
    const recorder = new CheckpointRecorder(
      path.resolve(process.cwd(), 'reports/checkpoints'),
      testInfo.title,
      `${testInfo.file}-${testInfo.title}`,
    );
    await recorder.mark('test:begin');
    try {
      await use(recorder);
      await recorder.mark('test:complete', { status: testInfo.status });
    } catch (error) {
      await recorder.mark('test:error', { message: (error as Error).message });
      throw error;
    }
  },
});

export { expect, test, storageStatePathFor };
