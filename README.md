# Chaster Android

Native Android client built with Kotlin, Jetpack Compose and Material 3.

## Included
- Locks screen with status and countdown computed from the server end date when available.
- Lock history screen requesting event history for loaded locks.
- Profile screen.
- Settings to save/clear a personal developer token.
- AndroidX Security Crypto encrypted token storage.
- Network calls on the IO dispatcher, bearer authentication, HTTPS-only requests and loading/error feedback.

## Run
1. Open in Android Studio with JDK 17.
2. Sync Gradle and install Android SDK platform 35 if prompted.
3. Run the app on a device/emulator.
4. Open Settings, paste your Chaster developer token and tap Save token & connect.

## API verification
Chaster's Public API is in beta. The OpenAPI schema at https://api.chaster.app/api is authoritative. This first pass uses `GET /locks`, `GET /auth/profile`, and `POST /locks/{lockId}/history` (with an empty JSON request body), with common JSON response-envelope parsing. Verify those exact paths and fields against the current schema; update `ChasterApi.kt` if they differ. Profile may require the `profile` scope; locks/history require `locks`.

## Security
- The token is not embedded in source or APK.
- The token is encrypted locally with AndroidX Security Crypto.
- Do not share an APK containing your personal token.
- This is a personal-token prototype, not a multi-user OAuth client.
- API error messages do not include response bodies.

## References
- https://docs.chaster.app/api/public-api/endpoints/
- https://docs.chaster.app/api/public-api/developer-token/
- https://github.com/jonny550993/emlalock-android
