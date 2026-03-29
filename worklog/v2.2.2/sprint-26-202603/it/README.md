# Sprint-26 IT

## 目标

记录 Platform统一Admin Gateway 重构的集成验证命令、结果和残余风险。

## 计划中的验证

- `cd source/dts-platform && ./mvnw -q -DskipTests compile`
- `cd source/dts-platform && ./mvnw -q -Dtest=DirectoryResourceTest,KeycloakAuthResourceTest,SecurityAuditLogProxyResourceTest test`
- `cd source/dts-platform-webapp && pnpm build`

## 结果

- [ ] 待执行

## 风险记录

- [ ] 待补充
