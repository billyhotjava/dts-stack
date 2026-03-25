# dts-build Analytics Modern Minimal Context Design

## Goal

Reduce the Docker build context for `dts-analytics-webapp-modern` so `docker build` no longer sends the entire repository to the daemon.

## Current Problem

`builds/dts-build.sh` currently invokes:

```bash
docker build -t <tag> -f builds/dts-analytics-webapp/modern/Dockerfile .
```

with the repository root as context. That causes multi-gigabyte context uploads even though the Dockerfile only needs:

- `builds/dts-analytics-webapp/modern/Dockerfile`
- `builds/dts-analytics-webapp/modern/nginx.conf`
- `source/dts-analytics-webapp/modern/`

## Approach

Keep the Dockerfile unchanged and change only `builds/dts-build.sh`:

1. Create a temporary directory.
2. Reconstruct only the required repository subpaths inside that directory.
3. Invoke `docker build` with the temporary directory as the build context.
4. Remove the temporary directory after the build finishes.

## Scope

Only optimize `dts-analytics-webapp-modern` in this change. Other images keep their current behavior.

## Validation

- `bash -n builds/dts-build.sh`
- Verify the helper creates the expected minimal tree
- Run the single-image build path logic far enough to confirm the generated context is used
