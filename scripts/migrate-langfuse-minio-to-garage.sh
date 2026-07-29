#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(
  docker compose
  -f "${project_root}/compose.yaml"
  -f "${project_root}/compose.langfuse-migration.yaml"
  --profile langfuse
  --profile langfuse-migration
)

usage() {
  echo "Usage: $0 {start|copy|verify|stop-source}" >&2
}

run_rclone() {
  "${compose[@]}" run --rm langfuse-object-migrator "$@"
}

case "${1:-}" in
  start)
    "${compose[@]}" up -d --build \
      langfuse-garage langfuse-garage-init langfuse-minio-migration
    ;;
  copy)
    run_rclone copy minio:langfuse garage:langfuse \
      --create-empty-src-dirs --metadata --progress
    ;;
  verify)
    inventory_dir="$(mktemp -d)"
    trap 'rm -rf "$inventory_dir"' EXIT

    run_rclone lsf minio:langfuse --recursive --files-only --format 'sp' \
      | LC_ALL=C sort >"${inventory_dir}/minio.txt"
    run_rclone lsf garage:langfuse --recursive --files-only --format 'sp' \
      | LC_ALL=C sort >"${inventory_dir}/garage.txt"
    diff -u "${inventory_dir}/minio.txt" "${inventory_dir}/garage.txt"

    run_rclone check minio:langfuse garage:langfuse \
      --download --one-way --progress
    ;;
  stop-source)
    "${compose[@]}" stop langfuse-minio-migration
    ;;
  *)
    usage
    exit 2
    ;;
esac
