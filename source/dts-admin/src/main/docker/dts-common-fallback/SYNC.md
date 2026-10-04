# dts-common-fallback 同步规则

本目录是 `dts-common` 模块的镜像副本，用于 docker 镜像构建在隔离环境下作为
`dts-common` 的备份。**所有内容必须与 `source/dts-common/` 保持一致**。

## 必须保持同步的文件

- `src/main/resources/config/audit-action-catalog.json`
- `src/main/java/com/yuzhi/dts/common/audit/*.java`
- `pom.xml` 中的依赖与 GAV

## 检查方式

```
# 在 source/ 目录下执行
diff -r dts-common/src dts-admin/src/main/docker/dts-common-fallback/src
diff -r dts-common/src dts-platform/src/main/docker/dts-common-fallback/src
```

任何 diff 都说明 fallback 已与 dts-common 不同步——属于配置漂移，必须立刻修复。

## 长期目标

把这两个副本改成 maven 构建时自动拷贝（`maven-resources-plugin` + `<filtering>`），
彻底消除手动同步成本。在那之前，每次改 dts-common 的资源/代码，请同步两个 fallback。
