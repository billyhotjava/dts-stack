# F1-T02 manual-conflict sample

Captured on 2026-07-19 from the pure `BusinessObjectRetirementPolicyTest` contract run.

- CWD: `/opt/prod/s10/v2.2.3/source/dts-platform`
- Command: `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -Dtest=BusinessObjectRetirementPolicyTest test`
- Exit code: `0`
- Result: `23 tests, 0 failures, 0 errors, 0 skipped`
- Production migration executed: **no**

## Sample A: duplicate source-field identity

Input records `Status_Code` and `STATUS_CODE` normalize to the same source-field identity, even when both carry the same `STANDARD` signal.

Verified decision:

- one output only: `FieldDisposition(sourceField=status_code, owner=MANUAL_REVIEW)`;
- conflict: `FIELD_DUPLICATE:status_code`;
- readiness: `NEEDS_CLASSIFICATION`;
- executable action: absent.

The conflicting-signal variant (`Amount=ATTRIBUTE`, `amount=METRIC`) is governed by the same one-owner rule.

## Sample B: conflicting metadata for one target ID

Input candidates use the same `modelSpecId=fact-order` with `(FACT, operations)` and `(DIMENSION, operations)` metadata.

Verified decision:

- conflict: `TARGET_METADATA_CONFLICT:fact-order`;
- target model: absent;
- readiness: `NEEDS_CLASSIFICATION`;
- executable action: absent.

## Sample C: archive with unresolved source metadata

An archive candidate with a `JOINED` source lacking alias and explicit join type produces `SOURCE_ALIAS_MISSING` and `SOURCE_JOIN_TYPE_AMBIGUOUS`.

Verified decision: zero consumers is not sufficient; readiness remains `NEEDS_CLASSIFICATION` and `ARCHIVE_ONLY` is not executable.

## Sample D: incomplete FACT target metadata

An otherwise unique compatible FACT target is accompanied by a candidate whose `modelType` or `domainId` is null or blank.

Verified decision:

- conflict: `TARGET_METADATA_INCOMPLETE:<modelSpecId>`;
- readiness: `NEEDS_CLASSIFICATION`;
- executable action and target write metadata: absent.

A single incomplete candidate follows the same classification path. Complete non-FACT and different-domain candidates remain provably incompatible and may be filtered without blocking one compatible FACT target.
