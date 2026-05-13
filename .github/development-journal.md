# Development Journal

## Software Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Storage | Storage Access Framework (SAF) — `DocumentFile` + `ACTION_OPEN_DOCUMENT_TREE` |
| Background | Foreground Service + Kotlin Coroutines (`Dispatchers.IO + SupervisorJob`) |
| Persistence | DataStore Preferences |
| Min SDK | 33 (Android 13) |
| Build | Gradle + version catalog (`gradle/libs.versions.toml`) |

## Key Decisions

### SAF over direct file I/O
Android 13 gives no path-based access to `/mnt/media_rw` without root. SAF tree URIs with `takePersistableUriPermission` are the only reliable cross-device approach that survives reboots without special system permissions.

### "Ask" overwrite strategy not implemented
The README listed "Ask" as an option, but the user decided conflict resolution must be set before transfer starts to allow unattended operation. Strategies are: Skip, Overwrite Smaller, Overwrite.

### Foreground Service for transfers
Long-running IO on `Dispatchers.IO` inside a `CoroutineScope` tied to a foreground service. This survives the app being backgrounded or the screen turning off. The service self-stops when all tasks complete.

### Progress bar resume invariant
Skipped files immediately contribute their byte count to `bytesCopied`. This ensures the progress bar never goes backwards when resuming a partial transfer — it jumps forward past already-handled files.

### Speed calculation
Speed is sampled every 500 ms to avoid flooding the StateFlow with updates on fast storage. Between samples, `speedBytesPerSec = 0` is emitted (UI shows last known speed).

### Reusable CI workflow
`_build-sign.yml` is a `workflow_call` reusable workflow consumed by both `build.yml` and `release.yml`. This eliminates duplicated checkout + java + gradle + signing steps. Signing secrets are inherited automatically via `secrets: inherit`.

## Core Features

- Parallel transfers (one coroutine per source device) or sequential mode
- Per-task progress cards: speed, filename, progress bar
- Detail screen: append-only chronological file event log
- Signed APK pre-release published to GitHub on every push to `main` (tagged `dev`)
- Manual release workflow with auto-incrementing patch version
