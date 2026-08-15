#!/usr/bin/env bash
set -euo pipefail

project_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
output_path=${1:-"$project_dir/../pjm-dbt-model.zip"}
metric_handbook="$project_dir/../metric-handbook.md"
metric_registry="$project_dir/../metric-registry.json"
stage_dir=$(mktemp -d)
archive_dir=$(mktemp -d)
archive_tmp="$archive_dir/archive.zip"

cleanup() {
  case "$stage_dir" in
    /tmp/*) rm -rf -- "$stage_dir" ;;
  esac
  case "$archive_dir" in
    /tmp/*) rm -rf -- "$archive_dir" ;;
  esac
}
trap cleanup EXIT

for required in \
  "$project_dir/dbt_project.yml" \
  "$project_dir/package-contract.yml" \
  "$project_dir/target/manifest.json" \
  "$project_dir/target/catalog.json" \
  "$metric_handbook" \
  "$metric_registry"; do
  if [[ ! -f "$required" ]]; then
    echo "missing package input: $required" >&2
    exit 1
  fi
done

mkdir -p "$stage_dir/target" "$stage_dir/docs" "$stage_dir/scripts"
cp "$project_dir/dbt_project.yml" "$project_dir/package-contract.yml" \
  "$project_dir/README.md" "$project_dir/model-governance.md" \
  "$project_dir/models.tsv" "$stage_dir/"
cp -R "$project_dir/models" "$project_dir/macros" "$project_dir/tests" "$stage_dir/"
cp "$project_dir/scripts/sync_schema_contracts.py" "$stage_dir/scripts/"
cp "$project_dir/target/manifest.json" "$project_dir/target/catalog.json" "$stage_dir/target/"
cp "$metric_handbook" "$stage_dir/docs/metric-handbook.md"
cp "$metric_registry" "$stage_dir/docs/metric-registry.json"

(
  cd "$stage_dir"
  zip -X -q -r "$archive_tmp" .
)

if unzip -Z1 "$archive_tmp" | grep -Eq '(^|/)(profiles\.yml|\.env|logs/|dbt_packages/|run_results\.json|index\.html|partial_parse\.msgpack)'; then
  echo "forbidden runtime or secret-bearing entry found in archive" >&2
  exit 1
fi

mkdir -p "$(dirname "$output_path")"
mv "$archive_tmp" "$output_path"
sha256sum "$output_path"
