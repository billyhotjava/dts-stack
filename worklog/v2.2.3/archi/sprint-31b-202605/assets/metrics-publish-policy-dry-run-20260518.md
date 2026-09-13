# Metrics Publish Policy Dry-Run Evidence (2026-05-18)

## Scope

Sprint-31B F2/T01 closes the preview/publish policy drift for the current dts-metrics publish preflight path.

## Runtime Path

```text
/api/metrics/packs/publish-dry-run
  -> MetricArtifactPublishService.publishDryRun
  -> MetricArtifactGenerationService.preview
  -> PlatformContractClient.resolveRlsPolicy
  -> securityPolicyJson + appliedPredicateHash
  -> PlatformContractClient.checkDbtReleaseGate
  -> /api/etl/dbt/release-gate/check
```

## Contract

The response includes:

- `appliedPolicySource`
- `appliedPredicateHash`
- `releaseGate`
- regenerated `artifacts.dbtModelSql`

The publish preflight never trusts a previously cached candidate SQL. It regenerates artifacts from the manifest and current platform policy.

## Verification

```bash
./mvnw -q -pl dts-metrics -Dtest=MetricArtifactGenerationServiceTest,MetricArtifactPublishServiceTest,PlatformContractClientTest,MetricPackResourceTest,MetricsFrontendResourceContractTest test
```
