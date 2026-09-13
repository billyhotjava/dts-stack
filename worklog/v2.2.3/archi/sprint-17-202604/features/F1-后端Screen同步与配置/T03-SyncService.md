# T03: ScreenReportLinkSyncService reconcile 主逻辑

**优先级**: P0
**状态**: READY
**依赖**: T01, T02

## 目标

把 dts-bi 的 screens 列表 reconcile 进 `bi_report_link`，让 leader-overview 自动覆盖大屏。

## 技术设计

```java
@Service
@Transactional
public class ScreenReportLinkSyncService {

    private static final String SOURCE_TAG = "SCREEN_SYNC";
    private static final String CODE_PREFIX = "screen-";
    private static final String TYPE = "SCREEN";
    private static final String ENGINE = "DTS_BI";

    private final DtsAnalyticsClient client;
    private final BiReportLinkRepository repo;

    public SyncResult reconcileOnce() {
        List<ScreenSummary> screens;
        try {
            screens = client.listScreens();
        } catch (NotConfiguredException e) {
            log.info("dts-analytics not configured, skip reconcile");
            return SyncResult.skipped();
        } catch (RestClientException e) {
            log.warn("dts-analytics list failed: {}", e.getMessage());
            return SyncResult.failed(e);
        }

        Map<String, BiReportLink> existing = repo.findAllBySource(SOURCE_TAG)
            .stream()
            .collect(Collectors.toMap(BiReportLink::getCode, l -> l));

        int created = 0, updated = 0, archived = 0;
        for (ScreenSummary s : screens) {
            String code = CODE_PREFIX + s.id();
            BiReportLink link = existing.remove(code);
            if (link == null) {
                link = newLink(code, s);
                created++;
            } else {
                if (applyUpdate(link, s)) updated++;
            }
            repo.save(link);
        }

        // existing 中剩下的是 dts-bi 已经删除的大屏，统一软停用
        for (BiReportLink stale : existing.values()) {
            if (stale.getEnabled()) {
                stale.setEnabled(false);
                repo.save(stale);
                archived++;
            }
        }

        return SyncResult.ok(created, updated, archived);
    }

    private BiReportLink newLink(String code, ScreenSummary s) {
        BiReportLink l = new BiReportLink();
        l.setCode(code);
        l.setTitle(s.name());
        l.setReportType(TYPE);
        l.setEngine(ENGINE);
        l.setUrl("/bi/screens/" + s.id() + "/preview");
        l.setBizDomain(null);                       // dts-bi 暂无 bizDomain
        l.setClassification(s.classification());
        l.setDeptCodes(s.ownerDeptCode());
        l.setEnabled(!s.archived());
        l.setSource(SOURCE_TAG);
        l.setSortOrder(0);
        return l;
    }

    private boolean applyUpdate(BiReportLink l, ScreenSummary s) {
        boolean dirty = false;
        if (!Objects.equals(l.getTitle(), s.name())) { l.setTitle(s.name()); dirty = true; }
        if (!Objects.equals(l.getClassification(), s.classification())) { l.setClassification(s.classification()); dirty = true; }
        if (!Objects.equals(l.getDeptCodes(), s.ownerDeptCode())) { l.setDeptCodes(s.ownerDeptCode()); dirty = true; }
        boolean shouldEnable = !s.archived();
        if (l.getEnabled() != shouldEnable) { l.setEnabled(shouldEnable); dirty = true; }
        return dirty;
    }
}

public record SyncResult(int created, int updated, int archived, boolean skipped, String error) {
    public static SyncResult ok(int c, int u, int a) { return new SyncResult(c, u, a, false, null); }
    public static SyncResult skipped() { return new SyncResult(0, 0, 0, true, null); }
    public static SyncResult failed(Exception e) { return new SyncResult(0, 0, 0, false, e.getMessage()); }
}
```

`BiReportLinkRepository` 加方法：

```java
List<BiReportLink> findAllBySource(@Param("source") String source);
```

## 防误删保护

- reconcile 只 SELECT `source = 'SCREEN_SYNC'` 的行
- 手工创建的 BiReportLink（`source IS NULL` 或 `MANUAL`）完全不出现在 reconcile 集合里
- 即使有人手工把某行 `source` 改成了 `SCREEN_SYNC` 但 dts-bi 又没有对应大屏，会被软停用（`enabled=false`），不会物理删除——保留人工恢复机会

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/integration/ScreenReportLinkSyncService.java`（新建）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/integration/SyncResult.java`（新建）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/visualization/BiReportLinkRepository.java`（加 findAllBySource）

## 验证

- [ ] 给定 mock client 返回 3 条 screens（2 active, 1 archived），首次 reconcile 创建 3 条 BiReportLink，第三条 enabled=false
- [ ] 再次 reconcile 同一份返回，updated=0
- [ ] 改一条 screen 名，reconcile 后 title 同步更新
- [ ] mock 返回中删掉一条，下次 reconcile 软停用对应行
- [ ] 数据库里 `source='MANUAL'` 的行不被读到，更不会被改

## 完成标准

- [ ] SyncService + SyncResult 文件创建
- [ ] Repository 加 findAllBySource
- [ ] reconcileOnce() 同步语义符合上述四种 case
