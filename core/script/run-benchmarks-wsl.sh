#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
MODULE_DIR="$ROOT_DIR/core"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="${1:-$ROOT_DIR/benchmark-runs/$TIMESTAMP}"

AERON_JAR="${AERON_JAR:-$HOME/.m2/repository/io/aeron/aeron-all/1.50.4/aeron-all-1.50.4.jar}"
AGRONA_JAR="${AGRONA_JAR:-$HOME/.m2/repository/org/agrona/agrona/2.4.0/agrona-2.4.0.jar}"
ASPROF="${ASPROF:-$HOME/.local/opt/async-profiler/bin/asprof}"

WARMUP_SECONDS="${WARMUP_SECONDS:-1}"
MEASURE_SECONDS="${MEASURE_SECONDS:-5}"
PROFILE_WARMUP_SECONDS="${PROFILE_WARMUP_SECONDS:-2}"
PROFILE_MEASURE_SECONDS="${PROFILE_MEASURE_SECONDS:-8}"
FRAGMENT_LIMIT="${FRAGMENT_LIMIT:-32}"
LISTENER_MAX_YIELDS="${LISTENER_MAX_YIELDS:-512}"
PUBLISHER_MAX_YIELDS="${PUBLISHER_MAX_YIELDS:-20000}"
WAITERS_INITIAL_CAPACITY="${WAITERS_INITIAL_CAPACITY:-1048576}"
WAITERS_LOAD_FACTOR="${WAITERS_LOAD_FACTOR:-0.75}"
CHANNELS_LIST="${CHANNELS_LIST:-1 4 8}"

mkdir -p "$OUT_DIR"

mvn -q -pl core -am -DskipTests compile

CP="$MODULE_DIR/target/classes:$AERON_JAR:$AGRONA_JAR"
JAVA_OPTS=(--add-exports java.base/jdk.internal.misc=ALL-UNNAMED -cp "$CP")
BENCH_ARGS=("$WARMUP_SECONDS" "$MEASURE_SECONDS" "$FRAGMENT_LIMIT" "$LISTENER_MAX_YIELDS" "$PUBLISHER_MAX_YIELDS" "$WAITERS_INITIAL_CAPACITY" "$WAITERS_LOAD_FACTOR")

cat > "$OUT_DIR/run-config.txt" <<EOF
root=$ROOT_DIR
module=$MODULE_DIR
warmupSeconds=$WARMUP_SECONDS
measureSeconds=$MEASURE_SECONDS
profileWarmupSeconds=$PROFILE_WARMUP_SECONDS
profileMeasureSeconds=$PROFILE_MEASURE_SECONDS
fragmentLimit=$FRAGMENT_LIMIT
listenerMaxYields=$LISTENER_MAX_YIELDS
publisherMaxYields=$PUBLISHER_MAX_YIELDS
waitersInitialCapacity=$WAITERS_INITIAL_CAPACITY
waitersLoadFactor=$WAITERS_LOAD_FACTOR
channelsList=$CHANNELS_LIST
asyncProfiler=$ASPROF
EOF

run_profiler() {
  local channels="$1"
  local event="$2"
  local output_file="$3"
  local log_file="$4"
  java "${JAVA_OPTS[@]}" ru.pathcreator.pyc.rpc.core.benchmark.RpcBenchmarkMain \
    "$channels" "$PROFILE_WARMUP_SECONDS" "$PROFILE_MEASURE_SECONDS" \
    "$FRAGMENT_LIMIT" "$LISTENER_MAX_YIELDS" "$PUBLISHER_MAX_YIELDS" \
    "$WAITERS_INITIAL_CAPACITY" "$WAITERS_LOAD_FACTOR" > "$log_file" 2>&1 &
  local app_pid=$!
  sleep 1
  "$ASPROF" collect -d "$PROFILE_MEASURE_SECONDS" -e "$event" -o flamegraph -f "$output_file" "$app_pid"
  wait "$app_pid"
}

for channels in $CHANNELS_LIST; do
  channel_dir="$OUT_DIR/channels-$channels"
  mkdir -p "$channel_dir"

  java "${JAVA_OPTS[@]}" ru.pathcreator.pyc.rpc.core.benchmark.RpcBenchmarkMain \
    "$channels" "${BENCH_ARGS[@]}" | tee "$channel_dir/summary.txt"

  if [[ -x "$ASPROF" ]]; then
    run_profiler "$channels" cpu "$channel_dir/async-cpu.html" "$channel_dir/async-cpu-run.log"
    run_profiler "$channels" alloc "$channel_dir/async-alloc.html" "$channel_dir/async-alloc-run.log"
  else
    printf 'async-profiler not found at %s\n' "$ASPROF" > "$channel_dir/async-profiler-missing.txt"
  fi

  java -XX:StartFlightRecording=filename="$channel_dir/run.jfr",duration=$((PROFILE_WARMUP_SECONDS + PROFILE_MEASURE_SECONDS))s,settings=profile \
    "${JAVA_OPTS[@]}" ru.pathcreator.pyc.rpc.core.benchmark.RpcBenchmarkMain \
    "$channels" "$PROFILE_WARMUP_SECONDS" "$PROFILE_MEASURE_SECONDS" \
    "$FRAGMENT_LIMIT" "$LISTENER_MAX_YIELDS" "$PUBLISHER_MAX_YIELDS" \
    "$WAITERS_INITIAL_CAPACITY" "$WAITERS_LOAD_FACTOR" > "$channel_dir/jfr-run.log" 2>&1
done

printf 'results=%s\n' "$OUT_DIR"
