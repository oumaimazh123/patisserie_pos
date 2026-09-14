#!/usr/bin/env bash
set -euo pipefail

application="${1:-/opt/patisserie-pos/bin/PATISSERIE_POS}"
screenshot="${2:-/tmp/patisserie-pos-smoke.png}"
display_number="${POS_TEST_DISPLAY:-:97}"

test -x "$application"
command -v Xvfb >/dev/null
command -v import >/dev/null

Xvfb "$display_number" -screen 0 1280x800x24 -nolisten tcp >/tmp/general-pos-xvfb.log 2>&1 &
xvfb_pid=$!
app_pid=""
cleanup() {
    if [[ -n "$app_pid" ]]; then kill "$app_pid" 2>/dev/null || true; fi
    kill "$xvfb_pid" 2>/dev/null || true
    wait "$app_pid" 2>/dev/null || true
    wait "$xvfb_pid" 2>/dev/null || true
}
trap cleanup EXIT

sleep 1
DISPLAY="$display_number" LIBGL_ALWAYS_SOFTWARE=1 SKIKO_RENDER_API=SOFTWARE "$application" >/tmp/general-pos-smoke.log 2>&1 &
app_pid=$!
sleep 8
kill -0 "$app_pid"
DISPLAY="$display_number" import -window root "$screenshot"
test -s "$screenshot"
echo "Packaged application stayed alive and screenshot was written to $screenshot"
