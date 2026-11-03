#!/bin/bash
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export GOMODCACHE="$TASK_ROOT/.tools/go-mod"
export GOCACHE="$TASK_ROOT/.tools/go-cache"
export GOTMPDIR="$TASK_ROOT/.tools/go-tmp"
mkdir -p "$GOMODCACHE" "$GOCACHE" "$GOTMPDIR"
TASK_CLASSES="$(mktemp -d /private/tmp/simple-node-tests.XXXXXX)"
trap 'rm -rf "$TASK_CLASSES"' EXIT
/opt/homebrew/opt/openjdk@17/bin/javac -d "$TASK_CLASSES" "$TASK_ROOT/app/src/main/java/cn/hqq/simplenode/NodeState.java" "$TASK_ROOT/app/src/main/java/cn/hqq/simplenode/ProfileTransaction.java" "$TASK_ROOT/tests/NodeStateTest.java" "$TASK_ROOT/tests/ProfileTransactionTest.java"
/opt/homebrew/opt/openjdk@17/bin/java -cp "$TASK_CLASSES" NodeStateTest
/opt/homebrew/opt/openjdk@17/bin/java -cp "$TASK_CLASSES" ProfileTransactionTest
/opt/homebrew/opt/openjdk@17/bin/javac -d "$TASK_CLASSES" "$TASK_ROOT/app/src/main/java/cn/hqq/simplenode/LatencyBatch.java" "$TASK_ROOT/tests/LatencyBatchTest.java"
/opt/homebrew/opt/openjdk@17/bin/java -cp "$TASK_CLASSES" LatencyBatchTest
cd "$TASK_ROOT/native"
go test -mod=mod -tags with_gvisor .
