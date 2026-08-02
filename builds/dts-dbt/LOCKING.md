# dts-dbt dependency lock

`requirements.in` contains only the reviewed direct dependencies. The runtime
image installs `requirements.lock` with `--require-hashes`; do not hand-edit the
resolved file or install an additional distribution later in the Dockerfile.

Regenerate the lock with the exact generator image and tool version:

```sh
mkdir -p /tmp/dts-dbt-lock
docker run --rm \
  --platform linux/amd64 \
  -v "$PWD/builds/dts-dbt:/src:ro" \
  -v /tmp/dts-dbt-lock:/out \
  python:3.11-slim@sha256:db3ff2e1800a8581e2c48a27c3995339d47bdf046da21c7627accd3d51053a93 \
  sh -lc 'python -m pip install --disable-pip-version-check --no-cache-dir --require-hashes --requirement /src/lock-tool-requirements.lock >/dev/null && \
    pip-compile --generate-hashes --no-emit-index-url \
      --no-emit-trusted-host --strip-extras --resolver=backtracking \
      --output-file=/out/requirements.lock /src/requirements.in'
```

The generator bootstrap, including `pip-tools==7.5.2`, is itself reviewed and
hash locked. Its accepted `lock-tool-requirements.lock` SHA-256 is:

```text
e4860ac14f16fb4fd6f5b1383017f49f51ff4623174ae8e62458c9ac6c416f63
```

The fixed linux/amd64 generator base provides `pip==24.0` and
`setuptools==79.0.1`; they are part of the pinned base manifest rather than
installed from the bootstrap lock. Upgrading either requires a new generator
image digest and complete lock review. In particular, `pip==26.2` is not used:
it is incompatible with this pinned `pip-tools==7.5.2` generator.

Review the complete diff, then use `apply_patch` to replace the repository lock.
The accepted H83-01 candidate lock has SHA-256:

```text
01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02
```

The current lock and evidence only apply to `linux/amd64`. Both the generator
and runtime build must explicitly request that platform. An ARM64 candidate
requires a separate lock verification, platform manifest digest, candidate ID,
image digest and `it/rt-01/linux/arm64/...` evidence directory.

After any direct or transitive change, assign a new candidate profile ID that
contains the complete new lock SHA-256 and repeat all RT-01 evidence. The image
build rejects a changed lock with the old candidate identity. A regenerated
lock or image never inherits older evidence or certification status.
