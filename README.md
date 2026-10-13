# PATISSERIE_POS

Système de caisse enregistreuse hors-ligne (offline-first) pour pâtisseries, boulangeries et commerces de bouche au Maroc.
Application de bureau **Compose Multiplatform** (Windows 10/11 en priorité, Linux en option) avec base SQLite locale.

Version actuelle : voir `pos.desktop.version` dans [`gradle.properties`](./gradle.properties) (1.2.9). Schéma de base de données : v12.

---

## Documentation

- **[`AGENTS.md`](./AGENTS.md)** — règles obligatoires pour les agents IA et les développeurs.
- **[`docs/context/00_index.md`](./docs/context/00_index.md)** — index de la documentation vérifiée contre le code :
  - `01` périmètre et état réel des fonctionnalités · `02` architecture et dépendances · `03` base de données ·
    `04` règles métier et invariants financiers · `05` écrans et routes · `06` impression, afficheur client, scanner ·
    `07` licence et sécurité · `08` packaging MSI, mise à jour sûre, CI · `09` tests · `10` problèmes connus.
- [`docs/CSV_IMPORT_GUIDE.md`](./docs/CSV_IMPORT_GUIDE.md) — import CSV du catalogue (format officiel à 16 colonnes).
- [`docs/WINDOWS_INSTALLER_DATA_SAFETY.md`](./docs/WINDOWS_INSTALLER_DATA_SAFETY.md) — protection des données lors des mises à jour MSI.
- [`docs/UBUNTU_RELEASE.md`](./docs/UBUNTU_RELEASE.md), [`docs/CI.md`](./docs/CI.md), [`docs/DESIGN.md`](./docs/DESIGN.md).
- `docs/archive/` — rapports et checklists **historiques** (non à jour).

---

## Fonctionnalités implémentées

- **Vente comptoir** tactile : panier, recherche par nom / code-barres, remise globale et par article (en points de base),
  TVA incluse (extraction par ligne), mise en attente / reprise des ventes.
- **Paiement** : espèces (calcul de la monnaie) et carte (confirmation manuelle du TPE). Les valeurs « carnet client » et
  « Mobile/QR » existent dans le code mais **ne sont pas proposées** dans l'interface.
- **Sessions de caisse** : ouverture, entrées/sorties d'espèces, clôture avec comptage, écart, ventilation tiroir/remise,
  rapport de clôture imprimable.
- **Catalogue** : catégories hiérarchiques, produits (SKU, code-barres, nom arabe, unité, image), import CSV, suppression douce.
- **Impression ESC/POS** 58/80 mm (CP858), ouverture du tiroir-caisse, anti-doublon d'impression.
- **Afficheur client VFD 20×2** (ESC/POS, CD5220, DSP800, UTC, texte simple) : total brut et net à payer.
- **Rapports** : ventes du jour, par produit / catégorie / caissier, historique des ventes et des sessions.
- **Sécurité** : rôles Propriétaire / Caissier, PIN PBKDF2, verrouillage temporaire après 5 échecs, PIN d'annulation
  optionnel, mot de passe de suppression de données, licence hors-ligne signée (ECDSA P-256) avec essai de 7 jours.
- **Sauvegarde** ZIP + restauration planifiée au redémarrage, sauvegarde automatique avant migration de schéma.
- **Langues** : français, arabe (RTL), anglais.

### Non implémenté (ne pas supposer)

Gestion de stock · précommandes complètes (acompte / solde / statut — structures de données uniquement) ·
remboursements / retours · paiement fractionné · approbation par PIN des retraits de caisse · numérotation séquentielle
des tickets · sauvegarde automatique avant suppression de données · synchronisation cloud.
Voir [`docs/context/10_known_issues_and_technical_debt.md`](./docs/context/10_known_issues_and_technical_debt.md).

---

## Structure du projet

- `sharedLogic` — règles métier, cas d'usage, protocole VFD (Kotlin Multiplatform, sans UI).
- `desktopApp` — application de bureau livrée : UI, SQLite/JDBC, impression, licence, packaging.
- `app` — ancien module Android (non livré).
- `scripts` — audit de sécurité du MSI, signature, nettoyage de données de test.
- `tools/license-generator` — générateur de licences hors-ligne (clé privée à ne jamais versionner).

## Données client (Windows)

- Données : `%LOCALAPPDATA%\PATISSERIE_POS\` (`data\pos.db`, `data\images`, `data\backups`, `data\secure\license.dat`, `config`).
- Binaires : `%LOCALAPPDATA%\PATISSERIE_POS_APP\` — l'installateur ne possède et ne touche jamais le dossier des données.

---

## Commandes (PowerShell)

```powershell
# Tests
.\gradlew.bat :sharedLogic:desktopTest
.\gradlew.bat :desktopApp:desktopTest

# Lancer en développement
.\gradlew.bat :desktopApp:run

# Installateur Windows
.\gradlew.bat :desktopApp:packageMsi

# Vérifier la sécurité des données de l'installateur
powershell -ExecutionPolicy Bypass -File .\scripts\Test-WindowsInstallerDataSafety.ps1 -MsiPath ".\desktopApp\build\compose\binaries\main\msi\PATISSERIE_POS-<version>.msi"
```
