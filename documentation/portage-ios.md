# Portage iOS de Sommelle — Plan et avancement

## État initial posé

- Coque XcodeGen dans `iosApp/` (`project.yml`, `SommelleApp.swift`, `Info.plist`, `PrivacyInfo.xcprivacy`).
- Module Kotlin Multiplatform `:shared` (`shared/build.gradle.kts`) avec cibles `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `iosX64` et Compose Multiplatform `1.7.3`.
- Implémentation iOS de `SommellePlatform` (`AVSpeechSynthesizer` pour la synthèse vocale des exercices de nuit, `UNUserNotificationCenter` pour les rappels quotidiens) et point d'entrée `MainViewController`.

## Étapes suivantes de migration

1. **Socle métier (`core:model`, `core:protocol`, `core:presentation`)** :
   - Remplacer `java.time.{LocalDate, LocalTime, LocalDateTime, Duration}` par `kotlinx.datetime` et `kotlin.time.Duration`.
   - Passer ces 3 modules de `kotlin("jvm")` vers `kotlin-multiplatform` (`jvm` + cibles `ios*`), en gardant la compatibilité Android.
2. **Stockage local (`core:data`)** :
   - Monter Room `2.6.1` vers Room KMP (`2.7+`) avec `androidx.sqlite:sqlite-bundled`, puis déplacer les entités/DAO/base dans `commonMain`.
3. **Export PDF (`feature:export`)** :
   - Extraire une interface `SleepReportPdfExporter` dans `commonMain` et implémenter le rendu iOS via `UIGraphicsPDFRenderer`.
4. **Écrans Compose (`feature:*` -> `:shared`)** :
   - Déplacer progressivement les écrans sans dépendance `android.*` vers `:shared/commonMain` et les relier à `MainViewController`.
5. **CI macOS** :
   - Ajouter un job macOS (XcodeGen + `xcodebuild`) dans `.github/workflows/ci.yml` pour vérifier la compilation iOS à chaque commit.
