#!/usr/bin/env bash
set -e

# Discover active desktop user environment
export XDG_RUNTIME_DIR="/run/user/1000"
export DBUS_SESSION_BUS_ADDRESS="unix:path=/run/user/1000/bus"

# Check for Xauthority files
for auth in /run/user/1000/.mutter-Xwaylandauth* /run/user/1000/gdm/Xauthority /home/zakaria/.Xauthority; do
    if [ -f "$auth" ]; then
        export XAUTHORITY="$auth"
        break
    fi
done

# Try WAYLAND_DISPLAY and DISPLAY
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-0}"
export DISPLAY="${DISPLAY:-:0}"

APP_BIN="/home/zakaria/patisserie_pos_installed/opt/patisserie-pos/bin/PATISSERIE_POS"

echo "=== Launching PATISSERIE_POS on VM Desktop ==="
echo "DISPLAY: $DISPLAY"
echo "WAYLAND_DISPLAY: $WAYLAND_DISPLAY"
echo "XAUTHORITY: $XAUTHORITY"
echo "XDG_RUNTIME_DIR: $XDG_RUNTIME_DIR"

nohup "$APP_BIN" > /tmp/pos_stdout.log 2>&1 &
APP_PID=$!
echo "Application started with PID: $APP_PID"

sleep 3
if kill -0 "$APP_PID" 2>/dev/null; then
    echo "SUCCESS: PATISSERIE_POS is RUNNING on Ubuntu VM desktop!"
    ps aux | grep -i PATISSERIE_POS | grep -v grep
else
    echo "FAILED: Process exited. Logs below:"
    cat /tmp/pos_stdout.log
    echo "--- System / Application Log ---"
    cat /home/zakaria/.local/share/patisserie-pos/logs/application.log 2>/dev/null || true
fi

