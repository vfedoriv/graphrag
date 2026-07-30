#!/usr/bin/env bash
set -euo pipefail

run_name="${1:-$(date -u +%Y%m%dT%H%M%SZ)}"
result_dir="target/test-performance/${run_name}"
report_dir="target/surefire-reports"
mkdir -p "${result_dir}"
find "${report_dir}" -maxdepth 1 -type f -delete 2>/dev/null || true

started_epoch="$(date +%s)"
started_monotonic="$(date +%s)"
set +e
./mvnw test 2>&1 | tee "${result_dir}/maven.log"
maven_status="${PIPESTATUS[0]}"
set -e
finished_monotonic="$(date +%s)"
wall_seconds="$((finished_monotonic - started_monotonic))"
if [[ "${maven_status}" -ne 0 ]]; then
    echo "Maven test run failed; log preserved at ${result_dir}/maven.log" >&2
    exit "${maven_status}"
fi

python3 scripts/test-suite-performance-report.py \
    --reports "${report_dir}" \
    --log "${result_dir}/maven.log" \
    --started-at "${started_epoch}" \
    --wall-seconds "${wall_seconds}" \
    --output "${result_dir}/report.json"
