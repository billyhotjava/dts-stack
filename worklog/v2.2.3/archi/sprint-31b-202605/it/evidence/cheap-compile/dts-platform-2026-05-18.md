# dts-platform Cheap Compile Evidence

日期：2026-05-18

## 命令

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -pl dts-platform -am -DskipTests compile
```

## 结果

- 退出码：0
- 结论：BUILD SUCCESS
- `ERROR` 数量：0
- `WARNING` 数量：35

## 主要 warning

- `dts-platform/pom.xml` 存在重复 `maven-compiler-plugin` 声明。
- `poi-ooxml` 依赖 convergence warning：`easyexcel` 引入 `4.1.2`，平台直接引入 `5.3.0`。
- 多处 `RestTemplateBuilder#setConnectTimeout` / `setReadTimeout` deprecation warning。
- `Subject#doAs(...)` deprecation warning。

这些 warning 均为既有构建警告，本次 cheap compile 未发现 Sprint-31A/31B 引入的编译错误。
