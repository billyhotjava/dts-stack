# API E2E 测试（Java）

基于 JUnit 5 + RestAssured + Allure 的 API 回归套件，面向熟悉 Java 的测试同事。与 `tests/api-e2e-python/` 覆盖面等价，二选一或并行使用均可。

## 目录结构

```
tests/api-e2e-java/
├── pom.xml
├── .env.example
├── run.sh
├── src/test/
│   ├── java/com/yuzhi/dts/apitests/
│   │   ├── support/           # Env / Keycloak / BaseTest
│   │   ├── testapi/           # /test/** 自测
│   │   ├── admin/             # 三员 API 用例
│   │   └── platform/          # 平台 API 用例
│   └── resources/junit-platform.properties
└── target/allure-results/     # gitignored
```

## 快速开始

```bash
cd tests/api-e2e-java
cp .env.example .env && vim .env

# 跑 smoke（标记 @Tag("smoke")）
bash run.sh smoke

# 仅 admin / platform
bash run.sh admin
bash run.sh platform

# 全量
bash run.sh all

# Allure 报告
mvn -q allure:serve
```

`.env` 通过 `run.sh` 注入为环境变量，支持变量同 Python 版（见 `.env.example`）。

## 写新用例

参考 `src/test/java/com/yuzhi/dts/apitests/admin/AuthSmokeIT.java`。要点：

1. 继承 `BaseAdminTest` / `BasePlatformTest` 拿预配置的 RestAssured `RequestSpecification`
2. 需要 `/test/**` 辅助时用 `BaseTestApiTest`
3. 一律 `@Tag` 标注套件归属（`admin` / `platform` / `smoke`）
