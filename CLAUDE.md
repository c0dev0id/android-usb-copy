# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android app that copies files in parallel from multiple USB SD card readers (source devices) to one large destination drive — all attached via a USB hub. Each source device is identified by its UUID, and files are copied into `<destination>/<UUID>/` subdirectories.

Key behaviours:
- Transfers run in parallel by default (one coroutine/thread per source device); optional sequential mode.
- Overwrite strategy: Skip | Overwrite | Overwrite Smaller | Ask.
- Error strategy: Skip and continue | Stop transfer.
- Progress is tracked against total bytes on the source, so resuming a partial transfer (Skip/Overwrite Smaller) jumps the bar forward past already-handled files.
- Each transfer task card shows speed (MB/s), current filename, and a progress bar.
- Tapping a task card opens a detail view: chronological file list coloured green (done/skipped), orange (in progress), red (error).

## Build & CI

**Do not build locally** — Gradle/AGP cannot be accessed due to firewall restrictions. All builds run in CI via GitHub Actions.

| Workflow | Trigger | What it does |
|---|---|---|
| `build.yml` | push to `main`, or PR labelled `run-build` | lint → assembleDebug → zipalign + apksigner (release APK as artifact) |
| `release.yml` | manual `workflow_dispatch` | assembleRelease (signed), tags the repo, creates a draft GitHub release |

Signing uses four repository secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. The release APK is named `keytester-<version>.apk`.

Version auto-increments the patch segment of the latest `v*` tag if no version is supplied to the release workflow.

## Architecture Notes

- Target platform: Android, external storage under `/mnt/media_rw/<UUID>/`.
- Parallelism is the default; the sequential toggle is a user preference, not an architectural mode switch.
- Progress accounting must include skipped/already-copied bytes so the bar is monotonically non-decreasing on resume.
- The detail view is a live log — append-only, not a summary.
