import { isRetryableHttpError } from '../../../api/analyticsApi';

type QueueTask<T> = {
    run: () => Promise<T>;
    resolve: (value: T) => void;
    reject: (reason?: unknown) => void;
    timeoutMs: number;
};

type ScheduleOptions = {
    timeoutMs?: number;
};

type RetryOptions = {
    maxRetries?: number;
    baseDelayMs?: number;
};

const DEFAULT_MAX_CONCURRENCY = 8;
const DEFAULT_TIMEOUT_MS = 30000;
const DEFAULT_BASE_DELAY_MS = 350;

let maxConcurrency = DEFAULT_MAX_CONCURRENCY;
let activeCount = 0;
const queue: Array<QueueTask<unknown>> = [];

function runNext(): void {
    while (activeCount < maxConcurrency && queue.length > 0) {
        const task = queue.shift();
        if (!task) {
            continue;
        }
        activeCount += 1;

        let settled = false;
        const timer = window.setTimeout(() => {
            if (settled) {
                return;
            }
            settled = true;
            activeCount = Math.max(0, activeCount - 1);
            task.reject(new Error('查询超时，请稍后重试'));
            runNext();
        }, task.timeoutMs);

        task.run()
            .then((value) => {
                if (settled) {
                    return;
                }
                settled = true;
                window.clearTimeout(timer);
                activeCount = Math.max(0, activeCount - 1);
                task.resolve(value);
                runNext();
            })
            .catch((error) => {
                if (settled) {
                    return;
                }
                settled = true;
                window.clearTimeout(timer);
                activeCount = Math.max(0, activeCount - 1);
                task.reject(error);
                runNext();
            });
    }
}

export function configureQueryScheduler(concurrency: number): void {
    if (!Number.isFinite(concurrency)) {
        return;
    }
    maxConcurrency = Math.max(1, Math.min(32, Math.round(concurrency)));
    runNext();
}

export function scheduleQueryTask<T>(run: () => Promise<T>, options?: ScheduleOptions): Promise<T> {
    const timeoutMs = Number(options?.timeoutMs ?? DEFAULT_TIMEOUT_MS);
    const normalizedTimeout = Number.isFinite(timeoutMs) && timeoutMs > 0
        ? Math.min(Math.max(timeoutMs, 1000), 300000)
        : DEFAULT_TIMEOUT_MS;
    return new Promise<T>((resolve, reject) => {
        queue.push({
            run: run as () => Promise<unknown>,
            resolve: resolve as (value: unknown) => void,
            reject,
            timeoutMs: normalizedTimeout,
        });
        runNext();
    });
}

function delay(ms: number): Promise<void> {
    return new Promise((resolve) => {
        window.setTimeout(resolve, Math.max(0, Math.floor(ms)));
    });
}

export function isRetryableQueryError(error: unknown): boolean {
    if (isRetryableHttpError(error)) {
        return true;
    }
    if (error instanceof TypeError) {
        return true;
    }
    if (!(error instanceof Error)) {
        return false;
    }
    const msg = String(error.message || '').toLowerCase();
    return msg.includes('networkerror')
        || msg.includes('failed to fetch')
        || msg.includes('timed out')
        || msg.includes('timeout');
}

export async function runWithRetry<T>(runner: () => Promise<T>, options?: RetryOptions): Promise<T> {
    const maxRetries = Math.max(0, Math.min(3, Math.floor(options?.maxRetries ?? 1)));
    const baseDelayMs = Math.max(50, Math.min(2000, Math.floor(options?.baseDelayMs ?? DEFAULT_BASE_DELAY_MS)));

    let attempt = 0;
    let lastError: unknown;
    while (attempt <= maxRetries) {
        try {
            return await runner();
        } catch (error) {
            lastError = error;
            if (attempt >= maxRetries || !isRetryableQueryError(error)) {
                throw error;
            }
            const nextDelay = baseDelayMs * (attempt + 1);
            await delay(nextDelay);
            attempt += 1;
        }
    }
    throw lastError instanceof Error ? lastError : new Error('查询失败');
}
