# Sprint-39 Evidence Template

本目录存放 `it/scripts/golden-chain-*.sh` 的 live 运行证据。

文件命名：

```text
golden-chain-{scenario}-{yyyyMMddHHmmss}.json
```

每份证据至少包含：

- `scenario`: `jdbc` / `api` / `file` / `governance-permission-ops`
- `chainKey`: 黄金链路实例 key
- `capturedAt`: 采集时间
- `list`: `/api/golden-chains` 输出
- `detail`: `/api/golden-chains/{chainKey}` 输出

验收口径：

- IT-01: `golden-chain-jdbc.sh`
- IT-02: `golden-chain-api.sh`
- IT-03: `golden-chain-file.sh`
- IT-04/IT-05/IT-06: `golden-chain-governance-permission-ops.sh`
- IT-07/IT-08: `customer-demo/` + `assets/customer-demo-acceptance-package.md`
