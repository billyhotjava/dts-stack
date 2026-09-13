# Sprint-29 集成测试

**状态**: 待 Sprint 进入 IN_PROGRESS 后逐 Feature 补充

## 测试矩阵

| Feature | IT 范围 | 证据位置 | 责任 |
|---------|---------|---------|------|
| F0 | Chrome 95 polyfill 真机/模拟器冒烟 | `assets/chrome95-polyfill-test-evidence.md` | F0-T01 |
| F1 | 画布壳 + 三层 store 单测覆盖率 ≥ 80% | `it/F1-foundation-evidence.md` | F1 完成时补 |
| F2 | 节点库拖拽 e2e（playwright） | `it/F2-dnd-evidence.md` | F2 完成时补 |
| F3 | 6 类节点 snapshot 测试 + 节点 ARIA 校验 | `it/F3-nodes-evidence.md` | F3 完成时补 |
| F4 | DSL 序列化/反序列化幂等性单测 + 后端 IT（保存→读取→反序列化） | `it/F4-dsl-evidence.md` | F4 完成时补 |
| F5 | iteration + loop 子流程 DSL 嵌套测试 + 撤销重做 50 步压测 | `it/F5-advanced-evidence.md` | F5 完成时补 |

## 浏览器兼容矩阵

每个 Feature 完成前必须在以下环境冒烟：

| 浏览器 | 版本 | 渠道 |
|--------|------|------|
| Chrome | 95（项目下限）| 真机或 DevTools 设备模拟 |
| Chrome | 109+（主流）| 真机 |
| Firefox | 102+（ESR）| 真机 |
| Safari | 15.4+ | 真机（Mac）|

## Chrome 95 兼容性 grep 检查清单

每次 Feature DONE 前，跑：

```bash
# 不应有任何业务代码命中（只有 polyfill + node_modules 命中）
grep -rn 'structuredClone' source/dts-platform-webapp/src/

# 不应有任何业务代码命中
grep -rn '\.toReversed\(\|\.toSorted\(\|Object\.hasOwn(' source/dts-platform-webapp/src/

# 不应有任何业务代码命中
grep -rn ':has(\|@layer\|oklch(\|color-mix(' source/dts-platform-webapp/src/
```

## 部署阶段验证（待 sprint 收尾时补全）

- [ ] 生产构建 bundle 大小：基线 vs 引入 workflow 后 diff
- [ ] Lighthouse 性能：LCP / TBT / CLS 不退化超 10%
- [ ] 后端 graph_dsl 字段 Liquibase changelog 在 staging 跑一次完整 baseline
- [ ] 老 OrchestrationPage 链接 redirect 校验（运行实例 Tab 可达）

## 缺陷追踪

发现的 IT 缺陷请在本目录新建 `bug-{YYYYMMDD}-{slug}.md`，并在 sprint README 风险与对策表中追加链接。
