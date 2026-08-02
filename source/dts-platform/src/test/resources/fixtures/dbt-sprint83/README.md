# Sprint-83 dbt engineering fixtures

These fixtures are synthetic, contain no customer data or credentials, and are
the offline engineering admission set for Sprint-83.

- `fx01-artifact-rich/`: dbt manifest v12 plus catalog evidence. It contains a
  technical staging model, one governed fact model, one attached test, one
  source, and one macro.
- `fx03-basic-blocked/`: source-only project with one missing literal ref and
  one dynamic ref. Static inspection must keep the affected model blocked.
- `fx05-malicious/cases.json`: deterministic archive-security cases. Tests
  construct the unsafe ZIP entries in memory; no executable or unsafe binary is
  stored in Git.

`inventory.json` records the expected engineering profile. `SHA256SUMS` pins
every fixture input used by the tests. Materialization is intentionally
`NOT_CERTIFIED`; runtime evidence belongs to H83-01 and F0/T05.
