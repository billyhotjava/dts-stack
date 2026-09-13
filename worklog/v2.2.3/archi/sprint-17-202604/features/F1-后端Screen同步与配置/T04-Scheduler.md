# T04: ScreenReportLinkSyncScheduler 调度

**优先级**: P1
**状态**: READY
**依赖**: T03

## 目标

把 reconcile 跑起来：启动后跑一次（≤60s），之后每小时一次。

## 技术设计

```java
@Component
@ConditionalOnProperty(prefix = "dts.analytics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScreenReportLinkSyncScheduler {

    private final ScreenReportLinkSyncService service;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 启动后异步触发一次（不阻塞 boot）
        CompletableFuture.runAsync(this::tick);
    }

    @Scheduled(cron = "0 17 * * * *")  // 每小时第 17 分钟
    public void tick() {
        try {
            SyncResult r = service.reconcileOnce();
            log.info("screen reconcile: created={} updated={} archived={} skipped={} error={}",
                r.created(), r.updated(), r.archived(), r.skipped(), r.error());
        } catch (Exception e) {
            log.error("screen reconcile crashed", e);
        }
    }
}
```

需要在 main application class（或现有配置类）保证 `@EnableScheduling` 已开启。

### 多实例并发

dts-platform 通常单实例部署。如有多实例需求，加分布式锁：

```java
@SchedulerLock(name = "screen_reconcile", lockAtMostFor = "PT5M")
public void tick() { ... }
```

依赖 `shedlock-spring`（看现有 pom 是否已引入；没有的话本 Sprint 不强加，注释里标 follow-up）。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/integration/ScreenReportLinkSyncScheduler.java`（新建）
- 启动配置类确认 `@EnableScheduling` 已开启（grep 现有代码）

## 验证

- [ ] 本地启动应用，60s 内日志出现 `screen reconcile: created=…`
- [ ] 调整 cron 为 `0/2 * * * * *`（每 2 分钟）做手测，定时触发
- [ ] `dts.analytics.enabled=false` 时 scheduler bean 不创建（不报错）
- [ ] reconcile crash 不导致 scheduler 死掉（catch 全异常）

## 完成标准

- [ ] Scheduler bean 创建
- [ ] 启动事件 + 定时任务都能触发
- [ ] 异常隔离，单次失败不影响下一次
