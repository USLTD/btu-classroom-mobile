# BTUCM (BTU Classroom Mobile — 0.1 Alpha)

Unofficial, offline-first native Android client for Business and Technology University's **BTU Classroom** portal (`https://classroom.btu.edu.ge/`), built with **Kotlin**, **Jetpack Compose (Material 3)**, **Mozilla GeckoView**, **OkHttp**, **Jsoup**, **Room**, and **DataStore**.

## Architecture (`com.example`)

- **Authentication & Security (`core/auth`, `core/security`, `assets/btucm_ext/`)**
  - **On-Demand GeckoView (`GeckoAuthManager`, `GeckoExtensionMessageHandler`)**: Opens a full-screen GeckoView session only during interactive sign-in, captures `argus_session` from the hardened bundled WebExtension (`cookies`, `webNavigation`, `nativeMessaging`, `geckoViewAddons` scoped strictly to `https://classroom.btu.edu.ge/*`), independently verifies authentication via `BtuHttpClient`, and releases the browser session immediately.
  - **Session Persistence (`SessionManager`, `AndroidKeystoreSessionCipher`)**: Supports **Session-only (in-memory)** and **Remember Login (Android Keystore AES-256-GCM encrypted in DataStore)** modes. Automatically clears invalidated credentials and distinguishes **Sign Out** (calling `/ge/login/logout` + wiping local cache/browser state) from **Clear Local Data** (local wipe without remote logout).
  - **External Browser Contingency**: Surfaces an external-browser fallback if Google OAuth ever rejects the embedded browser (`disallowed_useragent`).
- **Networking & HTML Contracts (`core/network`, `core/parsing`)**
  - **Centralized `BtuHttpClient`**: Single OkHttp client enforcing approved `classroom.btu.edu.ge` HTTPS origins, disabled redirects, bounded body reads, timeouts, and redacted diagnostics.
  - **`SelectedCoursesParser` & `CourseDetailParser`**: Semantic Jsoup parsers extracting course codes, titles, credits, nullable scores (`null` for ungraded courses, never `0`), semester groupings, grade components, and safe BTU resource links, paired with `PageCompatibilityRegistry`.
- **Offline-First Storage (`core/database`, `core/preferences`)**
  - **Room Database (`BtuDatabase`, `CourseRepository`)**: Caches `StudentEntity`, `SemesterEntity`, `CourseEntity`, `EnrollmentEntity`, `GradeEntity`, and `SyncMetadataEntity` transactionally with reactive `Flow` streams and explicit freshness banners.
  - **Localization (`LocaleHelper`)**: Complete English (`values/strings.xml`) and Georgian (`values-ka/strings.xml`) resources with instant in-app switching.
- **UI & Navigation (`app`, `design`, `feature`)**
  - Adaptive Material 3 shell (`NavigationBar` on phones, `NavigationRail` on larger screens) with four destinations: **Home**, **Courses** (+ **Course Details**), **Schedule** (Phase 3 placeholder), and **More** (+ **Settings** and `BuildConfig.DEBUG`-gated **Developer Diagnostics**).

## Build & Run

1. Open the project in Android Studio or build via CLI with Gradle.
2. Ensure internet access is enabled to resolve dependencies including `org.mozilla.geckoview:geckoview` from `https://maven.mozilla.org/maven2/`.
3. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```

## Tests

Run all local JVM unit and Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

## Security & Privacy Notes

- No OAuth authorization codes, Google cookies, release User-Agent spoofing, or third-party analytics exist in production code.
- Session values and student data are never written to Logcat or diagnostics, and Android cloud backups/device transfers are disabled (`android:allowBackup="false"`).
- `BTUCM` is unofficial and unaffiliated with Business and Technology University (BTU).
