# Platform Contracts Evidence

## Scope

验证 `dts-metrics` 作为独立服务调用 platform internal API 时，必须使用 `X-DTS-Service` / `X-DTS-Service-Token`，且只能访问明确允许的契约端点。

## Verified Contract

允许 `dts-metrics` 访问：

- `GET /api/internal/capabilities`
- `GET /api/catalog/assets-v2`
- `GET /api/catalog/assets-v2/{id}/contract`
- `GET /api/catalog/assets-v2/{id}/schema-contract`
- `POST /api/internal/asset-permission/check`
- `POST /api/internal/asset-permission/batch-check`
- `POST /api/internal/asset-permission/accessible-ids`
- `GET /api/internal/asset-permission/grants`
- `POST /api/etl/dbt/release-gate/check`
- `POST /api/etl/dbt/release/submit`

明确不允许：

- `POST /api/internal/asset-permission/grants`
- `DELETE /api/internal/asset-permission/grants/**`
- `GET /api/infra/data-sources/{id}/runtime-detail`

## Commands

```bash
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform -Dtest=ServiceDependencyAuthenticationFilterTest test
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-platform -DskipTests compile
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-metrics test
```

## Results

- `dts-metrics` token 匹配时可注入 `service:dts-metrics` principal。
- `dts-metrics` 可访问 `/api/internal/capabilities`。
- `dts-metrics` 可做 asset permission 校验和只读 grants 查询。
- `dts-metrics` 尝试写入 asset grant 时被 `endpoint_not_allowed` 拒绝。
- platform 编译和 metrics 单测需要在 Sprint-31A/31/32 全部完成后统一重跑。

## Notes

该证据只覆盖服务鉴权边界。当前已补齐 metrics 客户端 URL 拼接测试，防止 `/api/api/internal/...` 双前缀；真实 internal API 调用输出待最终统一 IT 归档。
