#!/usr/bin/env bash
# Usage: ./diagnose-hang.sh
# Run this IMMEDIATELY when a chat turn hangs (before the 5-min timeout).
# It finds the goosed process and captures thread state + stack traces.

set -euo pipefail

OUTDIR="/tmp/goosed-diag-$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUTDIR"

# Find goosed PID
PID=$(pgrep -f "goosed" | head -1 || true)
if [ -z "$PID" ]; then
    echo "ERROR: No goosed process found"
    exit 1
fi
echo "Found goosed PID: $PID"

# 1. macOS process sample (3 seconds, high frequency)
echo "Capturing process sample (3s)..."
sample "$PID" 3 -file "$OUTDIR/sample.txt" 2>/dev/null &

# 2. lsof to see open file descriptors and network connections
echo "Capturing lsof..."
lsof -p "$PID" > "$OUTDIR/lsof.txt" 2>/dev/null || true

# 3. Wait for sample to complete
wait

# 4. Extract key info from sample
echo ""
echo "=== ANALYSIS ==="
echo ""

# Check if any thread is actively running (not parked)
ACTIVE=$(grep -c "goose\|rmcp\|list_tools\|fetch_all\|prepare_tools\|tokio::runtime::task" "$OUTDIR/sample.txt" 2>/dev/null || echo "0")
PARKED=$(grep -c "parking_lot.*condvar\|__psynch_cvwait" "$OUTDIR/sample.txt" 2>/dev/null || echo "0")

echo "Active goose/rmcp frames: $ACTIVE"
echo "Parked threads: $PARKED"
echo ""

# Show any goose-specific stack frames
echo "=== Goose stack frames ==="
grep -E "goose::|rmcp::|list_tools|fetch_all|prepare_tools|get_all_tools|tools_cache|extension_manager" "$OUTDIR/sample.txt" 2>/dev/null | head -30 || echo "(none found)"

echo ""
echo "=== Network connections ==="
grep -E "TCP|UDP" "$OUTDIR/lsof.txt" 2>/dev/null | head -20 || echo "(none)"

echo ""
echo "Full output saved to: $OUTDIR/"
echo "  sample.txt  - thread stack traces"
echo "  lsof.txt    - file descriptors and connections"
