# dts-metrics Cheap Compile Evidence

日期：2026-05-18

## 命令

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -pl dts-metrics -am -DskipTests compile
```

## 结果

- 退出码：0
- 结论：BUILD SUCCESS
- `ERROR` 数量：0
- `WARNING` 数量：8

## 说明

warning 来自 Maven effective model / 上游 platform POM 提示，未发现 dts-metrics 内部签名漂移或编译错误。
