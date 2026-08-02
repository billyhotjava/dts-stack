#!/bin/sh
set -eu

METADATA_FILE=/opt/dts-runtime/runtime-metadata

fail_closed() {
  printf '{"status":"FAILED","code":"DBT_RUNTIME_NOT_CERTIFIED"}\n' >&2
  exit 42
}

if [ ! -r "$METADATA_FILE" ]; then
  printf '{"status":"FAILED","code":"DBT_RUNTIME_METADATA_INVALID"}\n' >&2
  exit 43
fi

# The immutable image metadata is authoritative. In particular, a runtime
# DBT_RUNTIME_CERTIFICATION_STATUS=CERTIFIED override cannot authorize dbt.
# shellcheck disable=SC1091
. "$METADATA_FILE"

case "${IMAGE_CERTIFICATION_STATUS:-}" in
  CERTIFIED)
    exec dbt "$@"
    ;;
  NOT_CERTIFIED)
    ;;
  *)
    printf '{"status":"FAILED","code":"DBT_RUNTIME_METADATA_INVALID"}\n' >&2
    exit 43
    ;;
esac

case "${1:-}" in
  ""|--help|-h|help|--version|version|parse|list|ls)
    exec dbt "$@"
    ;;
  *)
    # Whitelist semantics intentionally block connection-capable debug,
    # compile/docs execution, run/build/seed/snapshot/test, run-operation,
    # retry, show and every future or globally-prefixed command by default.
    fail_closed
    ;;
esac
