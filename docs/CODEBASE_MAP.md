# BoxVault Living Codebase Map

## 1. Header & Scope Note

- **Last Verified:** `2026-11-20`
- **Git Context:** Branch `docs/codebase-map` (branched from `main` at `d314735`)
- **Scope & Exclusions:** This map covers tracked source, test, resource, documentation, Gradle, and CI files only. Generated, cache, and IDE directories (`.git/`, `.gradle/`, `.idea/`, `build/`, `app/build/`) as well as secret/keystore patterns (`.env*`, `*.jks`, `*.keystore`, credential JSONs) are intentionally omitted.
- **Test Roots Note:** Shift-left unit tests live under `app/src/test/` (15 test files). `app/src/androidTest/` is not present in this repository.

---

## 2. Annotated Project File Tree

```text
BoxVault/
├── .github/workflows/
│   └── android-ci.yml                          # GitHub Actions CI: debug build, unit tests, Kover coverage & zero-network manifest audit
├── app/
│   ├── build.gradle.kts                        # Android app config (SDK 26–35, Compose, Room KSP, CameraX, ML Kit, ZXing, Kover >= 80%)
│   ├── proguard-rules.pro                      # R8/ProGuard keep rules for Room entities/DAOs, ML Kit Barhopper JNI & ZXing
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml             # Offline-hardened manifest (INTERNET & ACCESS_NETWORK_STATE + datatransport removed)
│       │   ├── java/com/boxvault/app/
│       │   │   ├── BoxVaultApplication.kt      # Application singleton holding lazy prefs, sensory, Room DB, photo, repo & backup managers
│       │   │   ├── MainActivity.kt             # Single-activity Compose host collecting ThemeMode and launching BoxVaultNavHost
│       │   │   ├── data/                       # Local persistence, repositories, preferences, storage & backup engines
│       │   │   │   ├── backup/
│       │   │   │   │   └── BackupManager.kt    # SAF .zip export & atomic staged restore (Zip-Slip guard + SHA-256 verification)
│       │   │   │   ├── local/
│       │   │   │   │   ├── BoxVaultDatabase.kt # Room SQLite database (v2) with MIGRATION_1_2 (rooms table & boxes.roomId)
│       │   │   │   │   ├── dao/                # Room DAOs for rooms, boxes, items & photos
│       │   │   │   │   │   ├── BoxDao.kt       # Box queries, summaries, search & relational fetches
│       │   │   │   │   │   ├── ItemDao.kt      # Item CRUD, quantity updates & box item queries
│       │   │   │   │   │   ├── PhotoDao.kt     # Photo metadata CRUD for boxes and items
│       │   │   │   │   │   └── RoomDao.kt      # Room CRUD & room-grouped box summaries
│       │   │   │   │   ├── entity/             # Room @Entity table definitions
│       │   │   │   │   │   ├── BoxEntity.kt    # Storage box entity (UUID, shortCode, roomId FK SET_NULL, QR URI helper)
│       │   │   │   │   │   ├── ItemEntity.kt   # Cataloged item entity linked to BoxEntity
│       │   │   │   │   │   ├── PhotoEntity.kt  # Local photo reference entity for boxes/items
│       │   │   │   │   │   └── RoomEntity.kt   # Storage room/location entity (name, colorHex, iconName)
│       │   │   │   │   └── model/
│       │   │   │   │       └── RelationalModels.kt # Room POJOs (BoxSummary, BoxWithItemsAndPhotos, RoomWithBoxes, GlobalSearchResult)
│       │   │   │   ├── preferences/
│       │   │   │   │   └── UserPreferencesManager.kt # SharedPreferences-backed ThemeMode, sound, haptics & onboarding StateFlows
│       │   │   │   ├── repository/
│       │   │   │   │   └── BoxVaultRepository.kt # Single data-access facade coordinating DAOs, transactions & photo cleanup
│       │   │   │   └── storage/
│       │   │   │       └── PhotoStorageManager.kt # App-private filesDir/photos/ store with canonical path traversal checks
│       │   │   ├── ui/                         # Jetpack Compose Material 3 UI, navigation, ViewModels & theme
│       │   │   │   ├── components/             # Reusable Compose UI dialogs, header & photo widgets
│       │   │   │   │   ├── BoxVaultHeader.kt   # Persistent top branding header with dashboard navigation & action triggers
│       │   │   │   │   ├── LocalPhotoThumbnail.kt # Async/local thumbnail renderer for app-private photo files
│       │   │   │   │   ├── OnboardingDialog.kt # First-run onboarding modal
│       │   │   │   │   ├── PhotoSlotCard.kt    # Box/item photo slot card with add/delete actions
│       │   │   │   │   ├── PhotoSourceSelectionDialog.kt # Camera vs. Gallery picker dialog with permission handling
│       │   │   │   │   ├── PrivacyPolicyDialog.kt # 100% offline privacy & data-handling disclosure modal
│       │   │   │   │   └── ThemeSelectionDialog.kt # ThemeMode (System/Light/Dark) and sensory feedback toggles
│       │   │   │   ├── navigation/
│       │   │   │   │   ├── BoxVaultDestinations.kt # Route constants and URI-encoded route builder helpers
│       │   │   │   │   └── BoxVaultNavHost.kt  # NavHost wiring screens, ViewModels, dialogs & slide/fade transitions
│       │   │   │   ├── screens/                # Feature screens paired with StateFlow ViewModels
│       │   │   │   │   ├── backup/             # BackupRestoreScreen.kt, BackupRestoreViewModel.kt
│       │   │   │   │   ├── boxdetail/          # BoxDetailScreen.kt, BoxDetailViewModel.kt
│       │   │   │   │   ├── boxedit/            # BoxEditScreen.kt, BoxEditViewModel.kt
│       │   │   │   │   ├── bulkentry/          # BulkEntryScreen.kt, BulkEntryViewModel.kt
│       │   │   │   │   ├── dashboard/          # DashboardScreen.kt, DashboardViewModel.kt
│       │   │   │   │   ├── pdfgenerator/       # PdfGeneratorScreen.kt, PdfGeneratorViewModel.kt
│       │   │   │   │   └── scanner/            # QrScannerScreen.kt, QrScannerViewModel.kt
│       │   │   │   └── theme/                  # Material 3 Light/Dark palettes & typography (Color.kt, Theme.kt, Type.kt)
│       │   │   └── util/                       # Offline imaging, PDF, QR & sensory utilities
│       │   │       ├── image/
│       │       │       └── PhotoCompressor.kt  # EXIF-normalized, max-1600px 82% WebP lossy image compressor
│       │   │       ├── pdf/
│       │   │       │   ├── BoxVaultPrintDocumentAdapter.kt # Android PrintManager adapter for direct QR sheet printing
│       │   │       │   └── QrSheetPdfGenerator.kt # Native PdfDocument A4/Letter grid generator (2x4, 3x7, 4x10)
│       │   │       ├── qr/
│       │   │       │   └── QrCodeGenerator.kt  # ZXing high-contrast QR bitmap generator & boxvault://box/<uuid> parser
│       │   │       └── sensory/
│       │   │           └── SensoryFeedbackManager.kt # AudioManager clicks/chimes & tactile haptics honoring user prefs
│       │   └── res/
│       │       ├── drawable/                   # App icons and branding banners (ic_launcher.png, ic_boxvault_logo.png, banner_*.png)
│       │       ├── values/                     # Default English strings.xml & themes.xml
│       │       ├── values-{de,es,fr,it,pl,uk}/ # Localized strings.xml for 6 additional languages (7 total)
│       │       └── xml/
│       │           └── data_extraction_rules.xml # Blocks cloud backup and device-to-device data extraction
│       └── test/java/com/boxvault/app/         # 15 JUnit + Robolectric + Room in-memory unit test suites
│           ├── data/backup/BackupManagerTest.kt
│           ├── data/local/dao/BoxVaultDaoTest.kt
│           ├── data/local/dao/RoomDaoTest.kt
│           ├── data/preferences/UserPreferencesManagerTest.kt
│           ├── data/repository/BoxVaultRepositoryTest.kt
│           ├── data/storage/PhotoStorageManagerTest.kt
│           ├── ui/navigation/BoxVaultDestinationsTest.kt
│           ├── ui/screens/boxdetail/BoxEditAndDetailViewModelTest.kt
│           ├── ui/screens/bulkentry/BulkEntryViewModelTest.kt
│           ├── ui/screens/dashboard/DashboardViewModelTest.kt
│           ├── util/image/PhotoCompressorTest.kt
│           ├── util/localization/LocalizationCompletenessTest.kt
│           ├── util/pdf/QrSheetPdfGeneratorTest.kt
│           ├── util/qr/QrCodeGeneratorTest.kt
│           └── util/sensory/SensoryFeedbackManagerTest.kt
├── docs/
│   ├── CODEBASE_MAP.md                         # This living codebase orientation map
│   ├── assets/banner_hero.png                  # README hero banner image
│   └── screenshots/                            # App showcase screenshots (01_dashboard..04_pdf_generator.png)
├── gradle/
│   ├── libs.versions.toml                      # Centralized Gradle version catalog (AGP, Kotlin, Compose, Room, CameraX, ML Kit, Kover)
│   └── wrapper/                                # Gradle wrapper jar & properties
├── build.gradle.kts                            # Root Gradle build configuration
├── gradle.properties                           # Gradle JVM/AndroidX build properties
├── settings.gradle.kts                         # Gradle module inclusion (:app)
├── gradlew / gradlew.bat                       # Gradle wrapper scripts
├── README.md                                   # Project overview, architecture diagram, features & build commands
└── LICENSE                                     # Repository license
```

