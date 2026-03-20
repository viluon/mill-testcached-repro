#!/usr/bin/env bash
set -uo pipefail

# Reproducer for Mill testCached sandbox-escape bug.
# Runs clean + __.testCached in a loop until the flaky error appears.
# Usually triggers within 1-10 iterations.

MAX_ITERATIONS=${1:-50}
echo "Running up to $MAX_ITERATIONS iterations to reproduce testCached bug..."
echo "=================================================================="

for i in $(seq 1 "$MAX_ITERATIONS"); do
  echo ""
  echo "--- Iteration $i/$MAX_ITERATIONS ---"
  ./mill clean 2>&1 | tail -1
  output=$(./mill __.testCached 2>&1)
  exit_code=$?

  if echo "$output" | grep -q "not allowed during execution of"; then
    echo "BUG REPRODUCED on iteration $i!"
    echo ""
    echo "$output" | grep -A2 "not allowed during execution of"
    echo ""
    echo "Full output:"
    echo "$output"
    exit 1
  fi

  if [ $exit_code -ne 0 ]; then
    echo "Failed with exit code $exit_code (but not the sandbox bug):"
    echo "$output" | tail -5
  else
    echo "OK (all tests passed)"
  fi
done

echo ""
echo "Bug did not reproduce in $MAX_ITERATIONS iterations."
echo "Try increasing the iteration count or adding more modules to build.mill."
exit 0
