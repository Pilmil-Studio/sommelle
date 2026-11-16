# Feuille de route — Sommelle

> Dernière mise à jour : novembre 2026. Architecture : [documentation/architecture.md](documentation/architecture.md).

## 1. Fonctionnalités à venir
- **v1.x** :
  - Relecture clinique du contenu psycho-éducatif et des règles TCC-I par un médecin du sommeil.
  - Intégration de `pilmil-core` (`BackupVault` `.pilmil`) pour les sauvegardes locales chiffrées portables entre appareils.
  - Passage en production sur Google Play (après 12 testeurs × 14 jours) et soumission F-Droid.

## 2. Aspect visuel & UX
- Vérification des contrastes et de la lisibilité nocturne sur les 3 modes (clair, sombre, automatique/personnalisable).
- Maintien des captures d'écran Paparazzi à jour pour les fiches stores (6 langues).

## 3. Qualité de code & sécurité
- Sauvegarde Android : activée dans le manifeste, et c'est l'agent de sauvegarde qui applique le choix de la personne. La copie dans le compte Google est désactivée par défaut, activable dans les réglages et jamais faite sans chiffrement de bout en bout ; le transfert direct vers un nouveau téléphone reste permis.
- Évaluer le chiffrement au repos de la base Room locale (SQLCipher) pour les données d'agenda de sommeil.
- Exécution de `test` et `:app:lintDebug` en CI ; à compléter par `ktlint` / `detekt`.

## 4. Feuille de route iOS
- **État actuel** : 100 % Android, mais `core:model`, `core:protocol` et `core:presentation` sont déjà en Kotlin pur.
- **Étapes** :
  1. Monter Room 2.6.1 → **Room 2.7 KMP** (`sqlite-bundled`) et déplacer le schéma en `commonMain`.
  2. Implémenter les rappels via `UNUserNotificationCenter` et réécrire l'export PDF avec `UIGraphicsPDFRenderer`.
  3. Ajouter la coque Xcode `iosApp/` et la compilation simulateur en CI macOS.
