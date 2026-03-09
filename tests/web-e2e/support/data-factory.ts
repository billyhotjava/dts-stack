import { getJson, postJson } from './api-client';

export type JourneyName = 'erp' | 'analytics' | 'governance' | 'auth';

export type SeedState = {
  journeys: Record<string, { status: string; seededAt: string | null; count: number }>;
  resetCount: number;
  lastScope: string | null;
};

export class DataFactoryClient {
  async resetSuite(): Promise<SeedState> {
    const payload = await postJson('/api/test-support/reset', { scope: 'suite' });
    return this.unwrapState(payload);
  }

  async resetCase(caseId?: string): Promise<SeedState> {
    const payload = await postJson('/api/test-support/reset', {
      scope: 'case',
      ...(caseId ? { caseId } : {}),
    });
    return this.unwrapState(payload);
  }

  async seedJourney(journey: JourneyName): Promise<SeedState> {
    const payload = await postJson('/api/test-support/seed', { journeys: [journey] });
    return this.unwrapState(payload);
  }

  async seedJourneys(journeys: JourneyName[]): Promise<SeedState> {
    const payload = await postJson('/api/test-support/seed', { journeys });
    return this.unwrapState(payload);
  }

  async snapshot(): Promise<SeedState> {
    const payload = await getJson('/api/test-support/state');
    return this.unwrapState(payload);
  }

  private unwrapState(payload: Record<string, unknown>): SeedState {
    const state = (payload.data ?? payload) as SeedState;
    if (!state || typeof state !== 'object' || !state.journeys) {
      throw new Error('test-support API returned invalid state payload');
    }
    return state;
  }
}
