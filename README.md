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
The current public OpenAPI schema at https://api.chaster.app/api lists `GET /locks`, `GET /auth/profile`, `POST /locks/{lockId}/history`, `POST /locks/{lockId}/update-time`, `POST /locks/{lockId}/max-limit-date`, and `POST /locks/{lockId}/min-limit-date`. This verifies the route/method names, but does not by itself verify every request/response field used by this client. The adapter deliberately reports a clear schema-mismatch error when expected list/history arrays are absent. Recheck request DTOs and response schemas against the OpenAPI spec before relying on lock-changing actions.

The Public API is in beta. Use a developer token with the `profile` scope for profile access and `locks` scope for lock/history operations.

## Security
- The token is not embedded in source or APK.
- The token is encrypted locally with AndroidX Security Crypto.
- Do not share an APK containing your personal token.
- This is a personal-token prototype, not a multi-user OAuth client.
- API error messages do not include response bodies.

## References
- https://api.chaster.app/api
- https://docs.chaster.app/api/public-api/endpoints/
- https://docs.chaster.app/api/public-api/developer-token/
- https://github.com/jonny550993/emlalock-android
