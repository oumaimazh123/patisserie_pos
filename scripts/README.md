# PATISSERIE_POS Data Management & Cleanup Scripts

This directory contains data cleanup and reset scripts for PATISSERIE_POS installations on **Linux (Ubuntu/Debian)** and **Windows**.

---

## ??? Modes Available

### **Mode 1 (Recommended): Clean Sales Data Only**
* **What it does**: Clears historical sales, orders, order line items, payments, register sessions, and cash movements.
* **What it preserves**: Products, Categories, Cashier accounts, Store settings, Tax configuration, and License.
* **Use case**: Ideal when transitioning from testing/training to live store operations without re-entering your product catalog.

### **Mode 2: Complete Factory Reset**
* **What it does**: Completely wipes the SQLite database, images, logs, and temporary caches.
* **What it preserves**: None (full factory reset).
* **Use case**: Prepares a fresh installation starting at the initial setup wizard (Setup screen).

> [!NOTE]
> **In both modes, the script automatically creates a timestamped safety backup of your data directory before making any changes.**

---

## ?? How to Run

### **On Linux (Ubuntu / Debian / POS Terminals)**:

```bash
chmod +x scripts/clean_pos_data.sh
./scripts/clean_pos_data.sh
```

### **On Windows (PowerShell)**:

```powershell
# Interactive mode (prompts for mode 1 or 2):
.\scripts\clean_pos_data.ps1

# Direct execution for Mode 1:
.\scripts\clean_pos_data.ps1 -Mode 1

# Direct execution for Mode 2:
.\scripts\clean_pos_data.ps1 -Mode 2
```
