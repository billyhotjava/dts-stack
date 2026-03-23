# ELT-003：质量基线与回归门禁建立

## 目标

为接入中心和开发中心建立最小可执行的测试基线和回归门禁。

## 重点文件

- `source/dts-ingestion/src/test/java/...`
- `source/dts-platform/src/test/java/...`
- `tests/web-e2e/specs/...`
- `worklog/v2.2.2/sprint-6/req/elt-quality-baseline-gap-analysis.md`

## 交付标准

- 列出当前已有测试
- 列出缺口
- 固定最小回归命令
- 固定需要新增的 E2E 冒烟方向

## 当前结论

- 接入中心最小回归面当前不可运行，优先级高于继续补新测试
- 开发中心最小回归面可运行但为红，说明契约与实现漂移
- `dts-platform-webapp` 的 ELT 页面当前没有独立行为测试
- `tests/web-e2e` 现有用例不覆盖 ELT 两大中心的真实执行异常链

## 已执行验证

```bash
find source/dts-ingestion/src/test/java -type f | sort
find source/dts-platform/src/test/java -type f | sort
find tests/web-e2e/specs -type f | sort
rg --files source/dts-platform-webapp/src | rg "(Transform|SqlModeling|QueryWorkbench|Orchestration|ScriptStudio).*\\.(test|spec)\\.(ts|tsx)$"
```

## 建议进入门禁的最小集合

- `source/dts-ingestion`：先恢复 `IngestionTaskService*` 相关测试可运行
- `source/dts-platform`：保持 `DbtDagService/DbtOutputRelation/EtlResource/Gate` 这组为主门禁
- `source/dts-platform-webapp`：至少保留 `pnpm build`
- `tests/web-e2e`：新增接入中心执行链和开发中心 compile/test/build 两组冒烟
