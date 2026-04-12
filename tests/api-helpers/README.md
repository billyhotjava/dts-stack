# /test/** 辅助 API

供外部测试用例调用的测试辅助端点，直接挂在 `dts-admin` / `dts-platform` 同一端口下，路径前缀 `/test/**`。

## 安全模型

**双闸门**（代码里强制）：

1. `app.test-api.enabled=true` — 需显式开启
2. 当前 profile 不是 `prod` — 即使配错了，生产 profile 也不会装载

外加 `X-Test-Token` header 做访问控制（`TestApiAuthFilter` 常量时间比对）。

## 启用方式

只在 dev/test 环境设置：

```bash
# .env（非生产）
APP_TEST_API_ENABLED=true
APP_TEST_API_TOKEN=$(openssl rand -hex 32)
```

docker-compose 需把这两个变量注入到 `dts-admin` 与 `dts-platform` 两个容器。如果想给两个模块用不同的 token，可以在 compose 里展开：

```yaml
dts-admin:
  environment:
    APP_TEST_API_ENABLED: ${APP_TEST_API_ENABLED:-false}
    APP_TEST_API_TOKEN: ${APP_TEST_API_ADMIN_TOKEN:-}

dts-platform:
  environment:
    APP_TEST_API_ENABLED: ${APP_TEST_API_ENABLED:-false}
    APP_TEST_API_TOKEN: ${APP_TEST_API_PLATFORM_TOKEN:-}
```

## 端点

| 方法 | 路径 | 用途 | 当前状态 |
|---|---|---|---|
| GET  | `/test/ping`            | 健康/联通性探测，返回 `module` 标识 | ✅ 已实现 |
| POST | `/test/reset-data`      | 清空/重置测试数据           | ⚠️ 501 stub，需按各模块 schema 填充 |
| POST | `/test/seed/{scenario}` | 灌入指定场景数据            | ⚠️ 501 stub |
| POST | `/test/clock/advance`   | 时间穿越（测定时任务）      | ⚠️ 501 stub |

**为什么是 stub？** 重置/灌数据逻辑强依赖业务 schema，得由熟悉领域的同事按需补实现。骨架（鉴权 + 路由 + 开关）已就绪，补实现只需往 `TestApiResource#resetData/seed/advanceClock` 里加代码。

## 调用示例

```bash
curl -sk -H "X-Test-Token: $APP_TEST_API_TOKEN" \
  https://biadmin.yuzhicloud.com/test/ping

# -> {"status":"ok","module":"admin","ts":"2026-04-12T..."}
```

## 代码位置

| 文件 | admin | platform |
|---|---|---|
| Properties | `config/TestApiProperties.java` | 同左 |
| Filter | `web/filter/TestApiAuthFilter.java` | 同左 |
| Security | `config/TestApiSecurityConfiguration.java` | 同左 |
| Controller | `web/rest/testapi/TestApiResource.java` | 同左 |
| YML | `application.yml` 末尾 `app.test-api.*` | 同左 |

## 生产验证

部署后自检：

```bash
# prod profile 下无论 enabled 是否为 true，都应 404
curl -sk -o /dev/null -w "%{http_code}\n" https://biadmin.yuzhicloud.com/test/ping  # 期望 404
```
