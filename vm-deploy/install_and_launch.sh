#!/usr/bin/env bash
set -eu

PACKAGE="/mnt/hgfs/GeneralPOSDeploy/patisserie-pos_1.0.1_amd64.deb"
TARGET="$HOME/patisserie_pos_installed"
APP_BIN="$TARGET/opt/patisserie-pos/bin/PATISSERIE_POS"
STATUS="/mnt/hgfs/GeneralPOSDeploy/vm-launch-status.txt"

exec > >(tee "$STATUS") 2>&1

mkdir -p "$TARGET"
dpkg-deb -x "$PACKAGE" "$TARGET"

export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=$XDG_RUNTIME_DIR/bus}"
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
export DISPLAY="${DISPLAY:-:0}"

nohup "$APP_BIN" > /tmp/patisserie-pos_stdout.log 2>&1 &
APP_PID=$!
sleep 5

if kill -0 "$APP_PID" 2>/dev/null; then
    echo "SUCCESS: PATISSERIE_POS is running (PID $APP_PID)."
    echo "$APP_PID" > /tmp/patisserie-pos.pid
else
    echo "FAILED: PATISSERIE_POS exited during startup."
    cat /tmp/patisserie-pos_stdout.log
    exit 1
fi
