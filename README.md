# BTUCM Auth PoC

Experimental single-APK Android proof of concept testing whether Mozilla **GeckoView** + a bundled **WebExtension** can complete Google OAuth authentication against **BTU Classroom** (`https://classroom.btu.edu.ge/`) and hand off the resulting `argus_session` cookie for independent native **OkHttp** verification.

## Architecture

- **Compose UI (`MainActivity`, `AuthDiagnosticsScreen`, `GeckoBrowserModal`)**
  - Displays live diagnostic telemetry, control buttons (**Start Login**, **Verify Session**, **Reset Session**, and **Logout from BTU** with confirmation), and a timestamped diagnostic event log.
  - Opens the embedded **GeckoView** session in a full-screen modal during login and keeps it open for inspection if Google rejects the browser (`disallowed_useragent`) or verification fails.
- **GeckoView & Native Messaging (`GeckoAuthManager`)**
  - Initializes a singleton `GeckoRuntime` with JavaScript enabled, installs the bundled WebExtension via `WebExtensionController.ensureBuiltIn`, and attaches `WebExtension.MessageDelegate` before navigating to `https://classroom.btu.edu.ge/ge/login/index`.
  - Tracks BTU login loading, Google OAuth navigation, `disallowed_useragent` rejection, `/ge/login/oauth/` callback, and `/ge/student/` portal reachability without altering OAuth parameters or spoofing user agents.
- **Bundled WebExtension (`app/src/main/assets/btucm_ext/`)**
  - Scoped strictly to `https://classroom.btu.edu.ge/*` with `cookies`, `webNavigation`, `nativeMessaging`, and `geckoViewAddons`.
  - Never inspects Google domains or Google cookies. Observes `argus_session` cookie creation and relays `SESSION_DETECTED` / `SESSION_READY` privately over native messaging.
- **In-Memory Repository & OkHttp Verifier (`BtuSessionRepository`, `BtuSessionVerifier`)**
  - Keeps `argus_session` strictly in memory (never persisted, backed up, or logged).
  - Performs an independent `GET https://classroom.btu.edu.ge/ge/student/me/courses` with `Cookie: argus_session=<SESSION>`, TLS verification enabled, redirects disabled, and no browser impersonation headers.
  - Verifies `AUTHENTICATED` strictly on HTTP 200 containing the Georgian course-table heading (`კურსის დასახელება`) and course-detail links. Releases the `GeckoSession` upon verified success.

## Build & Run

1. Open the project in Android Studio (or build from CLI with Gradle).
2. Ensure internet access is enabled so Gradle can resolve `org.mozilla.geckoview:geckoview` from `https://maven.mozilla.org/maven2/`.
3. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```

## Tests

Run the local JVM unit and Robolectric tests (including `MockWebServer` verification for `AUTHENTICATED`, `UNAUTHENTICATED`, `CLOUDFLARE_BLOCKED`, `NETWORK_ERROR`, and `UNEXPECTED_RESPONSE` outcomes):

```bash
gradle :app:testDebugUnitTest
```

## Security Notes

- Session values, OAuth authorization codes, Google credentials, student PII, and raw HTML bodies are never logged or shown in the UI.
- Android Auto Backup and cloud extraction are disabled (`android:allowBackup="false"` and exclusion rules in `backup_rules.xml` / `data_extraction_rules.xml`).
- No User-Agent spoofing, TLS bypass, or OAuth request tampering is performed.
