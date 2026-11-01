#!/usr/bin/env bash
# Publishes test totals and build errors as workflow annotations so results are
# visible without opening the raw logs.
# Usage: summarize.sh <build-log> <test-results-dir> [<test-results-dir> ...]
set +e
LOG="${1:-build.log}"
shift 2>/dev/null
RES_DIRS=("$@")
[ "${#RES_DIRS[@]}" -eq 0 ] && RES_DIRS=(core/docx/build/test-results/test)
RES_FILES=()
for d in "${RES_DIRS[@]}"; do RES_FILES+=("$d"/*.xml); done

encode() {
  local s="$1"
  s="${s//'%'/'%25'}"
  s="${s//$'\r'/'%0D'}"
  s="${s//$'\n'/'%0A'}"
  printf '%s' "$s"
}

tests=0; failures=0; errors=0; skipped=0; suites=0
for f in "${RES_FILES[@]}"; do
  [ -f "$f" ] || continue
  head=$(grep -m1 '<testsuite ' "$f")
  t=$(sed -n 's/.* tests="\([0-9]*\)".*/\1/p' <<<"$head")
  s=$(sed -n 's/.* skipped="\([0-9]*\)".*/\1/p' <<<"$head")
  fl=$(sed -n 's/.* failures="\([0-9]*\)".*/\1/p' <<<"$head")
  er=$(sed -n 's/.* errors="\([0-9]*\)".*/\1/p' <<<"$head")
  tests=$((tests + ${t:-0})); skipped=$((skipped + ${s:-0}))
  failures=$((failures + ${fl:-0})); errors=$((errors + ${er:-0}))
  suites=$((suites + 1))
done
echo "::notice title=Test totals::suites=$suites tests=$tests failures=$failures errors=$errors skipped=$skipped"

if [ "$failures" -gt 0 ] || [ "$errors" -gt 0 ]; then
  detail=$(grep -h -B1 -A3 '<failure\|<error' "${RES_FILES[@]}" 2>/dev/null | head -60)
  echo "::error title=Failed tests::$(encode "$detail")"
fi

if [ -f "$LOG" ]; then
  problems=$(grep -E '^e: |What went wrong|FAILED$|error:' -A4 "$LOG" | head -80)
  if [ -n "$problems" ]; then
    echo "::error title=Build output::$(encode "$problems")"
  fi
fi
exit 0
