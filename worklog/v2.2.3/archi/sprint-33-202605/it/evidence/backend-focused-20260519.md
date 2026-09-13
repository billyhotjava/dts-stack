# Backend Focused Verification - 2026-05-19

## Command

```bash
cd /opt/prod/s10/v2.2.3/source
./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-admin -am -Dtest=AdminUserServiceListSnapshotsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Result

PASS.

## Evidence Notes

- `AdminUserServiceListSnapshotsTest` completed successfully.
- The focused test covers the role-assignment user query path and verifies filtered candidate users can return `inRole=true`.
- Output included expected Mockito / ByteBuddy dynamic-agent warnings.
- Output included existing service logs for empty snapshot refresh and missing management-client config in a negative path; the command exited with status 0.