---

## 3. Task-to-File Routing Table ("Where to Look First")

| Task Domain | Primary Files to Inspect First |
| :--- | :--- |
| **UI Screens, Navigation & Compose Components** | `app/src/main/java/com/boxvault/app/ui/navigation/BoxVaultNavHost.kt`, `BoxVaultDestinations.kt`, `app/src/main/java/com/boxvault/app/ui/components/*`, `app/src/main/java/com/boxvault/app/ui/screens/*/*Screen.kt` |
| **ViewModels & UI State Management** | `app/src/main/java/com/boxvault/app/ui/screens/dashboard/DashboardViewModel.kt`, `boxdetail/BoxDetailViewModel.kt`, `boxedit/BoxEditViewModel.kt`, `bulkentry/BulkEntryViewModel.kt`, `scanner/QrScannerViewModel.kt`, `pdfgenerator/PdfGeneratorViewModel.kt`, `backup/BackupRestoreViewModel.kt` |
| **Room Database, Entities, DAOs, Migrations & Repository** | `app/src/main/java/com/boxvault/app/data/local/BoxVaultDatabase.kt`, `entity/*.kt`, `dao/*.kt`, `model/RelationalModels.kt`, `app/src/main/java/com/boxvault/app/data/repository/BoxVaultRepository.kt` |
| **CameraX / ML Kit QR Scanning & ZXing QR Generation** | `app/src/main/java/com/boxvault/app/ui/screens/scanner/QrScannerScreen.kt`, `QrScannerViewModel.kt`, `app/src/main/java/com/boxvault/app/util/qr/QrCodeGenerator.kt` |
| **PDF Sticker Sheets & WebP Photo Compression/Storage** | `app/src/main/java/com/boxvault/app/util/pdf/QrSheetPdfGenerator.kt`, `BoxVaultPrintDocumentAdapter.kt`, `app/src/main/java/com/boxvault/app/util/image/PhotoCompressor.kt`, `app/src/main/java/com/boxvault/app/data/storage/PhotoStorageManager.kt` |
| **SAF `.zip` Backup/Restore & Preferences/Sensory** | `app/src/main/java/com/boxvault/app/data/backup/BackupManager.kt`, `app/src/main/java/com/boxvault/app/data/preferences/UserPreferencesManager.kt`, `app/src/main/java/com/boxvault/app/util/sensory/SensoryFeedbackManager.kt` |
| **Localization, Themes & Privacy Policy Dialog** | `app/src/main/res/values/strings.xml`, `app/src/main/res/values-{de,es,fr,it,pl,uk}/strings.xml`, `app/src/main/java/com/boxvault/app/ui/theme/{Color,Theme,Type}.kt`, `app/src/main/java/com/boxvault/app/ui/components/PrivacyPolicyDialog.kt` |
| **Build Config, ProGuard/R8, Manifest & CI/CD** | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `.github/workflows/android-ci.yml` |
| **Unit Tests & Kover Coverage Config** | `app/build.gradle.kts` (`kover {}` block), `app/src/test/java/com/boxvault/app/**/*Test.kt`, `app/src/test/java/com/boxvault/app/util/localization/LocalizationCompletenessTest.kt` |

