# BTUCM Auth PoC

Experimental single-APK Android proof of concept testing whether Mozilla **GeckoView** + a bundled **WebExtension** can complete Google OAuth authentication against **BTU Classroom** (`https://classroom.btu.edu.ge/`) and hand off the resulting `argus_session` cookie for independent native **OkHttp** verification.

## Architecture

- **Compose UI (`MainActivity`, `AuthDiagnosticsScreen`, `GeckoBrowserModal`)**
  - Displays live diagnostic telemetry, control buttons (**Start Login**, **Verify Session**, **Reset Session**, **Check Expiry**, and **Logout from BTU** with confirmation), a **WebExtension User-Agent Switcher**, a **Cookie Inspector & Management** panel, a session-expiration re-authentication dialog, and a timestamped diagnostic event log.
  - Opens the embedded **GeckoView** session in a full-screen modal during login and keeps it open for inspection if Google rejects the browser (`disallowed_useragent`) or verification fails.
- **GeckoView & Bidirectional Native Messaging (`GeckoAuthManager`, `GeckoExtensionMessageHandler`)**
  - Initializes a singleton `GeckoRuntime` with JavaScript enabled, installs the bundled WebExtension via `WebExtensionController.ensureBuiltIn`, and attaches `GeckoExtensionMessageHandler` (`WebExtension.MessageDelegate` + `WebExtension.PortDelegate`) before navigating to `https://classroom.btu.edu.ge/ge/login/index`.
  - Tracks BTU login loading, Google OAuth navigation, `disallowed_useragent` rejection, `/ge/login/oauth/` callback, OAuth token/callback capture, and `/ge/student/` portal reachability.
  - Releases the `GeckoSession` once native OkHttp verification succeeds.
- **Bundled WebExtension (`app/src/main/assets/btucm_ext/`)**
  - Observes `argus_session` cookie creation, updates, and expiration (`cookies.onChanged` + periodic `expirationDate` checks) strictly for `classroom.btu.edu.ge` (Google cookies are never read).
  - Supports dynamic `User-Agent` header switching (`DEFAULT`, `FIREFOX_MOBILE`, `CHROME_MOBILE`, `FIREFOX_DESKTOP`, `CUSTOM`) via `webRequest.onBeforeSendHeaders` controlled by native Kotlin messages to test Google OAuth `disallowed_useragent` behavior.
  - Provides domain cookie listing, explicit domain cookie deletion, and redacted diagnostic logging of all observed `classroom.btu.edu.ge` cookies.
- **In-Memory Repository & OkHttp Verifier (`BtuSessionRepository`, `BtuSessionVerifier`)**
  - Keeps `argus_session`, last known valid session details, and OAuth token/code payloads strictly in memory (never persisted, backed up, or logged).
  - Attempts automatic session verification/refresh using the last known valid session details if `argus_session` expires, and prompts the user to re-authenticate when refresh fails.
  - Performs an independent `GET https://classroom.btu.edu.ge/ge/student/me/courses` with `Cookie: argus_session=<SESSION>`, TLS verification enabled, redirects disabled, and no browser impersonation headers, verifying `AUTHENTICATED` strictly on HTTP 200 containing `"კურსის დასახელება"` and course-detail links.

## Build & Run

1. Open the project in Android Studio (or build from CLI with Gradle).
2. Ensure internet access is enabled so Gradle can resolve `org.mozilla.geckoview:geckoview` from `https://maven.mozilla.org/maven2/`.
3. Build the debug APK:
   ```bash
   gradle :app:assembleDebug
   ```

## Tests

Run the local JVM unit and Robolectric tests:

```bash
gradle :app:testDebugUnitTest
```

## GitHub Actions APK Build

The `Build Android APK` workflow builds and uploads a debug APK signed with the
standard Android debug keystore when changes are pushed to `main` or `master`, or
when manually dispatched. No repository signing secrets are required.

## Security Notes

- Session values, OAuth authorization codes, Google credentials, student PII, and raw HTML bodies are never logged or shown in the UI.
- Android Auto Backup and cloud extraction are disabled (`android:allowBackup="false"` and exclusion rules in `backup_rules.xml` / `data_extraction_rules.xml`).
- No TLS bypass or OAuth parameter tampering is performed; `User-Agent` switching is off (`DEFAULT`) unless explicitly selected for experimental testing.
