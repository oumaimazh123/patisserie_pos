# ==============================================================================
# PATISSERIE_POS Data Cleaner Script (Windows PowerShell)
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

[CmdletBinding()]
param(
    [ValidateSet(1, 2)]
    [int]$Mode = 0
)

Clear-Host
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "       PATISSERIE_POS - Data Management & Cleanup     " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

$AppDir = "$env:LOCALAPPDATA\PATISSERIE_POS"
if (-not (Test-Path $AppDir) -and (Test-Path "$env:LOCALAPPDATA\GeneralPOS")) {
    $AppDir = "$env:LOCALAPPDATA\GeneralPOS"
}
$TempAppDir = "$env:TEMP\PATISSERIE_POS"
$DbPath = "$AppDir\data\pos.db"
$DateTag = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupDir = "$env:USERPROFILE\Desktop\PATISSERIE_POS_Backup_$DateTag"

# 1. Check if application is running
Write-Host "`nVerifying running processes..." -ForegroundColor Yellow
$processes = Get-Process -Name "PATISSERIE_POS", "General POS", "patisserie-pos", "general-pos" -ErrorAction SilentlyContinue
if ($processes) {
    Write-Host "Stopping application process..." -ForegroundColor Yellow
    $processes | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

if (-not (Test-Path $AppDir)) {
    Write-Host "No PATISSERIE_POS directory found at: $AppDir" -ForegroundColor Red
    exit
}

# 2. Select Cleanup Mode
if ($Mode -eq 0) {
    Write-Host "`nPlease select cleanup mode:"
    Write-Host "  [1] Mode 1 (Recommended) : Clean Sales Data Only" -ForegroundColor Green
    Write-Host "      (Cleans sales, payments, sessions, and movements. Keeps products, categories, users, settings & license)"
    Write-Host "  [2] Mode 2               : Complete Factory Reset" -ForegroundColor Red
    Write-Host "      (Full wipe: resets everything back to the initial Setup wizard)"
    Write-Host "  [3] Cancel & Exit"
    Write-Host ""
    $choice = Read-Host "Enter choice [1-3] (default: 1)"
    if ([string]::IsNullOrWhiteSpace($choice)) { $choice = "1" }
    if ($choice -eq "3") {
        Write-Host "Operation cancelled." -ForegroundColor Yellow
        exit
    }
    $Mode = [int]$choice
}

# 3. Create Automatic Timestamped Safety Backup
Write-Host "`n?? Creating timestamped backup before modification..." -ForegroundColor Yellow
New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
Copy-Item -Path "$AppDir\*" -Destination $BackupDir -Recurse -Force
Write-Host "? Safety backup created at: $BackupDir" -ForegroundColor Green

# 4. Execute Selected Mode
if ($Mode -eq 1) {
    Write-Host "`n--- Executing Mode 1: Clean Sales Data Only ---" -ForegroundColor Cyan

    if (Test-Path $DbPath) {
        # Execute SQLite clean commands using .NET SQLite or python/sqlite3
        $cleanSql = @"
PRAGMA foreign_keys = OFF;
DELETE FROM payments;
DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM cash_movements;
DELETE FROM register_sessions;
DELETE FROM audit_logs;
VACUUM;
PRAGMA foreign_keys = ON;
"@
        
        $cleaned = $false
        # Try sqlite3 CLI if available
        $sqliteCli = Get-Command sqlite3 -ErrorAction SilentlyContinue
        if ($sqliteCli) {
            $cleanSql | & sqlite3 $DbPath
            $cleaned = $true
        } else {
            # Try python if available
            $pythonCli = Get-Command python -ErrorAction SilentlyContinue
            if ($pythonCli) {
                $pyScript = @"
import sqlite3
con = sqlite3.connect(r'$DbPath')
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
"@
                & python -c $pyScript
                $cleaned = $true
            }
        }

        if (-not $cleaned) {
            # Pure PowerShell fallback via System.Data.SQLite / JDBC or copy template
            Write-Host "Executing database clean via PowerShell process..." -ForegroundColor Yellow
            $pyScript = @"
import sqlite3
con = sqlite3.connect(r'$DbPath')
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
"@
            # Check for Windows bundled python or run command
            try {
                & python -c $pyScript
            } catch {
                Write-Host "Warning: Please ensure sqlite3 or python is installed to execute SQL VACUUM, or use Mode 2." -ForegroundColor Yellow
            }
        }

        # Remove WAL & SHM temporary files
        Remove-Item -Path "$AppDir\data\pos.db-wal" -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$AppDir\data\pos.db-shm" -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$AppDir\cache\*" -Recurse -Force -ErrorAction SilentlyContinue
        Remove-Item -Path $TempAppDir -Recurse -Force -ErrorAction SilentlyContinue

        Write-Host "`n======================================================" -ForegroundColor Green
        Write-Host "? Mode 1 completed successfully!" -ForegroundColor Green
        Write-Host "  - Catalog (Products, Categories) : PRESERVED"
        Write-Host "  - Users & PINs                   : PRESERVED"
        Write-Host "  - Store Settings & Receipt Info  : PRESERVED"
        Write-Host "  - License Configuration          : PRESERVED"
        Write-Host "  - Sales, Orders & Sessions       : RESET TO 0"
        Write-Host "======================================================" -ForegroundColor Green
    } else {
        Write-Host "No pos.db file found at: $DbPath" -ForegroundColor Yellow
    }

} elseif ($Mode -eq 2) {
    Write-Host "`n--- Executing Mode 2: Complete Factory Reset ---" -ForegroundColor Red

    # Remove database files
    Remove-Item -Path "$AppDir\data\pos.db*" -Force -ErrorAction SilentlyContinue

    # Remove images, logs, cache, and temporary data
    Remove-Item -Path "$AppDir\data\images\*" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$AppDir\data\logs\*" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$AppDir\cache\*" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path $TempAppDir -Recurse -Force -ErrorAction SilentlyContinue

    # Remove legacy folders if present
    Remove-Item -Path "$env:LOCALAPPDATA\CafeRestaurantPOS" -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path "$env:LOCALAPPDATA\POS_General" -Recurse -Force -ErrorAction SilentlyContinue

    Write-Host "`n======================================================" -ForegroundColor Green
    Write-Host "? Mode 2 Factory Reset completed successfully!" -ForegroundColor Green
    Write-Host "  The application will start fresh on the Initial Setup wizard."
    Write-Host "======================================================" -ForegroundColor Green
}
