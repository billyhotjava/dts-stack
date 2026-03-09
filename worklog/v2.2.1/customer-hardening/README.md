# Customer Hardening Backlog (v2.2.1)

## Purpose

This backlog is for customer-line hardening work after rebasing the delivery branch to `2.2.1`.

It is separate from the historic `v2.2.1` task boards so that old completion records do not mix with current onsite bug fixing and UI polish.

## Tracks

- `BUG`: functional defects, broken flows, API or state inconsistencies
- `UI`: usability, validation, interaction, and information display polish
- `OPS`: small onsite runtime or operational fixes that do not change architecture

## Priority Order

1. `platform development / modeling / ETL`
2. `platform governance / access`
3. `admin configuration / ops`
4. optional `BI / analytics` local fixes only when required by the customer

## Rules

- Stay inside the `2.2.1` architecture
- Do not package-backport `v2.2.2+`
- Record reproduction and verification for every bug
- Keep rollback scope explicit for every ops patch
