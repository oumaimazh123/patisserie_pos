#!/usr/bin/env bash
set -e

USER_HOME="/home/ubuntu"
if [ ! -d "$USER_HOME" ]; then
    USER_HOME="/root"
fi

DATA_DIR="$USER_HOME/.local/share/general-pos"
CONFIG_DIR="$USER_HOME/.config/general-pos"

mkdir -p "$DATA_DIR/backups" "$DATA_DIR/images" "$DATA_DIR/logs" "$CONFIG_DIR/secure"

# Write mock existing user data to simulate previous 1.2.1 usage
echo "MOCK_DATABASE_DATA_V121" > "$DATA_DIR/pos.db"
echo "MOCK_LICENSE_KEY" > "$CONFIG_DIR/secure/license.dat"
echo "MOCK_USER_IMAGE" > "$DATA_DIR/images/test.png"

echo "=== PRE-INSTALL CHECK ==="
ls -la "$DATA_DIR"
ls -la "$CONFIG_DIR/secure"

DEB_PATH="/mnt/c/Users/zakar/Desktop/cafeRestaurantPOS/POS_General/desktopApp/build/compose/binaries/main/deb/general-pos_1.2.3_amd64.deb"

echo "=== SIMULATING UPGRADE / REINSTALL ==="
apt-get install -y --reinstall "$DEB_PATH"

echo "=== POST-INSTALL DATA PRESERVATION CHECK ==="
if [ "$(cat "$DATA_DIR/pos.db")" = "MOCK_DATABASE_DATA_V121" ]; then
    echo "SUCCESS: pos.db PRESERVED"
else
    echo "FAILURE: pos.db modified or deleted"
    exit 1
fi

if [ "$(cat "$CONFIG_DIR/secure/license.dat")" = "MOCK_LICENSE_KEY" ]; then
    echo "SUCCESS: license.dat PRESERVED"
else
    echo "FAILURE: license.dat modified or deleted"
    exit 1
fi

if [ "$(cat "$DATA_DIR/images/test.png")" = "MOCK_USER_IMAGE" ]; then
    echo "SUCCESS: test.png PRESERVED"
else
    echo "FAILURE: test.png modified or deleted"
    exit 1
fi

echo "=== ALL USER DATA PRESERVED DURING UPGRADE ==="
