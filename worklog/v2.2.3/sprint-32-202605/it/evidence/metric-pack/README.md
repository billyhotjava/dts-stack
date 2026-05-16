# Metric Pack Evidence

## Scope

验证 `dts-metrics` MVP 指标包接口可以接收 YAML Manifest，并完成基础结构校验，为后续合作方交付指标包提供受控入口。

## Command

```bash
curl -fsS -X POST \
  http://127.0.0.1:18084/api/metrics/packs/validate \
  -H 'Content-Type: application/yaml' \
  --data-binary @worklog/v2.2.3/sprint-32-202605/assets/examples/flower-rental/manifest.yml
```

## Result

接口返回 `valid=true`，并识别出：

- `packId=flower-rental`
- `industry=flower_rental`
- `version=0.1.0`
- `editionRequired=professional`

## Guardrails

- Manifest 必须包含 `pack_id`、`pack_name`、`version`、`industry`、`edition_required`。
- Manifest 必须包含 `files` 和 `dependencies` 对象。
- `files` 必须声明 `domains`、`business_objects`、`dimensions`、`metrics`、`models`、`datasets`。
- 当前校验拒绝 `raw_sql`，避免合作方指标包绕过平台受控建模边界。

## Notes

- 本阶段 `import` 接口仍是 dry-run 语义，不写入平台资产表。
- 后续 Sprint-32 任务需要把 Manifest 导入结果写入 Platform 管理的资产、授权和审计链路。