---

## 4. Entry Points & Architecture Wiring

- **App Startup & DI Root:** `BoxVaultApplication` (`app/src/main/java/com/boxvault/app/BoxVaultApplication.kt`) lazily instantiates the singletons: `UserPreferencesManager`, `SensoryFeedbackManager`, `BoxVaultDatabase`, `PhotoStorageManager`, `BoxVaultRepository`, and `BackupManager`.
- **Activity Host:** `MainActivity` (`app/src/main/java/com/boxvault/app/MainActivity.kt`) enables edge-to-edge rendering, observes `UserPreferencesManager.themeMode`, applies `BoxVaultTheme`, and mounts `BoxVaultNavHost`.
- **Navigation Graph:** `BoxVaultNavHost` (`app/src/main/java/com/boxvault/app/ui/navigation/BoxVaultNavHost.kt`) renders the persistent `BoxVaultHeader` (plus `ThemeSelectionDialog` and `PrivacyPolicyDialog` on the dashboard) above a `NavHost` starting at `BoxVaultDestinations.DASHBOARD_ROUTE` with routes for `box_edit`, `box_detail/{boxId}`, `bulk_entry/{boxId}`, `qr_scanner`, `pdf_generator`, and `backup_restore`.
- **Data Flow:** `Screen (Compose)` -> `ViewModel (StateFlow)` -> `BoxVaultRepository` -> `Room DAOs (BoxDao, ItemDao, PhotoDao, RoomDao)` + `PhotoStorageManager` (`filesDir/photos/`) and `BackupManager` (SAF `.zip` + `cacheDir/restore_staging/`).
- **CI & Test Roots:** `.github/workflows/android-ci.yml` runs `./gradlew assembleDebug testDebugUnitTest koverXmlReportDebug koverHtmlReportDebug koverLogDebug --stacktrace` on `ubuntu-latest` (JDK 21), enforces zero network permissions on the merged manifest, and reports Kover coverage (80% minimum bound in `app/build.gradle.kts`).

