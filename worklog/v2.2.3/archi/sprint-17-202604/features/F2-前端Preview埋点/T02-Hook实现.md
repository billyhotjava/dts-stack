# T02: useScreenVisitTracker hook

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

封装 visit 防抖 + timer 管理，复用方便、可单测。

## 技术设计

```tsx
// src/analytics/pages/screens/hooks/useScreenVisitTracker.ts
import { useEffect, useRef } from "react";
import reportsService from "@/api/services/reportsService";

const STAY_MS = 3_000;
const DEDUPE_MS = 30_000;
const STORAGE_KEY = "dts.bi.screenVisit.lastFired.v1";

interface Args {
  screenId: string | number | undefined;
  title?: string;
  enabled: boolean;
}

export function useScreenVisitTracker({ screenId, title, enabled }: Args): void {
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    if (!enabled || screenId == null) return;

    const code = `screen-${screenId}`;
    if (recentlyFired(code)) return;

    timerRef.current = window.setTimeout(() => {
      fire(code, title, screenId);
    }, STAY_MS);

    return () => {
      if (timerRef.current != null) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [enabled, screenId, title]);
}

function recentlyFired(code: string): boolean {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return false;
    const map = JSON.parse(raw) as Record<string, number>;
    const last = map[code];
    return typeof last === "number" && Date.now() - last < DEDUPE_MS;
  } catch {
    return false;
  }
}

function markFired(code: string): void {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const map = raw ? (JSON.parse(raw) as Record<string, number>) : {};
    map[code] = Date.now();
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    /* swallow */
  }
}

async function fire(code: string, title: string | undefined, id: string | number): Promise<void> {
  try {
    await reportsService.visit({
      code,
      title: title ?? "",
      url: `/bi/screens/${id}/preview`,
    });
    markFired(code);
  } catch {
    /* visit 失败不影响 preview，吞掉 */
  }
}
```

### 设计点

- **防抖键 = code + 30s 时间窗**：同一个用户在 30s 内重复进出同一 screen 只记一条；切换 screen 不抖
- **sessionStorage 而非 localStorage**：跨 tab 不共享（避免一个 tab 触发了别的 tab 静默），关 browser 自动清
- **3s 计时器在 unmount 时清**：避免短停留误触
- **fire 失败吞掉**：visit 是辅助统计，不阻塞主流程；后端有 audit 日志兜底
- **不对 reportsService.visit 加重试**：reconcile 跑得勤（每小时一次），单条丢失影响有限

## 影响范围

- `source/dts-platform-webapp/src/analytics/pages/screens/hooks/useScreenVisitTracker.ts`（新建）
- `source/dts-platform-webapp/src/analytics/pages/screens/hooks/useScreenVisitTracker.test.ts`（T03 任务）

## 验证

- [ ] hook 接受 `enabled=false` 不启动 timer
- [ ] `enabled=true` + 等 3s 后调一次 `reportsService.visit`
- [ ] 30s 内同 id 第二次挂载不触发 visit
- [ ] 卸载时 timer 已清

## 完成标准

- [ ] hook 文件创建
- [ ] 类型签名清晰，无 any
- [ ] 防抖 + cleanup + error swallow 三层都到位
