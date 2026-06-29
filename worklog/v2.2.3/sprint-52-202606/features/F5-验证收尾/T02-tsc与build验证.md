# T02: tsc + build + 全量验证

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

过全量 tsc、生产构建（Chrome 95 legacy bundle）和全部 source-contract，产出 IT 证据存入 `it/README.md`。

## 执行命令

```bash
# 1. TypeScript 检查
cd source/dts-platform-webapp
pnpm exec tsc --noEmit 2>&1 | tail -5

# 2. 全量 source-contract
node --test 'src/**/*.source-contract.test.ts' 2>&1 | grep -E "pass|fail|ok|not ok" | tail -20

# 3. 生产构建
pnpm build 2>&1 | tail -10
```

## 验证（IT 证据要求）

- [ ] tsc 输出 0 errors，记录输出
- [ ] source-contract: 通过数 ≥ 133（Sprint-51 baseline），失败数 ≤ 10（baseline）
- [ ] build 输出 "built in Xs"，无 chunk size 警告超限

## 完成标准

- [ ] 三项均通过，证据写入 `worklog/v2.2.3/sprint-52-202606/it/README.md`
