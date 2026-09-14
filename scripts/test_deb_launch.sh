#!/usr/bin/env bash
set -euo pipefail

APP="/opt/patisserie-pos/bin/PATISSERIE_POS"
test -x "$APP"

display_number=":98"
Xvfb "$display_number" -screen 0 1280x800x24 -nolisten tcp >/tmp/pos-xvfb.log 2>&1 &
xvfb_pid=$!

app_pid=""
cleanup() {
    if [[ -n "$app_pid" ]]; then
        kill "$app_pid" 2>/dev/null || true
    fi
    kill "$xvfb_pid" 2>/dev/null || true
}
trap cleanup EXIT

sleep 2
DISPLAY="$display_number" LIBGL_ALWAYS_SOFTWARE=1 SKIKO_RENDER_API=SOFTWARE "$APP" >/tmp/pos-run.log 2>&1 &
app_pid=$!
echo "Application launched with PID: $app_pid"

sleep 8

if kill -0 "$app_pid" 2>/dev/null; then
    echo "SUCCESS: PATISSERIE_POS is RUNNING on Ubuntu X11/Xvfb!"
    if command -v import >/dev/null; then
        DISPLAY="$display_number" import -window root /tmp/patisserie-pos-run.png 2>/dev/null || true
        echo "Screenshot captured to /tmp/patisserie-pos-run.png"
    fi
else
    echo "ERROR: Process died. Logs below:"
    cat /tmp/pos-run.log
    exit 1
fi
