# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Initial Android app: parallel USB-to-USB file transfer via Storage Access Framework
- Setup screen: add/remove source folders, pick destination, configure overwrite and error strategies, sequential mode toggle
- Transfer screen: per-task progress cards showing speed (MB/s), current filename, and progress percentage
- Detail screen: chronological file event log per transfer task (copied, skipped, in-progress, error)
- Foreground service for background-safe transfers with persistent notification
- DataStore persistence for folder URIs and transfer settings across app restarts
- CI: reusable `_build-sign` workflow; signed APK pre-release published to GitHub on every push to `main`
- Release workflow: manual version bump, draft GitHub release with signed APK
