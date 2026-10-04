# Sprint-34 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development for each behavior change. Write focused failing tests first, then minimal implementation, then refactor.

**Goal:** Move audit classification runtime authority to dts-admin database catalogs and make platform audit logs represent human operations accurately.

**Architecture:** dts-admin owns runtime audit taxonomy in database tables. platform submits stable action codes and human-operation payloads. dts-admin classifies action codes deterministically, records sourceSystem faithfully, and creates classification miss records for unknown actions instead of guessing modules.

**Tech Stack:** Spring Boot, JPA/Hibernate, Liquibase, JUnit 5, Mockito, PostgreSQL JSON/INET columns.

---

## File Responsibilities

- `AuditActionRequest`: carry source system through the V2 audit write path.
- `AuditV2Service`: resolve button/action metadata from DB catalog, preserve sourceSystem, and write audit entries.
- `AuditActionCatalogService`: runtime DB lookup for module/action definitions and classification misses.
- Liquibase changelog: create DB catalog tables and seed minimum admin/platform actions.
- `AuditIngestResource`: pass platform sourceSystem/action metadata into `AuditV2Service`.
- `CatalogDomainResource`, `ReportsResource`, `SemanticModelingResource`: submit correct business action codes for human operations.
- `AuditLoggingFilter`: keep fallback for writes and meaningful reads only; suppress support queries.

## Execution Order

1. F1/T01-T03: add DB catalog schema, domain/repository/service, and seed minimum registry.
2. F2/T01-T04: route dts-admin V2 write path through DB catalog and sourceSystem pass-through.
3. F3/T01-T04: correct platform action codes for theme domains, semantic domains, reports, and fallback suppression.
4. F4/T01-T03: run focused verification, review findings, update docs and evidence.

## TDD Checkpoints

- RED: `AuditV2Service` persists `sourceSystem=platform` when request says platform.
- RED: known platform action code resolves module/action from DB catalog, not JSON.
- RED: unknown platform action code records classification miss and uses unclassified module.
- RED: domain create/update/delete no longer uses data asset action metadata.
- RED: report visit/create/update/delete records readable report actions.

## Review Loop

After focused tests pass, review the diff for:

- Any remaining runtime dependency on JSON for classification.
- Any new path-based module guessing in write path.
- Any support query or service actor being recorded as human audit.
- Any historical entry display that would change because the directory label changed.
- Any Liquibase seed or table constraint that makes field deployment fragile.
