# PATISSERIE_POS

Système de caisse enregistreuse et gestion commerciale hors-ligne (Offline-First) spécialement conçu pour les pâtisseries, boulangeries et salons de thé.

## Présentation

**PATISSERIE_POS** est une solution complète, moderne et sécurisée pour la gestion au quotidien des points de vente de pâtisserie :
- **Ventes rapides & comptoir** : encaissement fluide en espèces ou carte bancaire.
- **Précommandes & gâteaux personnalisés** : gestion des acomptes, date/heure de retrait et suivi du statut de préparation.
- **Gestion de catalogue** : catégories, sous-catégories et produits avec prise en charge des photos.
- **Sécurité renforcée** : hachage PBKDF2 avec sel cryptographique et protection anti-brute force.
- **Architecture Offline-First** : persistance locale SQLite v8, sauvegardes compressées et rapports de clôture de caisse ESC/POS.

## Modules

- `desktopApp` : Application Desktop Compose Multiplatform (Windows & Linux).
- `sharedLogic` : Logique métier partagée, cas d'utilisation et validation des règles métiers.
- `app` : Application Android pour terminaux de caisse mobiles et tablettes.

