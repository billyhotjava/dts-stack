# Sprint-31A Status Rectification - 2026-05-18

## Decision

Sprint-31A is now described as:

```text
CONTRACT_DONE / RUNTIME_PARTIAL
```

This replaces the previous mixed phrase `CONTRACT_DONE / ENFORCEMENT_IN_PROGRESS`.

## Rationale

The Sprint-31A foundation is no longer undefined or blocked. The platform asset fact-source contract is in place for:

- asset identity and lifecycle;
- governance field read contract;
- lineage and provenance entry points;
- asset permission and classification checks;
- asset portal UX;
- migration compatibility;
- RX guardrails for asset types, asset keys, metric-pack validation, code asset writers, resolver coverage, and preview-stage RLS/masking.

The remaining work is runtime closure, not contract design. That work belongs to Sprint-31B:

- platform/dbt publish gate must recheck the same RLS/masking policy;
- RLS/masking injection audit must be queryable;
- live dialect IT must prove PostgreSQL and Doris SQL behavior;
- cheap compile evidence must be captured before Sprint-32 final IT.

## Reader Guidance

External reviewers should read Sprint-31A as:

```text
Contract layer: done.
Runtime enforcement layer: partially complete, tracked in Sprint-31B.
```

They should not interpret RX as fully enterprise GA until Sprint-31B F2/F5 are closed.
