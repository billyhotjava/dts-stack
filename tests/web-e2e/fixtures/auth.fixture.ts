import { test as base, expect } from './base.fixture';
import type { AuthUrls } from '../support/storage-state';
import {
  namedStorageStatePath,
  resolveAuthUrls,
  storageStatePathFor,
  writeNamedStorageStateSync,
} from '../support/storage-state';

type AuthFixtures = {
  authUrls: AuthUrls;
};

const test = base.extend<AuthFixtures>({
  authUrls: async ({}, use) => {
    await use(resolveAuthUrls());
  },
});

export { expect, test, storageStatePathFor };
export { namedStorageStatePath, writeNamedStorageStateSync };
