# Sprint-11 Performance Report

**Status**: Baseline targets defined; measurements pending.

## Targets

| Metric | Target | Measurement Method |
|---|---|---|
| SqlIdePage 首屏渲染 | ≤ 800ms | Lighthouse LCP |
| Tab 切换延迟 | ≤ 100ms | Performance API, marks around setActive |
| Schema 树单节点展开 | ≤ 300ms（缓存命中 ≤ 50ms） | React DevTools profiler |
| 10k 行 Grid 滚动 | 60fps | Chrome DevTools Performance |
| 100k 行 CSV 导出 | ≤ 30s | 服务端日志 |
| 后端 /page P99 | ≤ 200ms | JMH / k6 |

## Measurements

待实施后补充。
