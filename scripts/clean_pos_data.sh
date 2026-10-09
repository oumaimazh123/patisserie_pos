#!/usr/bin/env bash
# ==============================================================================
# PATISSERIE_POS Data Cleaner Script (Linux / Ubuntu)
# ==============================================================================
# Mode 1 (Recommended) : Clean Sales Data Only
#   - Cleans orders, payments, register sessions, and cash movements.
#   - Preserves products, categories, users/cashiers, settings, and license.
# Mode 2               : Complete Factory Reset
#   - Cleans all database tables, images, cache, and logs.
#   - Resets the application back to the initial setup wizard.
# NOTE: In both modes, a timestamped backup is automatically created before
# modifying any data.
# ==============================================================================

set -e

COLOR_CYAN='\033[0;36m'
COLOR_GREEN='\033[0;32m'
COLOR_YELLOW='\033[1;33m'
COLOR_RED='\033[0;31m'
COLOR_RESET='\033[0m'

echo -e "${COLOR_CYAN}======================================================${COLOR_RESET}"
echo -e "${COLOR_CYAN}       PATISSERIE_POS - Data Management & Cleanup     ${COLOR_RESET}"
echo -e "${COLOR_CYAN}======================================================${COLOR_RESET}"

DATA_DIR="$HOME/.local/share/patisserie-pos"
CONFIG_DIR="$HOME/.config/patisserie-pos"
CACHE_DIR="$HOME/.cache/patisserie-pos"
TEMP_DIR="/tmp/patisserie-pos"
DB_FILE="$DATA_DIR/pos.db"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="$HOME/patisserie_pos_backup_$TIMESTAMP"

# 1. Check if application is running and terminate safely
echo -e "\n${COLOR_YELLOW}Verifying running processes...${COLOR_RESET}"
if pgrep -f "patisserie-pos" > /dev/null || pgrep -f "PATISSERIE_POS" > /dev/null; then
    echo "Stopping application process..."
    killall "PATISSERIE_POS" 2>/dev/null || true
    pkill -f "patisserie-pos" 2>/dev/null || true
    sleep 1
fi

if [ ! -d "$DATA_DIR" ] && [ ! -f "$DB_FILE" ]; then
    echo -e "${COLOR_RED}No PATISSERIE_POS data directory found at $DATA_DIR.${COLOR_RESET}"
    exit 0
fi

# 2. Select Cleanup Mode
echo -e "\nPlease select cleanup mode:"
echo -e "  ${COLOR_GREEN}[1] Mode 1 (Recommended) : Clean Sales Data Only${COLOR_RESET}"
echo -e "      (Cleans sales, payments, sessions, and movements. Keeps products, categories, users, settings & license)"
echo -e "  ${COLOR_RED}[2] Mode 2               : Complete Factory Reset${COLOR_RESET}"
echo -e "      (Full wipe: resets everything back to the initial Setup wizard)"
echo -e "  [3] Cancel & Exit"
echo ""
read -p "Enter choice [1-3] (default: 1): " CHOICE
CHOICE=${CHOICE:-1}

if [ "$CHOICE" == "3" ]; then
    echo "Operation cancelled."
    exit 0
fi

# 3. Create Automatic Timestamped Safety Backup
echo -e "\n${COLOR_YELLOW}?? Creating timestamped backup before modification...${COLOR_RESET}"
mkdir -p "$BACKUP_DIR"
if [ -d "$DATA_DIR" ]; then
    cp -r "$DATA_DIR" "$BACKUP_DIR/" 2>/dev/null || true
fi
if [ -d "$CONFIG_DIR" ]; then
    cp -r "$CONFIG_DIR" "$BACKUP_DIR/" 2>/dev/null || true
fi
echo -e "${COLOR_GREEN}? Safety backup created at: $BACKUP_DIR${COLOR_RESET}"

# 4. Execute Selected Mode
if [ "$CHOICE" == "1" ]; then
    echo -e "\n${COLOR_CYAN}--- Executing Mode 1: Clean Sales Data Only ---${COLOR_RESET}"
    if [ -f "$DB_FILE" ]; then
        if command -v sqlite3 >/dev/null 2>&1; then
            sqlite3 "$DB_FILE" << 'EOF'
PRAGMA foreign_keys = OFF;
DELETE FROM payments;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cash_movements;
DELETE FROM register_sessions;
DELETE FROM audit_logs;
VACUUM;
PRAGMA foreign_keys = ON;
EOF
            echo -e "${COLOR_GREEN}? Sales data, payments, orders, and sessions cleared successfully from SQLite.${COLOR_RESET}"
        else
            echo -e "${COLOR_YELLOW}sqlite3 CLI not found. Using python3 sqlite3 module...${COLOR_RESET}"
            python3 -c "
import sqlite3
con = sqlite3.connect('$DB_FILE')
cur = con.cursor()
cur.execute('PRAGMA foreign_keys = OFF;')
cur.execute('DELETE FROM payments;')
cur.execute('DELETE FROM order_items;')
cur.execute('DELETE FROM orders;')
cur.execute('DELETE FROM cash_movements;')
cur.execute('DELETE FROM register_sessions;')
cur.execute('DELETE FROM audit_logs;')
con.commit()
cur.execute('VACUUM;')
con.close()
"
            echo -e "${COLOR_GREEN}? Sales data cleared successfully.${COLOR_RESET}"
        fi
    else
        echo -e "${COLOR_YELLOW}No pos.db file found to clean.${COLOR_RESET}"
    fi

    # Clean temporary cache
    rm -rf "$CACHE_DIR"/* 2>/dev/null || true
    rm -rf "$TEMP_DIR" 2>/dev/null || true

    echo -e "\n${COLOR_GREEN}======================================================${COLOR_RESET}"
    echo -e "${COLOR_GREEN}? Mode 1 completed successfully!${COLOR_RESET}"
    echo -e "  - Catalog (Products, Categories) : PRESERVED"
    echo -e "  - Users & PINs                   : PRESERVED"
    echo -e "  - Store Settings & Receipt Info  : PRESERVED"
    echo -e "  - License Configuration          : PRESERVED"
    echo -e "  - Sales, Orders & Sessions       : RESET TO 0"
    echo -e "${COLOR_GREEN}======================================================${COLOR_RESET}"

elif [ "$CHOICE" == "2" ]; then
    echo -e "\n${COLOR_RED}--- Executing Mode 2: Complete Factory Reset ---${COLOR_RESET}"
    rm -f "$DATA_DIR/pos.db" "$DATA_DIR/pos.db-wal" "$DATA_DIR/pos.db-shm"
    rm -rf "$DATA_DIR/images"/* 2>/dev/null || true
    rm -rf "$DATA_DIR/logs"/* 2>/dev/null || true
    rm -rf "$CACHE_DIR"/* 2>/dev/null || true
    rm -rf "$TEMP_DIR" 2>/dev/null || true

    # Clean legacy directories if present
    rm -rf "$HOME/.local/share/caferestaurantpos" 2>/dev/null || true
    rm -rf "$HOME/.config/caferestaurantpos" 2>/dev/null || true

    echo -e "\n${COLOR_GREEN}======================================================${COLOR_RESET}"
    echo -e "${COLOR_GREEN}? Mode 2 Factory Reset completed successfully!${COLOR_RESET}"
    echo -e "  The application will start fresh on the Initial Setup wizard."
    echo -e "${COLOR_GREEN}======================================================${COLOR_RESET}"
fi
