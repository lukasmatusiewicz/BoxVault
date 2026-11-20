# BoxVault Living Codebase Map

> **Last Verified:** 2026-11-20
> **Branch / HEAD Context:** `docs/codebase-map` (branched from `main` at `d314735`)
> **Scope Note:** Generated, cache, and IDE directories (`.git/`, `.gradle/`, `.idea/`, `build/`, `app/build/`) as well as secret/keystore/credential files (`.env*`, `*.jks`, `*.keystore`, credential JSONs) are intentionally omitted. Every path below was verified against disk.

---

## 1. Annotated Project File Tree

```text
BoxVault/
├── .github/workflows/
│   └── android-ci.yml                  # CI: assembleDebug, unit tests, Kover coverage & zero-network manifest audit
├── docs/
│   ├── CODEBASE_MAP.md                 # This living codebase map
│   ├── assets/
│   │   └── banner_hero.png             # README hero banner image
│   └── screenshots/                    # App showcase screenshots (01_dashboard..04_pdf_generator.png)
├── gradle/
│   ├── libs.versions.toml              # Centralized Gradle version catalog (AGP 8.7.3, Kotlin 2.0.21, Compose, Room, ML Kit, ZXing, Kover)
│   └── wrapper/                        # Gradle wrapper jar & properties
├── app/
│   ├── build.gradle.kts                # App module config (minSdk 26, targetSdk 35, Compose, KSP, Room, Kover >=80% rule)
│   ├── proguard-rules.pro              # R8/ProGuard keep rules for Room, bundled ML Kit Barhopper, and ZXing
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # Hardened offline manifest (CAMERA only; INTERNET & ACCESS_NETWORK_STATE removed via tools:node="remove")
│       │   ├── java/com/boxvault/app/
│       │   │   ├── BoxVaultApplication.kt          # Application singleton wiring lazy prefs, sensory, Room DB, storage, repo, backup
│       │   │   ├── MainActivity.kt                 # Edge-to-edge ComponentActivity hosting BoxVaultTheme & BoxVaultNavHost
│       │   │   ├── data/
│       │   │   │   ├── backup/
│       │   │   │   │   └── BackupManager.kt        # SAF .zip export + atomic staged restore (cacheDir/restore_staging, Zip-Slip & SHA-256)
│       │   │   │   ├── local/
│       │   │   │   │   ├── BoxVaultDatabase.kt     # Room SQLite DB (v2, MIGRATION_1_2)
│       │   │   │   ├── dao/                    # Room DAOs: RoomDao.kt, BoxDao.kt, ItemDao.kt, PhotoDao.kt
│       │   │   │   ├── entity/                 # Room entities: RoomEntity.kt, BoxEntity.kt, ItemEntity.kt, PhotoEntity.kt
│       │   │   │   │   └── model/
│       │   │   │   │       └── RelationalModels.kt # Query POJOs (RoomWithBoxes, BoxWithItemsAndPhotos, GlobalSearchResult, etc.)
│       │   │   │   ├── preferences/
│       │   │   │   │   └── UserPreferencesManager.kt # SharedPreferences + StateFlows (ThemeMode, sound, haptics, onboarding)
│       │   │   │   ├── repository/
│       │   │   │   │   └── BoxVaultRepository.kt   # Single source of truth for Room DAOs, transactions & photo cleanup
│       │   │   │   └── storage/
│       │   │   │       └── PhotoStorageManager.kt  # Sandboxed filesDir/photos/ storage with canonical path-traversal checks
│       │   │   ├── ui/
│       │   │   │   ├── components/             # Shared Compose UI: BoxVaultHeader, LocalPhotoThumbnail, OnboardingDialog, PhotoSlotCard, PhotoSourceSelectionDialog, PrivacyPolicyDialog, ThemeSelectionDialog
│       │   │   │   ├── navigation/
│       │   │   │   │   ├── BoxVaultDestinations.kt # Route constants & URI-encoded route builders
│       │   │   │   │   └── BoxVaultNavHost.kt      # NavHost with slide/fade transitions, ViewModel factories & global dialogs
│       │   │   │   ├── screens/
│       │   │   │   │   ├── backup/                 # BackupRestoreScreen.kt, BackupRestoreViewModel.kt
│       │   │   │   │   ├── boxdetail/              # BoxDetailScreen.kt, BoxDetailViewModel.kt
│       │   │   │   ├── boxedit/                # BoxEditScreen.kt, BoxEditViewModel.kt
│       │   │   │   │   ├── bulkentry/              # BulkEntryScreen.kt, BulkEntryViewModel.kt
│       │   │   │   │   ├── dashboard/              # DashboardScreen.kt, DashboardViewModel.kt
│       │   │   │   │   ├── pdfgenerator/           # PdfGeneratorScreen.kt, PdfGeneratorViewModel.kt
│       │   │   │   │   └── scanner/                # QrScannerScreen.kt, QrScannerViewModel.kt
│       │   │   │   └── theme/                  # Color.kt, Theme.kt, Type.kt (Material 3 Light/Dark/System)
│       │   │   └── util/
│       │   │       ├── image/
│       │   │       │   └── PhotoCompressor.kt      # EXIF rotation normalization + max 1600px 82% WebP compression
│       │   │       ├── pdf/
│       │   │       │   ├── BoxVaultPrintDocumentAdapter.kt # Native Android PrintManager adapter for QR label sheets
│       │   │       │   └── QrSheetPdfGenerator.kt  # Native PdfDocument generator (A4/Letter, 2x4 / 3x7 / 4x10 grids)
│       │   │       ├── qr/
│       │   │       │   └── QrCodeGenerator.kt      # ZXing Core QR bitmap generator (`boxvault://box/<uuid>`, quiet zone=2)
│       │   │       └── sensory/
│       │   │           └── SensoryFeedbackManager.kt # AudioManager/ToneGenerator clicks & chimes + Compose haptics
│       │   └── res/
│       │       ├── drawable/                   # banner_header_wide.png, banner_hero.png, ic_boxvault_logo.png, ic_launcher.png
│       │       ├── values/                     # Default English strings.xml & themes.xml
│       │       ├── values-{de,es,fr,it,pl,uk}/ # Localized strings.xml for 6 additional languages (7 total)
│       │       └── xml/
│       │           └── data_extraction_rules.xml # Excludes all domains from cloud-backup and device-transfer
│       └── test/java/com/boxvault/app/         # 15 JVM/Robolectric/Room unit test suites (no androidTest/ directory)
│           ├── data/backup/BackupManagerTest.kt
│           ├── data/local/dao/{BoxVaultDaoTest.kt,RoomDaoTest.kt}
│           ├── data/preferences/UserPreferencesManagerTest.kt
│           ├── data/repository/BoxVaultRepositoryTest.kt
│           ├── data/storage/PhotoStorageManagerTest.kt
│           ├── ui/navigation/BoxVaultDestinationsTest.kt
│           ├── ui/screens/boxdetail/BoxEditAndDetailViewModelTest.kt
│           ├── ui/screens/bulkentry/BulkEntryViewModelTest.kt
│           ├── ui/screens/dashboard/DashboardViewModelTest.kt
│           └── util/{image/PhotoCompressorTest.kt,localization/LocalizationCompletenessTest.kt,pdf/QrSheetPdfGeneratorTest.kt,qr/QrCodeGeneratorTest.kt,sensory/SensoryFeedbackManagerTest.kt}
├── build.gradle.kts                            # Root Gradle build script
├── settings.gradle.kts                         # Project settings & repository config
├── gradle.properties                           # Gradle JVM & AndroidX flags
├── gradlew / gradlew.bat                       # Gradle wrapper scripts
├── README.md                                   # Architecture, security controls, and build/test documentation
└── LICENSE                                     # Project license
```

---

## 2. Task-to-File Routing Table ("Where to Look First")

| Task Area | Minimal Files to Inspect First |
| :--- | :--- |
| **UI screens, navigation & Compose components** | `app/src/main/java/com/boxvault/app/ui/navigation/BoxVaultDestinations.kt`, `BoxVaultNavHost.kt`, `ui/components/BoxVaultHeader.kt`, and the target screen under `ui/screens/<feature>/*Screen.kt` |
| **ViewModels & UI state management** | `ui/screens/<feature>/*ViewModel.kt` (`DashboardViewModel.kt`, `BoxDetailViewModel.kt`, `BoxEditViewModel.kt`, `BulkEntryViewModel.kt`, `QrScannerViewModel.kt`, `PdfGeneratorViewModel.kt`, `BackupRestoreViewModel.kt`) |
| **Room database, entities, DAOs, migrations & repository** | `data/local/BoxVaultDatabase.kt`, `data/local/entity/{RoomEntity,BoxEntity,ItemEntity,PhotoEntity}.kt`, `data/local/dao/{RoomDao,BoxDao,ItemDao,PhotoDao}.kt`, `data/local/model/RelationalModels.kt`, `data/repository/BoxVaultRepository.kt` |
| **CameraX / ML Kit QR scanning & ZXing QR generation** | `ui/screens/scanner/QrScannerScreen.kt`, `QrScannerViewModel.kt`, `util/qr/QrCodeGenerator.kt`, `data/local/entity/BoxEntity.kt` (`QR_URI_PREFIX` / parsing) |
| **PDF sticker sheet generation & WebP photo compression/storage** | `util/pdf/QrSheetPdfGenerator.kt`, `util/pdf/BoxVaultPrintDocumentAdapter.kt`, `util/image/PhotoCompressor.kt`, `data/storage/PhotoStorageManager.kt`, `ui/components/{PhotoSlotCard,PhotoSourceSelectionDialog,LocalPhotoThumbnail}.kt` |
| **SAF `.zip` backup/restore & preferences/sensory feedback** | `data/backup/BackupManager.kt`, `ui/screens/backup/BackupRestore{Screen,ViewModel}.kt`, `data/preferences/UserPreferencesManager.kt`, `util/sensory/SensoryFeedbackManager.kt` |
| **Localization, themes & privacy policy dialog** | `app/src/main/res/values/strings.xml`, `values-{de,es,fr,it,pl,uk}/strings.xml`, `ui/theme/{Color,Theme,Type}.kt`, `ui/components/{ThemeSelectionDialog,PrivacyPolicyDialog,OnboardingDialog}.kt` |
| **Build config, ProGuard/R8, AndroidManifest & CI/CD** | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `.github/workflows/android-ci.yml` |
| **Unit tests & Kover coverage config** | `app/build.gradle.kts` (`kover` block & `testOptions`), `app/src/test/java/com/boxvault/app/**`, `util/localization/LocalizationCompletenessTest.kt` |

---

## 3. Entry Points & Architecture Wiring

- **App Startup:** `BoxVaultApplication` (`app/src/main/java/com/boxvault/app/BoxVaultApplication.kt`) lazily initializes `UserPreferencesManager`, `SensoryFeedbackManager`, `BoxVaultDatabase` (Room v2), `PhotoStorageManager`, `BoxVaultRepository`, and `BackupManager`.
- **Activity & Theme:** `MainActivity` (`MainActivity.kt`) calls `enableEdgeToEdge()`, observes `userPreferencesManager.themeMode` via `collectAsStateWithLifecycle()`, wraps content in `BoxVaultTheme`, and mounts `BoxVaultNavHost`.
- **Navigation Graph:** `BoxVaultNavHost` (`ui/navigation/BoxVaultNavHost.kt`) renders the persistent `BoxVaultHeader` (with tap-to-dashboard, theme dialog, and privacy policy dialog) above a `NavHost` starting at `BoxVaultDestinations.DASHBOARD_ROUTE`, connecting `dashboard`, `box_edit`, `box_detail/{boxId}`, `bulk_entry/{boxId}`, `qr_scanner`, `pdf_generator`, and `backup_restore` with `StateFlow`-backed ViewModels instantiated via nested `Factory` classes.
- **Data Flow:** `Compose Screen` → `ViewModel` (`StateFlow`) → `BoxVaultRepository` → `Room DAOs` (`BoxVaultDatabase`) / `PhotoStorageManager` (`PhotoCompressor` → `filesDir/photos/`) / `BackupManager` (`cacheDir/restore_staging/`).
- **CI & Test Roots:** `.github/workflows/android-ci.yml` runs `./gradlew assembleDebug testDebugUnitTest koverXmlReportDebug koverHtmlReportDebug koverLogDebug`, audits the merged debug `AndroidManifest.xml` for zero network permissions, and publishes JUnit + Kover summaries. All tests live under `app/src/test/java/com/boxvault/app/` (JVM + Robolectric + Room in-memory).

---

## 4. Restricted, Isolated & Sensitive Zones

| Zone / File | Why Extra Care & Strict Verification Are Required |
| :--- | :--- |
| `app/src/main/AndroidManifest.xml` & `res/xml/data_extraction_rules.xml` | Enforces 100% offline guarantee via `tools:node="remove"` on `INTERNET`, `ACCESS_NETWORK_STATE`, and ML Kit `datatransport` services, plus `allowBackup="false"`. Any change must pass the CI merged-manifest audit. |
| `data/local/BoxVaultDatabase.kt` & `entity/*.kt` | Room schema v2 (`boxvault_local.db`) uses `MIGRATION_1_2` and `ForeignKey.SET_NULL` on `boxes.roomId`. Schema edits require explicit version bump and migration verification. |
| `data/backup/BackupManager.kt` | Handles untrusted SAF `.zip` streams: enforces Zip-Slip canonical path checks, archive entry/byte limits (`MAX_ENTRIES`, `MAX_TOTAL_ARCHIVE_BYTES`), `MessageDigest("SHA-256")` verification in `cacheDir/restore_staging`, and guaranteed `finally` cleanup. |
| `data/storage/PhotoStorageManager.kt` | `resolvePhotoFile()` enforces canonical path containment inside `filesDir/photos/` (rejecting `..`, leading slashes, and escapes) while preserving `.webp` / `.jpg` / `.jpeg` backward compatibility. |
| `app/proguard-rules.pro` | Protects Room entities/DAOs/POJOs, bundled ML Kit Barhopper JNI classes, and ZXing Core from R8 stripping in release builds (`isMinifyEnabled = true`). |
| `gradle/libs.versions.toml` & `app/build.gradle.kts` | Controls dependency versions, KSP/Room codegen, and Kover `minBound(80)` verification gate; adding libraries must not re-introduce unstripped network/telemetry components. |
| `.github/workflows/android-ci.yml` | Defines the mandatory CI build, test, Kover, and zero-network manifest security gate. |
| **Secret / Keystore Patterns (Never Commit / Never Read)** | `.env*`, `*.jks`, `*.keystore`, and credential JSONs must never be read, copied, or committed to the repository. |