---

## 5. Restricted, Isolated & Sensitive Zones

- **`app/src/main/AndroidManifest.xml` & `app/src/main/res/xml/data_extraction_rules.xml`:** Enforces the 100% offline security boundary via `tools:node="remove"` on `INTERNET`, `ACCESS_NETWORK_STATE`, and ML Kit `datatransport` services, plus `android:allowBackup="false"` and `fullBackupContent="false"`. Any dependency or manifest change must preserve zero network permissions in the merged manifest.
- **`app/src/main/java/com/boxvault/app/data/local/BoxVaultDatabase.kt` & `entity/*.kt`:** Database is at `version = 2` (`exportSchema = false`) with `MIGRATION_1_2` and `ForeignKey.SET_NULL` on `BoxEntity.roomId`. Schema changes require incrementing `version` and adding an explicit `Migration` to avoid destructive data loss.
- **`app/src/main/java/com/boxvault/app/data/backup/BackupManager.kt`:** Handles untrusted `.zip` imports via isolated `cacheDir/restore_staging/`, Zip-Slip path traversal rejection (`SecurityException`), and per-photo `SHA-256` verification before swapping into Room/storage. Never bypass staging or checksum validation.
- **`app/src/main/java/com/boxvault/app/data/storage/PhotoStorageManager.kt`:** Restricts all photo I/O to `context.filesDir/photos/` and enforces canonical path containment in `resolvePhotoFile()`.
- **`app/proguard-rules.pro`:** Keeps Room entities/DAOs/models, ML Kit Barhopper JNI classes, and ZXing Core for minified release builds (`isMinifyEnabled = true`).
- **`gradle/libs.versions.toml` & `.github/workflows/android-ci.yml`:** Central dependency/plugin versions and CI security gates (including the merged-manifest offline check).
- **Secrets & Keystores (Never Commit / Never Read):** Keep `.env*`, `*.jks`, `*.keystore`, and credential JSON files untracked and excluded from inspection or commits.
