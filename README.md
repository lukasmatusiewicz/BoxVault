# 📦 BoxVault — 100% Offline, High-Security QR Storage & Inventory Catalog for Android

[![Android CI & Security Audit](https://github.com/lukasmatusiewicz/BoxVault/actions/workflows/android-ci.yml/badge.svg)](https://github.com/lukasmatusiewicz/BoxVault/actions/workflows/android-ci.yml)
[![Code Coverage (Kover)](https://img.shields.io/badge/coverage-80.26%25%20lines%20%7C%2081.4%25%20instr-brightgreen?logo=kotlin)](app/build/reports/kover/htmlDebug/index.html)
[![100% Offline Verified](https://img.shields.io/badge/network%20permissions-0%20(100%25%20offline)-blue?logo=android)](app/src/main/AndroidManifest.xml)
[![Room SQLite (Schema v2)](https://img.shields.io/badge/Room%20SQLite-Schema%20v2-003B57?logo=sqlite&logoColor=white)](app/src/main/java/com/boxvault/app/data/local)
[![WebP Storage](https://img.shields.io/badge/Image%20Format-WebP%20(1600px%20%7C%2082%25)-009688?logo=google&logoColor=white)](app/src/main/java/com/boxvault/app/util/image)
[![Material 3](https://img.shields.io/badge/Material%203-Light%20%7C%20Dark%20%7C%20System-4285F4?logo=jetpackcompose&logoColor=white)](app/src/main/java/com/boxvault/app/ui)
[![MinSDK 26+](https://img.shields.io/badge/MinSDK-26%2B%20(Android%208.0%2B)-3DDC84?logo=android&logoColor=white)](app/build.gradle.kts)
[![Cost: $0 Local-First](https://img.shields.io/badge/cloud%20cost-%240%20forever-amber)](#security--privacy-architecture)

**BoxVault** is a privacy-first, 100% local Android application engineered for cataloging physical storage boxes, garage totes, basement shelves, and workshop bins using immutable QR codes. It operates with **zero network permissions**, **zero cloud accounts**, and **zero recurring costs**—all data, photos, QR generation, barcode scanning, printable PDF rendering, and cryptographic `.zip` backups execute strictly on-device.

---

## ✨ Core Features & Highlights

### 1. Room & Location Hierarchy (Schema v2)
- **Organize by Space**: Group boxes into custom rooms (e.g. *Basement Shelf 2*, *Garage Loft*, *Attic Rafters*, *Office Closet*) with dedicated color tags and icons.
- **Automatic Flat-List Fallback**: If 0 rooms are defined, BoxVault displays a streamlined flat list of boxes with zero friction.
- **Cascade Safety (`ForeignKey.SET_NULL`)**: Deleting a room safely sets child box room assignments to null rather than deleting boxes or their contents.
- **Collapsible Animated Sections**: Expand and collapse individual rooms with smooth `expandVertically` and `shrinkVertically` transitions.

### 2. High-Efficiency WebP Image Storage (`PhotoCompressor`)
- **Lossy WebP Compression**: Uses Android native `Bitmap.CompressFormat.WEBP_LOSSY` at 82% quality with 1600px longest-edge downsampling, slashing storage footprint by ~60% compared to legacy JPEG while maintaining crisp clarity.
- **Automatic EXIF Normalization**: Automatically respects camera orientation tags so vertical and horizontal shots never render sideways.
- **Backward-Compatible Archive Decoding**: Safely extracts and displays older JPEG backups alongside new WebP photo archives without data loss.

### 3. Unified Photo Slots UX (`PhotoSlotCard` & `PhotoSourceSelectionDialog`)
- **Dedicated Photo Placeholders**: High-visibility photo slots for both Boxes and Individual Items.
- **Camera vs. Gallery Source Picker**: Unified dialog with runtime camera permission enforcement and direct photo deletion capability.
- **Multi-Photo Carousels**: Support for multiple photos per item with local thumbnail caching.

### 4. Explicit Theme Mode (Light / Dark / System Default)
- **Local Preference Toggle**: Choose between **High-Contrast Light**, **Deep Dark (dimly lit basements & garages)**, or **System Default**.
- **Instant Live Updates**: StateFlow-backed local persistence via `UserPreferencesManager` dynamically updates UI theme colors in real time.

### 5. Tactile & Audio Sensory Feedback (`SensoryFeedbackManager`)
- **Tactile Haptics**: Subtle physical vibration cues on QR scan detection, fast bulk entry item additions, quantity steppers, and deletions.
- **Audio Sound Effects**: Gentle native audio click feedback (`AudioManager.FX_KEY_CLICK`) and scan success chime, with an independent user toggle to mute sensory effects.

### 6. Compose Animations & Motion Transitions
- **Fluid Navigation Motion**: Smooth `fadeIn + slideInHorizontally` / `fadeOut + slideOutHorizontally` transitions on all screen changes in `BoxVaultNavHost`.
- **Feed Item Insertions**: Animated row transitions (`Modifier.animateItem()`) in the Fast Bulk Entry feed.
- **Collapsible Room Sections**: Smooth expanding/collapsing on Dashboard room cards.

### 7. 100% Offline Privacy Policy
- **Built-in Transparency Modal**: In-app Privacy Policy dialog detailing the 100% on-device SQLite database, strict absence of `android.permission.INTERNET`, zero cloud backups, zero telemetry/analytics, and private local SAF archive export.

### 8. Immutable `boxvault://box/<uuid>` QR Codes (Rename & Move Without Re-Printing)
- Every box is identified by an immutable UUID v4 encoded as `boxvault://box/<uuid>` alongside a human-friendly monospace short code (`BOX-001`, `BOX-002`, …).
- **Decoupled Metadata**: Because the QR code encodes only the immutable UUID, you can rename a box, move it between rooms, or edit its contents at any time **without ever re-printing its physical QR sticker**.

### 9. Printable A4 / US Letter PDF QR Sticker Grids (`QrSheetPdfGenerator`)
- Native `android.graphics.pdf.PdfDocument` engine with **zero third-party PDF bloat**.
- **Razor-Sharp Nearest-Neighbor QR Rendering**: Module edges are drawn with `isFilterBitmap = false`, `isAntiAlias = false`, and an explicit white quiet zone (`EncodeHintType.MARGIN = 2`) for laser and inkjet printers.
- **Configurable Grid Presets**:
  - `2 × 4` (**8 labels/page**) — Large heavy-duty storage totes & moving cartons
  - `3 × 7` (**21 labels/page**) — Medium archive & shelf boxes
  - `4 × 10` (**40 labels/page**) — Small hardware drawers & parts organizers
- **Two Label Generation Modes**:
  - **Existing Boxes Mode**: Print labels for boxes already in your Room database.
  - **Pre-Allocated Blank Stickers Mode**: Print a sheet of unassigned `boxvault://box/<new-uuid>` stickers with write-in lines in advance. Stick one onto a new box in the basement, scan it, and BoxVault automatically launches **Assign Pre-Printed QR Label** bound to that exact UUID.

### 10. 100% Offline CameraX + Bundled ML Kit QR Scanner (`QrScannerScreen`)
- Live CameraX `PreviewView` + `ImageAnalysis` pipeline powered by the **bundled** ML Kit Barcode Scanning model (`com.google.mlkit:barcode-scanning:17.3.0`) statically linked into the APK.
- Includes hardware flashlight toggle and an instant **Manual Short-Code / QR URI Fallback** (`BOX-001` or `1`) if camera permission is denied.

### 11. Fast Bulk Item Entry UX (`BulkEntryScreen`)
- Purpose-built for standing in front of an open storage box and cataloging 30+ items in under a minute.
- **Persistent Keyboard Focus**: Pressing `ImeAction.Next` / `Enter` or tapping **Add & Keep Typing** inserts the item, increments the session counter, resets quantity to `1`, and keeps the soft keyboard open with focus locked on the Item Name field.

### 12. Portable SAF `.ZIP` Backups & Atomic Staged Restore (`BackupManager`)
- **Portable SAF `.ZIP` Export**: Exports `backup_manifest.json` (schema version, timestamps, rooms, boxes, items, and photo metadata with per-file **SHA-256 hex digests**) alongside `photos/<filename>` via Android's Storage Access Framework (`CreateDocument("application/zip")`).
- **Atomic Staged Restore (`cacheDir/restore_staging`)**:
  1. Extracts incoming `.zip` archives into an isolated staging directory (`cacheDir/restore_staging`)—**never mutating live Room tables or `filesDir/photos` while reading the stream**.
  2. Enforces strict **Zip-Slip path traversal defense** (`SecurityException` on any `..`, absolute path, or canonical path escape).
  3. Recomputes **SHA-256 checksums** on every staged photo file and verifies 100% match against `backup_manifest.json`.
  4. Atomically swaps the verified data into Room and `PhotoStorageManager`, and unconditionally wipes `cacheDir/restore_staging` in a `finally` block.

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    subgraph UI["Jetpack Compose Material 3 UI (Light / Dark / System Themes)"]
        Header["BoxVaultHeader (Persistent Vector Logo & Tap-to-Dashboard)"]
        Dash["DashboardScreen (Grouped Rooms & Collapsible Sections)"]
        Detail["BoxDetailScreen (Photo Slots, Steppers & Inline QR)"]
        Edit["BoxEditScreen (New / Edit / Room Selection)"]
        Bulk["BulkEntryScreen (Persistent Keyboard UX & AnimateItem)"]
        Scan["QrScannerScreen (CameraX + Bundled ML Kit + Audio/Haptics)"]
        PdfUI["PdfGeneratorScreen (A4 / Letter Presets)"]
        BackupUI["BackupRestoreScreen (SAF .ZIP Export/Import)"]
        Privacy["PrivacyPolicyDialog (100% Offline Declaration)"]
        ThemeDlg["ThemeSelectionDialog (Theme & Sensory Toggles)"]
    end

    subgraph VM["Reactive StateFlow ViewModels"]
        DashVM["DashboardViewModel"]
        DetailVM["BoxDetailViewModel"]
        EditVM["BoxEditViewModel"]
        BulkVM["BulkEntryViewModel"]
        ScanVM["QrScannerViewModel"]
        PdfVM["PdfGeneratorViewModel"]
        BackupVM["BackupRestoreViewModel"]
    end

    subgraph SensoryTheme["App-Private Preferences & Sensory Engine"]
        UserPrefs["UserPreferencesManager (SharedPreferences\nThemeMode, Sound & Haptic Toggles)"]
        SensoryMgr["SensoryFeedbackManager (Audio Click, Scan Chime & Tactile Haptics)"]
    end

    subgraph Core["Offline Domain, Imaging, QR, PDF & Cryptographic Backup Engines"]
        Repo["BoxVaultRepository"]
        QrGen["QrCodeGenerator (ZXing Core)"]
        PdfGen["QrSheetPdfGenerator (Native PdfDocument)"]
        PhotoComp["PhotoCompressor (Exif + 1600px WebP Lossy)"]
        BackupMgr["BackupManager (SHA-256 + Zip-Slip Guard)"]
    end

    subgraph Storage["100% Local App-Private Sandboxed Storage"]
        RoomDB[("Room SQLite Database (Schema v2)\nrooms | boxes | items | photos")]
        PhotoStore[("PhotoStorageManager\nfilesDir/photos/*.webp")]
        Staging[("Isolated Restore Staging\ncacheDir/restore_staging/")]
    end

    Dash --> DashVM --> Repo
    Detail --> DetailVM --> Repo
    Edit --> EditVM --> Repo
    Bulk --> BulkVM --> Repo
    Scan --> ScanVM --> Repo
    PdfUI --> PdfVM --> Repo
    PdfVM --> PdfGen --> QrGen
    Detail --> QrGen
    Edit --> QrGen
    BackupUI --> BackupVM --> BackupMgr
    
    Dash -.-> ThemeDlg -.-> UserPrefs
    Dash -.-> Privacy
    Dash -.-> SensoryMgr
    Bulk -.-> SensoryMgr
    Detail -.-> SensoryMgr
    Scan -.-> SensoryMgr

    Repo --> RoomDB
    Repo --> PhotoStore --> PhotoComp
    BackupMgr --> Staging
    BackupMgr --> Repo
    BackupMgr --> PhotoStore
```

---

## 🔒 Security & Privacy Architecture

| Security Control | Implementation in BoxVault | Verification |
| :--- | :--- | :--- |
| **Zero Network Capability** | `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` and `ACCESS_NETWORK_STATE` stripped from the merged manifest. | Enforced automatically on every PR/push in [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml). |
| **Zero Cloud Auto-Backup** | `android:allowBackup="false"`, `android:fullBackupContent="false"`, and `data_extraction_rules.xml` block silent cloud/D2D extraction. | Inspected in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml). |
| **Sandboxed Photo Storage** | Photos are stored strictly in `context.filesDir/photos/`. `PhotoStorageManager.resolvePhotoFile()` enforces canonical path checks. | Unit-tested in [`PhotoStorageManagerTest.kt`](app/src/test/java/com/boxvault/app/data/storage/PhotoStorageManagerTest.kt). |
| **Zip-Slip Traversal Defense** | `BackupManager.resolveSafeStagingFile()` rejects `..`, absolute paths, drive prefixes, and canonical escapes outside `cacheDir/restore_staging` with `SecurityException`. | Unit-tested in [`BackupManagerTest.kt`](app/src/test/java/com/boxvault/app/data/backup/BackupManagerTest.kt). |
| **Atomic Staged SHA-256 Restore** | Every extracted photo is hashed with `MessageDigest("SHA-256")` and verified against `backup_manifest.json` in `restore_staging` before any live DB row or photo is touched. | Unit-tested in [`BackupManagerTest.kt`](app/src/test/java/com/boxvault/app/data/backup/BackupManagerTest.kt). |

---

## 🧪 Shift-Left Unit Test Suite & Code Coverage (Kotlinx Kover)

BoxVault uses a shift-left JVM + Robolectric + Room In-Memory test suite (`51` unit tests across `13` test classes) measured by **Kotlinx Kover**:

| Metric | Percentage | Status |
| :--- | :---: | :---: |
| **Line Coverage** | **80.26%** | Met Kover Minimum (80%) |
| **Instruction Coverage** | **81.4%** | Passing |
| **Unit Test Pass Rate** | **100.0%** (`51 / 51`) | Passing |

### Key Test Suites
- [`RoomDaoTest.kt`](app/src/test/java/com/boxvault/app/data/local/dao/RoomDaoTest.kt) — Room entity relational queries, foreign key cascade safety, and box grouping.
- [`BoxVaultDaoTest.kt`](app/src/test/java/com/boxvault/app/data/local/dao/BoxVaultDaoTest.kt) — Room relational queries, cascading deletes, and indexed SQL search across boxes and items.
- [`PhotoStorageManagerTest.kt`](app/src/test/java/com/boxvault/app/data/storage/PhotoStorageManagerTest.kt) — App-private WebP photo read/write, orphan cleanup, and `SecurityException` path traversal rejection.
- [`BoxVaultRepositoryTest.kt`](app/src/test/java/com/boxvault/app/data/repository/BoxVaultRepositoryTest.kt) — Sequential short-code generation, immutable QR payload resolution after box rename/move, and cascading file cleanup.
- [`DashboardViewModelTest.kt`](app/src/test/java/com/boxvault/app/ui/screens/dashboard/DashboardViewModelTest.kt), [`BoxEditAndDetailViewModelTest.kt`](app/src/test/java/com/boxvault/app/ui/screens/boxdetail/BoxEditAndDetailViewModelTest.kt), [`BulkEntryViewModelTest.kt`](app/src/test/java/com/boxvault/app/ui/screens/bulkentry/BulkEntryViewModelTest.kt) — Reactive UI state flows, duplicate short-code guards, and fast bulk item entry session counters.
- [`QrCodeGeneratorTest.kt`](app/src/test/java/com/boxvault/app/util/qr/QrCodeGeneratorTest.kt) — ZXing QR bitmap generation, quiet-zone pixel verification, `QRCodeReader` round-trip decoding, and `QrScannerViewModel` existing vs. pre-printed blank label routing.
- [`QrSheetPdfGeneratorTest.kt`](app/src/test/java/com/boxvault/app/util/pdf/QrSheetPdfGeneratorTest.kt) — Multi-page grid math (`2x4`, `3x7`, `4x10`), pre-allocated blank label generation, and valid `%PDF-` stream generation.
- [`PhotoCompressorTest.kt`](app/src/test/java/com/boxvault/app/util/image/PhotoCompressorTest.kt) — WebP format validation, landscape/portrait `1600px` downsampling, aspect ratio preservation, and EXIF rotation normalization.
- [`BackupManagerTest.kt`](app/src/test/java/com/boxvault/app/data/backup/BackupManagerTest.kt) — Full export → wipe → atomic staged restore round-trip, Zip-Slip `../../evil.sh` rejection preserving live data, and SHA-256 tamper rejection preserving live data.
- [`UserPreferencesManagerTest.kt`](app/src/test/java/com/boxvault/app/data/preferences/UserPreferencesManagerTest.kt) — Local theme preference persistence (`LIGHT`, `DARK`, `SYSTEM`) and sensory feedback mute toggles.
- [`SensoryFeedbackManagerTest.kt`](app/src/test/java/com/boxvault/app/util/sensory/SensoryFeedbackManagerTest.kt) — Tactile haptic feedback triggers and audio click/chime effects honoring user mute preferences.
- [`BoxVaultDestinationsTest.kt`](app/src/test/java/com/boxvault/app/ui/navigation/BoxVaultDestinationsTest.kt) — Deep-link URI encoding and query string construction for navigation routes.

---

## 📂 Project Structure

```text
BoxVault/
├── .github/workflows/
│   └── android-ci.yml                  # GitHub Actions CI: Build, Unit Tests, Kover Coverage & Zero-Network Audit
├── app/
│   ├── build.gradle.kts                # Android App, Room KSP, Compose, CameraX, ML Kit, ZXing, Robolectric & Kover
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     # Hardened offline manifest (INTERNET & ACCESS_NETWORK_STATE removed)
│       │   └── java/com/boxvault/app/
│       │       ├── BoxVaultApplication.kt
│       │       ├── MainActivity.kt
│       │       ├── data/
│       │       │   ├── backup/         # BackupManager (SAF .ZIP export + atomic staged SHA-256 & Zip-Slip restore)
│       │       │   ├── local/          # Room Database (Schema v2), RoomEntity, BoxEntity, ItemEntity, PhotoEntity, DAOs
│       │       │   ├── preferences/    # UserPreferencesManager (ThemeMode, Sound & Haptics SharedPreferences)
│       │       │   ├── repository/     # BoxVaultRepository
│       │       │   └── storage/        # PhotoStorageManager (canonical sandboxed file storage)
│       │       ├── ui/
│       │       │   ├── components/     # BoxVaultHeader, PhotoSlotCard, ThemeSelectionDialog, PrivacyPolicyDialog
│       │       │   ├── navigation/     # BoxVaultDestinations & BoxVaultNavHost (with slide/fade animations)
│       │       │   ├── screens/        # dashboard, boxedit, boxdetail, bulkentry, scanner, pdfgenerator, backup
│       │       │   └── theme/          # High-contrast Material 3 theme (Light, Dark, and System palettes)
│       │       └── util/
│       │           ├── image/          # PhotoCompressor (EXIF normalization + max 1600px WebP downsampling)
│       │           ├── pdf/            # QrSheetPdfGenerator (Native PdfDocument A4/Letter crisp QR grids)
│       │           ├── qr/             # QrCodeGenerator (ZXing high-contrast QR bitmap generator)
│       │           └── sensory/        # SensoryFeedbackManager (tactile haptics & audio click/chime effects)
│       └── test/java/com/boxvault/app/ # 13 JUnit + Robolectric shift-left unit test suites (51 tests)
└── gradle/libs.versions.toml           # Centralized Gradle version catalog
```

---

## 🚀 Build, Test & Coverage Commands

### Prerequisites
- **JDK 21** (e.g., Eclipse Temurin 21)
- **Android SDK Platform 35**

### Run Full Build, Unit Tests & Kover Coverage Report
```bash
# Linux / macOS
./gradlew assembleDebug testDebugUnitTest koverXmlReportDebug koverHtmlReportDebug koverLogDebug

# Windows PowerShell
.\gradlew.bat assembleDebug testDebugUnitTest koverXmlReportDebug koverHtmlReportDebug koverLogDebug
```

### Coverage & Test Report Output Paths
- **Kover HTML Report**: `app/build/reports/kover/htmlDebug/index.html`
- **Kover XML Report**: `app/build/reports/kover/reportDebug.xml`
- **JUnit HTML Report**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
