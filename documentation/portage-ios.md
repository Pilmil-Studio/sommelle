# Portage iOS de Sommelle — Plan et avancement

## État initial posé

- Coque XcodeGen dans `iosApp/` (`project.yml`, `SommelleApp.swift`, `Info.plist`, `PrivacyInfo.xcprivacy`).
- Module Kotlin Multiplatform `:shared` (`shared/build.gradle.kts`) avec cibles `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `iosX64` et Compose Multiplatform `1.7.3`.
- Implémentation iOS de `SommellePlatform` (`AVSpeechSynthesizer` pour la synthèse vocale des exercices de nuit, `UNUserNotificationCenter` pour les rappels quotidiens) et point d'entrée `MainViewController`.
- Socle métier migré en Kotlin Multiplatform (`jvm` + `iosArm64`, `iosSimulatorArm64`, `iosX64`) sur `kotlinx-datetime` et `kotlin.time.Duration` : `core:model`, `core:protocol` et `core:presentation` (`commonMain` / `commonTest`, couche de compatibilité `DateCompat` et `PlatformLocale` `expect`/`actual`).
- Stockage local `core:data` migré en Kotlin Multiplatform (`androidTarget` + `ios*`) avec Room KMP `2.7.2`, `androidx.sqlite:sqlite-bundled` `2.5.1` (`BundledSQLiteDriver` sur iOS, `SQLiteConnection` pour `MIGRATION_1_2`) et `kotlinx.datetime.Clock` dans `SommelleRepository`.

## Étapes suivantes de migration

1. **Socle métier (`core:model`, `core:protocol`, `core:presentation`)** *(fait)* :
   - `java.time` remplacé par `kotlinx.datetime` et `kotlin.time.Duration` ; modules passés en `kotlin-multiplatform` (`jvm` + `ios*`) avec appelants Android mis à jour.
2. **Stockage local (`core:data`)** *(fait)* :
   - Room monté vers `2.7.2` (Kotlin `2.1.10`, KSP `2.1.10-1.0.31`) avec `androidx.sqlite:sqlite-bundled:2.5.1` ; entités, DAO, base et `SommelleRepository` déplacés dans `commonMain`, constructeurs plateforme dans `androidMain` / `iosMain`.
3. **Export PDF (`feature:export`)** :
   - Extraire une interface `SleepReportPdfExporter` dans `commonMain` et implémenter le rendu iOS via `UIGraphicsPDFRenderer`.
4. **Écrans Compose (`feature:*` -> `:shared`)** :
   - Déplacer progressivement les écrans sans dépendance `android.*` vers `:shared/commonMain` et les relier à `MainViewController`.
5. **CI macOS** :
   - Ajouter un job macOS (XcodeGen + `xcodebuild`) dans `.github/workflows/ci.yml` pour vérifier la compilation iOS à chaque commit.
