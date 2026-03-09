import fs from 'node:fs/promises';
import path from 'node:path';

export type CheckpointEntry = {
  step: string;
  at: string;
  detail?: Record<string, unknown>;
};

type CheckpointFile = {
  testId: string;
  title: string;
  entries: CheckpointEntry[];
};

function sanitizeSegment(value: string): string {
  return value.replace(/[^a-zA-Z0-9._-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 160) || 'checkpoint';
}

export class CheckpointRecorder {
  private readonly reportDir: string;
  private readonly filePath: string;
  private readonly title: string;
  private readonly testId: string;

  constructor(reportDir: string, title: string, testId: string) {
    this.reportDir = reportDir;
    this.title = title;
    this.testId = testId;
    this.filePath = path.join(this.reportDir, `${sanitizeSegment(testId)}.json`);
  }

  async mark(step: string, detail?: Record<string, unknown>): Promise<void> {
    await fs.mkdir(this.reportDir, { recursive: true });
    const payload = await this.load();
    payload.entries.push({
      step,
      at: new Date().toISOString(),
      ...(detail ? { detail } : {}),
    });
    await fs.writeFile(this.filePath, JSON.stringify(payload, null, 2), 'utf-8');
  }

  path(): string {
    return this.filePath;
  }

  private async load(): Promise<CheckpointFile> {
    try {
      const raw = await fs.readFile(this.filePath, 'utf-8');
      const parsed = JSON.parse(raw) as CheckpointFile;
      if (Array.isArray(parsed.entries)) {
        return parsed;
      }
    } catch {}
    return {
      testId: this.testId,
      title: this.title,
      entries: [],
    };
  }
}
