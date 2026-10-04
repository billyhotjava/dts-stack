# T01: ScreenPreviewPage 接入埋点

**优先级**: P0
**状态**: READY
**依赖**: 无（与 T02 并行；T01 wire-up，T02 hook 实现）

## 目标

在 `ScreenPreviewPage.tsx` 中调用 `useScreenVisitTracker` hook，触达"加载完 + 停留 ≥3s 后写 visit"的语义。

## 技术设计

```tsx
// src/analytics/pages/screens/ScreenPreviewPage.tsx
import { useScreenVisitTracker } from "./hooks/useScreenVisitTracker";

export default function ScreenPreviewPage() {
  const { id } = useParams();
  const screen = ...; // 已有的加载逻辑

  useScreenVisitTracker({
    screenId: id,
    title: screen?.name,
    enabled: !!screen,  // 加载完成后才开始计时
  });

  return ...;
}
```

`useScreenVisitTracker` 内部：
- `enabled=true` 触发 `setTimeout(fire, 3000)`
- `screenId` 变更（用户切换大屏）→ 清旧 timer，重新计
- 组件卸载 → 清 timer
- `fire()` 检查 sessionStorage 防抖，未触发过则 POST `/api/reports/visit` 然后写 sessionStorage

## 影响范围

- `source/dts-platform-webapp/src/analytics/pages/screens/ScreenPreviewPage.tsx`（接入 hook 调用）

## 验证

- [ ] 本地 `pnpm dev`，登录后进 `/bi/screens/:id/preview`，停留 4s 后 Network 看到 `POST /api/reports/visit`
- [ ] 1s 内离开页（未到 3s），无 POST
- [ ] 页面快速 unmount/mount，timer 不泄漏（DevTools 看 setInterval/setTimeout 列表）

## 完成标准

- [ ] ScreenPreviewPage 接入 hook 调用
- [ ] 不影响既有加载/渲染逻辑
