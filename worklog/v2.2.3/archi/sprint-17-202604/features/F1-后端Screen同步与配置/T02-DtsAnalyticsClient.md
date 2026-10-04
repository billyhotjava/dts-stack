# T02: DtsAnalyticsClient 与配置

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

提供从 dts-platform 调用 dts-analytics REST API 的轻量 client：列举所有 screens，用于 reconcile 输入。

## 技术设计

### 配置

新建 `DtsAnalyticsProperties`：

```java
@ConfigurationProperties(prefix = "dts.analytics")
public class DtsAnalyticsProperties {
    private String baseUrl;          // e.g. http://dts-analytics:8080
    private String internalToken;    // service-account token
    private Duration timeout = Duration.ofSeconds(10);
    private boolean enabled = true;  // 关闭开关，方便本地/测试
}
```

`application.yml` 增加：

```yaml
dts:
  analytics:
    base-url: ${DTS_ANALYTICS_BASE_URL:}
    internal-token: ${DTS_ANALYTICS_INTERNAL_TOKEN:}
    timeout: 10s
    enabled: true
```

`base-url` 为空时 client 抛 `NotConfiguredException`，sync service 捕获后跳过 reconcile（不阻断启动）。

### Client

```java
@Component
public class DtsAnalyticsClient {
    private final RestClient restClient;
    private final DtsAnalyticsProperties props;

    public List<ScreenSummary> listScreens() {
        if (!props.isEnabled() || !StringUtils.hasText(props.getBaseUrl())) {
            throw new NotConfiguredException();
        }
        return restClient.get()
            .uri(props.getBaseUrl() + "/bi/api/screens")
            .header("X-Internal-Token", props.getInternalToken())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }
}
```

### DTO

```java
public record ScreenSummary(
    Long id,
    String name,
    String description,
    String classification,
    String ownerDeptCode,
    boolean archived,
    Instant updatedAt
) {}
```

字段映射来自 `analytics_screen` 表（dts-analytics `AnalyticsScreen` 实体）；只取我们需要的子集。

### 鉴权

参考现有 `dts-platform` 调 `dts-admin` / `dts-ingestion` 的内部调用模式（先查代码确认是否已有 `internal-token` 头约定）。如果没有，则在 dts-bi 端新增一个轻量的 `InternalAuthFilter`（白名单 path `/bi/api/screens` GET，校验 `X-Internal-Token`）—— 但这违反"零改动"约束，所以应优先复用既有机制；如确实没有则降级为 fail-closed（`enabled=false`，sync 跳过）并在文档里标 follow-up。

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/DtsAnalyticsProperties.java`（新建）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/integration/DtsAnalyticsClient.java`（新建）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/integration/dto/ScreenSummary.java`（新建）
- `source/dts-platform/src/main/resources/config/application.yml`（增加 `dts.analytics`）

## 验证

- [ ] 启动时不报错（即使 `base-url` 为空）
- [ ] mock dts-bi 返回 `[{"id":1,...}]`，client 能解析
- [ ] 超时（>10s）抛 `RestClientException`，调用方能捕获

## 完成标准

- [ ] DtsAnalyticsProperties + DtsAnalyticsClient + ScreenSummary 三文件创建
- [ ] application.yml 添加配置块
- [ ] `enabled=false` 或 `base-url` 为空时 client.listScreens() 抛 NotConfiguredException
