import { ensureStorageStates } from './support/storage-state';

async function globalSetup(): Promise<void> {
  await ensureStorageStates();
}

export default globalSetup;
