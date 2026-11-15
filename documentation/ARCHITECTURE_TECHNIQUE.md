# Architecture & Choix Techniques (Complément) — Sommelle

Ce document complète `architecture.md` avec l'inventaire vérifié des modules, permissions et mécanismes techniques actuels.

## 1. Structure multi-modules

- **Cœur pur Kotlin (JVM 17, sans Android)** : `:core:model`, `:core:protocol` (algorithme TCC-I, calcul de fenêtre, dépistage), `:core:presentation`.
- **Socle Android** : `:core:data` (Room), `:core:designsystem` (typographie *Atkinson Hyperlegible*, thèmes jour et nuit ambre OLED).
- **Modules fonctionnels (`:feature:*`)** : `onboarding`, `home`, `diary`, `week`, `night`, `reminders`, `settings`, `export`, `cognitive`.
- **Application & outillage** : `:app` (`fr.sommelle`), `:screenshots` (Paparazzi), `pilmil-core`.

---

## 2. Manifeste, permissions et confidentialité

Dans `AndroidManifest.xml` :
- **Permissions** : `POST_NOTIFICATIONS` et `VIBRATE` (fusionnée depuis `pilmil-core:core-platform`).
- **Aucune permission `INTERNET`**.
- **Sauvegarde OS** : `android:allowBackup="false"` et `data_extraction_rules.xml` bloquant toute sauvegarde cloud tout en autorisant le transfert direct appareil à appareil.
- **Composants** : `MainActivity`, `SommelleWidget`, `FileProvider` (partage local du PDF/CSV).

---

## 3. Choix techniques vérifiés dans le code

- **Base de données** : Room 2.6.1 (`sommelle.db`, version 2), stockée dans le bac à sable privé Android (sans SQLCipher actuellement).
- **Planification locale** : `WorkManager 2.10.0` pour les rappels matin/soir.
- **Exports locaux** : génération PDF A4 native via `android.graphics.pdf.PdfDocument` (`PdfReport`) et export/import CSV (`sommelle-agenda.csv`).
- **Audio hors-ligne** : synthèse vocale Android locale (vitesse `0.86x`) combinée à une génération sinusoïdale `AudioTrack` à **432 Hz**.
- **Internationalisation** : 6 langues (FR, EN, DE, ES, PL, UK).
