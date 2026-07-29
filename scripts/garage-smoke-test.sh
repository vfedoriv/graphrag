#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(
  docker compose
  -f "${project_root}/compose.yaml"
  -f "${project_root}/compose.garage-smoke.yaml"
  --profile langfuse
  --profile garage-smoke
)
object_path="garage:langfuse/smoke/garage-smoke-test.txt"
payload="garage-smoke-$(date -u +%Y%m%dT%H%M%SZ)-$$"

"${compose[@]}" up -d --build langfuse-garage
"${compose[@]}" run --rm langfuse-garage-init

printf '%s' "$payload" \
  | "${compose[@]}" run --rm -T langfuse-garage-smoke rcat "$object_path"

downloaded="$("${compose[@]}" run --rm -T langfuse-garage-smoke cat "$object_path")"
test "$downloaded" = "$payload"
"${compose[@]}" run --rm -T langfuse-garage-smoke \
  lsf garage:langfuse/smoke --files-only \
  | grep -Fx 'garage-smoke-test.txt'

"${compose[@]}" up -d --force-recreate --wait langfuse-garage
"${compose[@]}" run --rm langfuse-garage-init

downloaded="$("${compose[@]}" run --rm -T langfuse-garage-smoke cat "$object_path")"
test "$downloaded" = "$payload"

"${compose[@]}" run --rm -T langfuse-garage-smoke deletefile "$object_path"
if "${compose[@]}" run --rm -T langfuse-garage-smoke \
  lsf garage:langfuse/smoke --files-only \
  | grep -Fx 'garage-smoke-test.txt'; then
  echo "Garage smoke object still exists after delete" >&2
  exit 1
fi

echo "Garage initialization, authorization, put/get/list/delete, and persistence checks passed."
