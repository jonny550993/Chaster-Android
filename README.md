# Chaster Android

Native Android client starter for Chaster, built with Kotlin and Jetpack Compose.

Initial implementation is being added in follow-up commits. See the project structure and security notes in this README.

## Goals
- Locks and live countdown timers
- Lock history and profile
- Settings and API token management
- Chaster Public API integration

## Security
Never commit a developer token. Before real-world use, store credentials using Android Keystore-backed encryption. A personal developer token is appropriate for a local prototype only; do not ship it in an APK.

## API
- https://docs.chaster.app/api/public-api/endpoints/
- https://docs.chaster.app/api/public-api/developer-token/
- https://api.chaster.app/api
